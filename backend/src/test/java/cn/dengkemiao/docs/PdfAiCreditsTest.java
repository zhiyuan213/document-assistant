package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"PDF_AI_DAILY_FREE_CREDITS=0","spring.datasource.url=jdbc:h2:mem:ai-credits;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=", "PDF_BILLING_ENABLED=true", "PDF_PAYMENT_PROVIDER=wechat"})
@AutoConfigureMockMvc
class PdfAiCreditsTest {
    @Autowired PdfAiCredits credits;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PdfAiController ai;
    @MockitoBean WechatPayService pay;
    @MockitoBean DeepSeekService model;

    private String owner(boolean vip, long topup) {
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO pdf_visitor(id,token_hash) VALUES (?,?)",id,UUID.randomUUID().toString());
        // DATETIME stores whole seconds; do not let fixture rounding move the period start into the future.
        if(vip)db.update("INSERT INTO pdf_membership(visitor_id,expires_at) VALUES (?,?)",id,Timestamp.valueOf(PdfAiCredits.now().plusMonths(1).withNano(0)));
        credits.snapshot(id);
        db.update("UPDATE pdf_ai_wallet SET topup_balance=? WHERE visitor_id=?",topup,id);
        return id;
    }
    @Test void translationUsesSelectedLanguageAndRejectsRetiredModes() throws Exception {
        String token=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).get("token").asText();
        var file=new org.springframework.mock.web.MockMultipartFile("files","synthetic.pdf","application/pdf",samplePdf());
        when(model.enabled()).thenReturn(true);
        when(model.complete(anyString(),anyString(),anyInt(),anyBoolean())).thenReturn(new DeepSeekService.Result("{\"translations\":[{\"id\":0,\"text\":\"Translated text\"}]}",100,25));
        org.springframework.test.util.ReflectionTestUtils.setField(ai,"testMode",true);
        try {
            var started=mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","translate").param("targetLanguage","en").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn();
            String id=json.readTree(started.getResponse().getContentAsString()).path("translationJobId").asText();assertFalse(id.isBlank());
            com.fasterxml.jackson.databind.JsonNode result=null;
            for(int i=0;i<100;i++){result=json.readTree(mvc.perform(get("/api/pdf/ai/translations/"+id).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());if(!Set.of("queued","processing").contains(result.path("status").asText()))break;Thread.sleep(30);}
            assertEquals("completed",result.path("status").asText(),result.toString());
            assertEquals(0,result.path("usedTokenCredits").asLong());
            verify(model).complete(contains("Translate document text"),contains("Target language: 英语"),anyInt(),eq(true));
            for(String format:List.of("pdf","docx","txt"))mvc.perform(get("/api/pdf/ai/translations/"+id+"/file").param("format",format).header("Authorization","Bearer "+token)).andExpect(status().isOk());
            String stranger=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).path("token").asText();
            mvc.perform(get("/api/pdf/ai/translations/"+id).header("Authorization","Bearer "+stranger)).andExpect(status().isNotFound());
            mvc.perform(multipart("/api/pdf/ai/estimate").file(file).param("mode","translate").param("targetLanguage","invalid").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
            mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","watermark").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
            mvc.perform(post("/api/pdf/ai/export").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(Map.of("content","一、重点内容\n测试报告","format","docx")))).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".docx")));
            verify(model,times(1)).complete(anyString(),anyString(),anyInt(),anyBoolean());
            mvc.perform(post("/api/pdf/ai/export").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        } finally {org.springframework.test.util.ReflectionTestUtils.setField(ai,"testMode",false);}
    }
    @Test void spendsMonthlyBeforeTopupAndReturnsUnusedToOriginalSource() {
        String id=owner(true,1000);
        db.update("UPDATE pdf_ai_wallet SET monthly_used=1999900 WHERE visitor_id=?",id);
        var reservation=credits.reserve(id,300);
        assertEquals(100,reservation.monthly()); assertEquals(200,reservation.topup());
        assertEquals(800,credits.snapshot(id).topup());
        credits.settle(id,reservation,50);
        assertEquals(50,credits.snapshot(id).monthly()); assertEquals(1000,credits.snapshot(id).topup());
    }
    @Test void documentTranslationRetriesReuseTheJobAndDownloadsNeverChargeAgain()throws Exception {
        String token=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).path("token").asText();
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String owner=db.queryForObject("SELECT id FROM pdf_visitor WHERE token_hash=?",String.class,hash);credits.grantTopup(owner,2000000);
        when(model.enabled()).thenReturn(true);when(model.complete(anyString(),anyString(),anyInt(),eq(true))).thenReturn(new DeepSeekService.Result("{\"translations\":[{\"id\":0,\"text\":\"Translated text\"}]}",100,25));
        var file=new org.springframework.mock.web.MockMultipartFile("files","synthetic.pdf","application/pdf",samplePdf());String key=UUID.randomUUID().toString();
        mvc.perform(multipart("/api/pdf/ai/estimate").file(file).param("mode","translate").param("targetLanguage","en").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.documentTranslation").value(true));assertEquals(2000000,credits.snapshot(owner).total());
        String id=json.readTree(mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","translate").param("targetLanguage","en").param("requestId",key).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("translationJobId").asText();
        for(int i=0;i<100;i++){String state=db.queryForObject("SELECT status FROM pdf_translation_job WHERE id=?",String.class,id);if(!Set.of("queued","processing").contains(state))break;Thread.sleep(30);}
        assertEquals("completed",db.queryForObject("SELECT status FROM pdf_translation_job WHERE id=?",String.class,id));assertEquals(1999800,credits.snapshot(owner).total());
        mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","translate").param("targetLanguage","en").param("requestId",key).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.translationJobId").value(id));
        mvc.perform(get("/api/pdf/ai/translations/by-request/"+key).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.translationJobId").value(id));
        for(String format:List.of("pdf","docx","txt"))mvc.perform(get("/api/pdf/ai/translations/"+id+"/file").param("format",format).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        verify(model,times(1)).complete(anyString(),anyString(),anyInt(),anyBoolean());assertEquals(1999800,credits.snapshot(owner).total());
        assertEquals(1,db.queryForObject("SELECT request_count FROM pdf_ai_daily_usage WHERE visitor_id=?",Integer.class,owner));
    }
    @Test void expiredMembershipCanStillSpendTopup() {
        String id=owner(true,1000);
        db.update("UPDATE pdf_membership SET expires_at=? WHERE visitor_id=?",Timestamp.valueOf(PdfAiCredits.now().minusDays(1)),id);
        assertFalse(credits.snapshot(id).vip()); assertEquals(0,credits.snapshot(id).monthly());
        var reservation=credits.reserve(id,700);
        credits.settle(id,reservation,600);
        assertEquals(400,credits.snapshot(id).total());
    }
    @Test void positiveBalanceAllowsLargerTaskAndRefundsUnusedCredits() {
        String id=owner(false,500);
        var reserved=credits.reserve(id,501);
        assertEquals(500,reserved.credits());
        assertEquals(0,credits.snapshot(id).total());
        assertEquals(400,credits.settle(id,reserved,400).credits());
        assertEquals(100,credits.snapshot(id).total());
    }
    @Test void concurrentRequestsCannotSpendTheSameBalance() throws Exception {
        String id=owner(false,1000);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Boolean> task=()->{gate.await();try{credits.reserve(id,700);return true;}catch(ApiError e){return false;}};
            var first=pool.submit(task);var second=pool.submit(task);gate.countDown();
            assertTrue(first.get(10,TimeUnit.SECONDS));assertTrue(second.get(10,TimeUnit.SECONDS));
        }
        assertEquals(0,credits.snapshot(id).total());
    }
    @Test void newCycleResetsMonthlyButRetainsTopupAndDoesNotRestoreExpiredReservation() {
        String id=owner(true,900);
        var reservation=credits.reserve(id,100);
        db.update("UPDATE pdf_ai_wallet SET period_end=? WHERE visitor_id=?",Timestamp.valueOf(PdfAiCredits.now().minusDays(1)),id);
        assertEquals(2000000,credits.snapshot(id).monthly());
        credits.settle(id,reservation,0);
        assertEquals(2000000,credits.snapshot(id).monthly()); assertEquals(900,credits.snapshot(id).topup());
    }
    @Test void renewalDoesNotResetCurrentCycle() {
        String id=owner(true,500);credits.reserve(id,1000);
        var before=credits.snapshot(id);
        db.update("UPDATE pdf_membership SET expires_at=TIMESTAMPADD(MONTH,1,expires_at) WHERE visitor_id=?",id);
        assertEquals(before.monthly(),credits.snapshot(id).monthly());
        assertEquals(before.periodEnd(),credits.snapshot(id).periodEnd());
    }
    @Test void underestimatedUsageIsAccountedForAndBlocksFurtherSpending() {
        String id=owner(false,100);var reservation=credits.reserve(id,100);
        assertEquals(100,credits.settle(id,reservation,120).credits());
        assertEquals(0,credits.snapshot(id).topup());assertEquals(0,credits.snapshot(id).total());
        assertThrows(ApiError.class,()->credits.reserve(id,1));
        credits.grantTopup(id,50);assertEquals(50,credits.snapshot(id).total());
    }
    @Test void sameWechatIdentitySharesWallet() {
        String first=owner(false,700),second=owner(false,0),openid=UUID.randomUUID().toString();
        db.update("UPDATE pdf_visitor SET wechat_openid=?,created_at=? WHERE id=?",openid,Timestamp.valueOf(PdfAiCredits.now().minusDays(2)),first);
        db.update("UPDATE pdf_visitor SET wechat_openid=? WHERE id=?",openid,second);
        assertEquals(first,credits.owner(second));assertEquals(700,credits.snapshot(credits.owner(second)).total());
        String older=owner(false,0);
        db.update("UPDATE pdf_visitor SET wechat_openid=?,created_at=? WHERE id=?",openid,Timestamp.valueOf(PdfAiCredits.now().minusDays(3)),older);
        assertEquals(first,credits.owner(older));
    }
    @Test void topupUserCanEstimateAndRunWithoutMembershipAndOnlyActualUsageIsCharged() throws Exception {
        String token=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).get("token").asText();
        // Obtain the anonymous owner through a job-free status request, then fund its wallet.
        mvc.perform(get("/api/pdf/ai/status").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String id=db.queryForObject("SELECT id FROM pdf_visitor WHERE token_hash=?",String.class,hash);
        db.update("UPDATE pdf_ai_wallet SET topup_balance=20000 WHERE visitor_id=?",id);
        byte[] pdf=samplePdf();
        when(model.enabled()).thenReturn(true);
        when(model.complete(anyString(),anyString(),anyInt(),anyBoolean())).thenReturn(new DeepSeekService.Result("Test summary [A-P1]",100,25));
        var file=new org.springframework.mock.web.MockMultipartFile("files","synthetic.pdf","application/pdf",pdf);
        mvc.perform(multipart("/api/pdf/ai/estimate").file(file).param("mode","summary").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canRun").value(true));
        assertEquals(20000,credits.snapshot(id).topup());
        verify(model,never()).complete(anyString(),anyString(),anyInt(),anyBoolean());
        db.update("INSERT INTO pdf_ai_daily_usage(visitor_id,usage_date,request_count) VALUES (?,?,99)",id,PdfAiCredits.now().toLocalDate());
        mvc.perform(get("/api/pdf/ai/status").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.dailyLimit").value(100)).andExpect(jsonPath("$.dailyRemaining").value(1));
        mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyRemaining").value(0)).andExpect(jsonPath("$.usedTokenCredits").value(200))
                .andExpect(jsonPath("$.inputTokens").value(100)).andExpect(jsonPath("$.outputTokens").value(25))
                .andExpect(jsonPath("$.monthlyChargedCredits").value(0)).andExpect(jsonPath("$.topupChargedCredits").value(200));
        assertEquals(19800,credits.snapshot(id).topup());assertFalse(credits.snapshot(id).vip());
        db.update("UPDATE pdf_ai_daily_usage SET request_count=100 WHERE visitor_id=?",id);
        mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").header("Authorization","Bearer "+token))
                .andExpect(status().isTooManyRequests());
        assertEquals(19800,credits.snapshot(id).topup());
    }
    @Test void oneCreditCanRunWithoutEstimateAndUsageLedgerOnlyRecordsActualDebit() throws Exception {
        String token=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).path("token").asText();
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String id=db.queryForObject("SELECT id FROM pdf_visitor WHERE token_hash=?",String.class,hash);
        credits.grantTopup(id,1);
        when(model.enabled()).thenReturn(true);
        when(model.complete(anyString(),anyString(),anyInt(),anyBoolean())).thenReturn(new DeepSeekService.Result("Summary",100,25));
        var file=new org.springframework.mock.web.MockMultipartFile("files","synthetic.pdf","application/pdf",samplePdf());
        mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usedTokenCredits").value(1))
                .andExpect(jsonPath("$.topupChargedCredits").value(1)).andExpect(jsonPath("$.actualTokenCredits").value(200));
        assertEquals(0,credits.snapshot(id).total());
        assertEquals(1L,db.queryForObject("SELECT used_credits FROM pdf_ai_usage WHERE visitor_id=?",Long.class,id));
        mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").header("Authorization","Bearer "+token)).andExpect(status().isPaymentRequired());
        verify(model,times(1)).complete(anyString(),anyString(),anyInt(),anyBoolean());
    }
    @Test void underestimatedUsageUsesBothSourcesWithoutDebt() {
        String id=owner(true,50);
        db.update("UPDATE pdf_ai_wallet SET monthly_used=1999900 WHERE visitor_id=?",id);
        var reserved=credits.reserve(id,20);
        var charged=credits.settle(id,reserved,200);
        assertEquals(150,charged.credits());assertEquals(100,charged.monthly());assertEquals(50,charged.topup());
        assertEquals(0,credits.snapshot(id).total());
    }
    private byte[] samplePdf() throws Exception {
        try(var doc=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();doc.addPage(page);
            try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,page)) {
                content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
                content.newLineAtOffset(30,700);content.showText("Synthetic test document. Revenue is 100 and total cost is 50. This is a fictional business report.");content.endText();
            }
            doc.save(out);return out.toByteArray();
        }
    }
    @Test void purchaseUsesServerPriceAndAmountAndPackageIsRequiredToBeValid() throws Exception {
        String token=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).get("token").asText();
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String id=db.queryForObject("SELECT id FROM pdf_visitor WHERE token_hash=?",String.class,hash);
        db.update("UPDATE pdf_visitor SET wechat_openid=? WHERE id=?",UUID.randomUUID().toString(),id);
        when(pay.enabled()).thenReturn(true);
        when(pay.createMiniProgramOrder(anyString(),anyString(),eq(990),anyString())).thenReturn(Map.of("package","synthetic-prepay"));
        mvc.perform(post("/api/pdf/purchase").header("Authorization","Bearer "+token).contentType("application/json")
                .content("{\"packageId\":\"ai-topup\",\"amountFen\":1,\"credits\":99999999}"))
                .andExpect(status().isOk());
        var order=db.queryForMap("SELECT amount_fen,credits,status FROM pdf_purchase WHERE visitor_id=?",id);
        assertEquals(990,((Number)order.get("amount_fen")).intValue());assertEquals(2000000,((Number)order.get("credits")).intValue());
        assertEquals("pending",order.get("status"));assertEquals(0,credits.snapshot(id).topup());
        mvc.perform(post("/api/pdf/purchase").header("Authorization","Bearer "+token).contentType("application/json").content("{\"packageId\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void serverTestModeBypassesBalanceWithoutChargingAndKeepsUsageLimits() throws Exception {
        String token=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).get("token").asText();
        String auth="Bearer "+token;
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String id=db.queryForObject("SELECT id FROM pdf_visitor WHERE token_hash=?",String.class,hash);
        var file=new org.springframework.mock.web.MockMultipartFile("files","synthetic.pdf","application/pdf",samplePdf());
        when(model.enabled()).thenReturn(true);
        when(model.complete(anyString(),anyString(),anyInt(),anyBoolean())).thenReturn(new DeepSeekService.Result("Test summary [A-P1]",100,25));
        // Default is paid mode; a client-supplied flag cannot bypass the server configuration.
        mvc.perform(get("/api/pdf/ai/status").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.testMode").value(false)).andExpect(jsonPath("$.canUse").value(false));
        mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").param("testMode","true").header("Authorization",auth))
                .andExpect(status().isPaymentRequired());
        org.springframework.test.util.ReflectionTestUtils.setField(ai,"testMode",true);
        try {
            mvc.perform(get("/api/pdf/ai/status").header("Authorization",auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.testMode").value(true)).andExpect(jsonPath("$.canUse").value(true));
            mvc.perform(multipart("/api/pdf/ai/estimate").file(file).param("mode","summary").header("Authorization",auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.canRun").value(true)).andExpect(jsonPath("$.reservedTokenCredits").value(0));
            mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").header("Authorization",auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.testMode").value(true))
                    .andExpect(jsonPath("$.usedTokenCredits").value(0)).andExpect(jsonPath("$.actualTokenCredits").value(200));
            // Paid balances must also remain untouched when the same account is funded.
            db.update("INSERT INTO pdf_membership(visitor_id,expires_at) VALUES (?,?)",id,Timestamp.valueOf(PdfAiCredits.now().plusMonths(1).withNano(0)));
            credits.snapshot(id);
            db.update("UPDATE pdf_ai_wallet SET monthly_used=123,topup_balance=456 WHERE visitor_id=?",id);
            mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").header("Authorization",auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.monthlyChargedCredits").value(0)).andExpect(jsonPath("$.topupChargedCredits").value(0));
            assertEquals(1999877,credits.snapshot(id).monthly());assertEquals(456,credits.snapshot(id).topup());
            var usage=db.queryForMap("SELECT input_tokens,output_tokens,used_credits FROM pdf_ai_usage WHERE visitor_id=?",id);
            assertEquals(200,((Number)usage.get("input_tokens")).intValue());assertEquals(50,((Number)usage.get("output_tokens")).intValue());
            assertEquals(0,((Number)usage.get("used_credits")).intValue());
            db.update("UPDATE pdf_ai_daily_usage SET request_count=100 WHERE visitor_id=?",id);
            mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").header("Authorization",auth)).andExpect(status().isTooManyRequests());
            when(model.enabled()).thenReturn(false);
            mvc.perform(multipart("/api/pdf/ai/estimate").file(file).param("mode","summary").header("Authorization",auth)).andExpect(status().isServiceUnavailable());
        } finally {org.springframework.test.util.ReflectionTestUtils.setField(ai,"testMode",false);}
        String freshToken=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).get("token").asText();
        when(model.enabled()).thenReturn(true);
        mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").header("Authorization","Bearer "+freshToken)).andExpect(status().isPaymentRequired());
    }
    @Test void paidTopupCallbackIsIdempotentAndChecksAmount() throws Exception {
        String id=owner(false,0),order=UUID.randomUUID().toString(),trade=UUID.randomUUID().toString().replace("-","");
        db.update("INSERT INTO pdf_purchase(id,visitor_id,package_id,amount_fen,credits,status,out_trade_no) VALUES (?,?,'ai-topup',990,2000000,'pending',?)",order,id,trade);
        when(pay.matchesMerchant(anyString(),anyString())).thenReturn(true);
        String body="{\"trade_state\":\"SUCCESS\",\"trade_type\":\"JSAPI\",\"appid\":\"test\",\"mchid\":\"test\",\"out_trade_no\":\""+trade+"\",\"transaction_id\":\""+UUID.randomUUID()+"\",\"amount\":{\"total\":990}}";
        when(pay.verifyAndDecrypt(any(),any(),any(),any(),any())).thenReturn(json.readTree(body.replace("990","989")));
        mvc.perform(post("/api/pdf/pay/notify").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        assertEquals(0,credits.snapshot(id).topup());
        when(pay.verifyAndDecrypt(any(),any(),any(),any(),any())).thenReturn(json.readTree(body));
        for(int i=0;i<2;i++)mvc.perform(post("/api/pdf/pay/notify").contentType("application/json").content("{}")).andExpect(status().isOk());
        assertEquals(2000000,credits.snapshot(id).topup());assertFalse(credits.snapshot(id).vip());
    }
    @Test void monthlyCallbackGrantsOneMonthAndRenewalPreservesCurrentConsumption() throws Exception {
        String id=owner(false,400);
        when(pay.matchesMerchant(anyString(),anyString())).thenReturn(true);
        java.time.LocalDateTime initialExpiry=null;
        for(int i=0;i<2;i++) {
            String order=UUID.randomUUID().toString(),trade=UUID.randomUUID().toString().replace("-","");
            db.update("INSERT INTO pdf_purchase(id,visitor_id,package_id,amount_fen,credits,status,out_trade_no) VALUES (?,?,'monthly',990,1,'pending',?)",order,id,trade);
            String body="{\"trade_state\":\"SUCCESS\",\"trade_type\":\"JSAPI\",\"appid\":\"test\",\"mchid\":\"test\",\"out_trade_no\":\""+trade+"\",\"transaction_id\":\""+UUID.randomUUID()+"\",\"amount\":{\"total\":990}}";
            when(pay.verifyAndDecrypt(any(),any(),any(),any(),any())).thenReturn(json.readTree(body));
            mvc.perform(post("/api/pdf/pay/notify").contentType("application/json").content("{}")).andExpect(status().isOk());
            var expiry=db.queryForObject("SELECT expires_at FROM pdf_membership WHERE visitor_id=?",Timestamp.class,id).toLocalDateTime();
            if(i==0) {
                assertTrue(expiry.isBefore(PdfAiCredits.now().plusMonths(1).plusMinutes(1)));
                assertTrue(expiry.isAfter(PdfAiCredits.now().plusMonths(1).minusMinutes(1)));
                initialExpiry=expiry; credits.reserve(id,1000);
            } else assertEquals(initialExpiry.plusMonths(1),expiry);
        }
        assertEquals(1999000,credits.snapshot(id).monthly());assertEquals(400,credits.snapshot(id).topup());
    }
}
