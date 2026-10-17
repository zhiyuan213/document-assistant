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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:daily-free;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","PDF_AI_DAILY_FREE_CREDITS=5000"})
@AutoConfigureMockMvc
class PdfDailyFreeCreditsTest {
    @Autowired PdfAiCredits credits;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean DeepSeekService model;

    private String owner() {
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO pdf_visitor(id,token_hash) VALUES (?,?)",id,UUID.randomUUID().toString());
        return id;
    }
    @Test void repeatedReadsGrantOnlyOnceAndFreeIsSpentBeforeBothPaidSources() {
        String id=owner();
        assertEquals(5000,credits.snapshot(id).free());assertEquals(5000,credits.snapshot(id).total());
        db.update("INSERT INTO pdf_membership(visitor_id,expires_at) VALUES (?,?)",id,Timestamp.valueOf(PdfAiCredits.now().plusMonths(1).withNano(0)));
        credits.grantTopup(id,100);
        db.update("UPDATE pdf_ai_wallet SET monthly_used=1999900 WHERE visitor_id=?",id);
        var reservation=credits.reserve(id,5150);
        assertEquals(5000,reservation.free());assertEquals(100,reservation.monthly());assertEquals(50,reservation.topup());
        assertEquals(0,credits.snapshot(id).free());
        var settled=credits.settle(id,reservation,5050);
        assertEquals(5000,settled.free());assertEquals(50,settled.monthly());assertEquals(0,settled.topup());
        assertEquals(50,credits.snapshot(id).monthly());assertEquals(100,credits.snapshot(id).topup());
    }
    @Test void failedTaskRefundsOriginalFreeBalanceAndOverageNeverCreatesDebt() {
        String id=owner();var reservation=credits.reserve(id,10000);
        assertEquals(5000,reservation.credits());assertEquals(0,credits.snapshot(id).total());
        credits.settle(id,reservation,0);assertEquals(5000,credits.snapshot(id).free());
        var second=credits.reserve(id,100);
        assertEquals(5000,credits.settle(id,second,9000).credits());
        assertEquals(0,credits.snapshot(id).total());assertEquals(0,credits.snapshot(id).topup());
        assertThrows(ApiError.class,()->credits.reserve(id,1));
    }
    @Test void nextDayResetsWithoutRolloverAndOldSettlementsCannotChangeNewFreeBalance() {
        String id=owner();var reserved=credits.reserve(id,3000);
        var yesterday=PdfAiCredits.now().toLocalDate().minusDays(1);
        db.update("UPDATE pdf_ai_daily_free SET credit_date=? WHERE visitor_id=?",yesterday,id);
        var old=new PdfAiCredits.Reservation(reserved.credits(),reserved.monthly(),reserved.topup(),reserved.periodEnd(),reserved.free(),yesterday);
        assertEquals(5000,credits.snapshot(id).free());
        credits.settle(id,old,0);assertEquals(5000,credits.snapshot(id).free());
        var oldUsage=new PdfAiCredits.Reservation(3000,0,0,null,3000,yesterday);
        assertEquals(3000,credits.settle(id,oldUsage,6000).credits());
        assertEquals(5000,credits.snapshot(id).free());
    }
    @Test void concurrentReservationsCannotDuplicateDailyGift() throws Exception {
        String id=owner();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Long> task=()->{gate.await();try{return credits.reserve(id,4000).free();}catch(ApiError e){return 0L;}};
            var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
            assertEquals(5000,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(0,credits.snapshot(id).free());
    }
    @Test void linkedVisitorsUseOneDailyAllowance() {
        String a=owner(),b=owner(),openid=UUID.randomUUID().toString();
        db.update("UPDATE pdf_visitor SET wechat_openid=?,created_at=? WHERE id=?",openid,Timestamp.valueOf(PdfAiCredits.now().minusDays(1)),a);
        db.update("UPDATE pdf_visitor SET wechat_openid=? WHERE id=?",openid,b);
        var reserved=credits.reserve(credits.owner(a),5000);credits.settle(credits.owner(a),reserved,5000);
        assertEquals(a,credits.owner(b));assertEquals(0,credits.snapshot(credits.owner(b)).free());
    }
    @Test void unpaidUserCanProcessAndStatusIncludesRemainingGift() throws Exception {
        String token=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).path("token").asText();
        String auth="Bearer "+token;
        when(model.enabled()).thenReturn(true);
        when(model.complete(anyString(),anyString(),anyInt(),anyBoolean())).thenReturn(new DeepSeekService.Result("Summary",100,25));
        mvc.perform(get("/api/pdf/ai/status").header("Authorization",auth)).andExpect(jsonPath("$.canUse").value(true)).andExpect(jsonPath("$.freeRemainingTokenCredits").value(5000));
        byte[] bytes;
        try(var doc=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();doc.addPage(page);
            try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,page)) {
                content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
                content.newLineAtOffset(30,700);content.showText("Synthetic daily free allowance test document with revenue 100 and costs 50.");content.endText();
            }
            doc.save(out);bytes=out.toByteArray();
        }
        var file=new org.springframework.mock.web.MockMultipartFile("files","synthetic.pdf","application/pdf",bytes);
        mvc.perform(multipart("/api/pdf/ai").file(file).param("mode","summary").header("Authorization",auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.freeChargedCredits").value(200))
            .andExpect(jsonPath("$.monthlyChargedCredits").value(0)).andExpect(jsonPath("$.topupChargedCredits").value(0));
        mvc.perform(get("/api/pdf/ai/status").header("Authorization",auth)).andExpect(jsonPath("$.freeRemainingTokenCredits").value(4800)).andExpect(jsonPath("$.remainingTokenCredits").value(4800)).andExpect(jsonPath("$.dailyRemaining").value(99));
    }
}
