package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"PDF_AI_DAILY_FREE_CREDITS=0","spring.datasource.url=jdbc:h2:mem:virtual-pay;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","PDF_BILLING_ENABLED=true","PDF_PAYMENT_PROVIDER=virtual"})
@AutoConfigureMockMvc
class PdfVirtualPaymentsTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;@Autowired PdfAiCredits credits;@Autowired PdfVirtualPayments payments;
    @MockitoBean WechatVirtualPayment pay;
    String token,openid;
    @BeforeEach void setup()throws Exception {
        token=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).path("token").asText();openid="openid-"+UUID.randomUUID();
        when(pay.enabled()).thenReturn(true);when(pay.offerId()).thenReturn("offer");when(pay.environment()).thenReturn(0);when(pay.product("monthly")).thenReturn("ai_monthly");when(pay.product("ai-topup")).thenReturn("ai_topup");
        when(pay.login(anyString())).thenReturn(new WechatVirtualPayment.Session(openid,"test-session"));when(pay.parameters(anyString(),anyString(),anyInt(),any())).thenReturn(Map.of("mode","short_series_goods","signData","{}","paySig","signed","signature","signed"));
        when(pay.pushCrypto()).thenReturn(new WechatPushCrypto("wx-test","testToken123",WechatVirtualPaymentTest.AES,json));
    }
    String purchase(String packageId)throws Exception {
        return json.readTree(mvc.perform(post("/api/pdf/purchase").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(Map.of("packageId",packageId,"code","fresh-wx-code")))).andExpect(status().isOk()).andExpect(jsonPath("$.paymentProvider").value("virtual")).andReturn().getResponse().getContentAsString()).path("orderId").asText();
    }
    Map<String,Object> order(String id){return db.queryForMap("SELECT * FROM pdf_purchase WHERE id=?",id);}
    String owner(String id){return order(id).get("visitor_id").toString();}
    JsonNode receipt(String id,int status,int refunded)throws Exception {
        var order=order(id);return json.valueToTree(Map.of("order_id",order.get("out_trade_no"),"order_fee",990,"left_fee",990-refunded,"status",status,"env_type",1,"order_type",0,"wx_order_id","wx-"+id));
    }
    void respond(String id,int status,int refunded)throws Exception {when(pay.query(eq(openid),eq(order(id).get("out_trade_no").toString()),eq(0),eq("offer"))).thenReturn(receipt(id,status,refunded));}
    JsonNode delivery(String id){var order=order(id);return json.valueToTree(Map.of("Event","xpay_goods_deliver_notify","OutTradeNo",order.get("out_trade_no"),"OpenId",openid,"Env",0,"GoodsInfo",Map.of("ProductId",order.get("package_id").equals("monthly")?"ai_monthly":"ai_topup","Quantity",1,"OrigPrice",990)));}
    JsonNode refund(String id,int amount){return json.valueToTree(Map.of("Event","xpay_refund_notify","MchOrderId",order(id).get("out_trade_no"),"OpenId",openid,"Env",0,"RefundFee",amount,"RetCode",0));}
    void callback(JsonNode event,int status)throws Exception {
        String time=""+Instant.now().getEpochSecond(),nonce="nonce",encrypted=WechatVirtualPaymentTest.encrypt(json.writeValueAsString(event),"wx-test");
        mvc.perform(post("/api/pdf/virtual/notify").param("timestamp",time).param("nonce",nonce).param("msg_signature",WechatVirtualPaymentTest.signature(time,nonce,encrypted)).contentType("application/json").content(json.writeValueAsString(Map.of("Encrypt",encrypted)))).andExpect(status().is(status));
    }
    @Test void clientCannotGrantCreditsAndRepeatedCallbacksGrantOnlyOnce()throws Exception {
        String id=purchase("ai-topup"),owner=owner(id);assertEquals(0,credits.snapshot(owner).topup());
        respond(id,1,0);callback(delivery(id),500);assertEquals(0,credits.snapshot(owner).topup());
        respond(id,2,0);callback(delivery(id),200);callback(delivery(id),200);assertEquals(1000000,credits.snapshot(owner).topup());
        var tampered=delivery(id).deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)tampered.path("GoodsInfo")).put("ProductId","wrong");callback(tampered,500);assertEquals(1000000,credits.snapshot(owner).topup());
        String stranger=json.readTree(mvc.perform(post("/api/pdf/visitor")).andReturn().getResponse().getContentAsString()).path("token").asText();mvc.perform(get("/api/pdf/purchase/"+id).header("Authorization","Bearer "+stranger)).andExpect(status().isNotFound());
    }
    @Test void queryRecoveryAndRefundAreIdempotentIncludingConsumedCredits()throws Exception {
        String id=purchase("ai-topup"),owner=owner(id);respond(id,2,0);db.update("UPDATE pdf_virtual_order SET next_check_at=? WHERE purchase_id=?",Timestamp.valueOf(PdfAiCredits.now().minusMinutes(1)),id);
        mvc.perform(get("/api/pdf/purchase/"+id).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.paid").value(true));assertEquals(1000000,credits.snapshot(owner).topup());credits.reserve(owner,500000);
        respond(id,8,990);callback(refund(id,990),200);callback(refund(id,990),200);assertEquals(-500000,credits.snapshot(owner).topup());assertEquals(0,credits.snapshot(owner).total());assertEquals("refunded",order(id).get("status"));
        String next=purchase("ai-topup");respond(next,2,0);callback(delivery(next),200);assertEquals(500000,credits.snapshot(owner).topup());
    }
    @Test void partialRefundAndFullRefundBeforeDeliveryNeverOvergrant()throws Exception {
        String partial=purchase("ai-topup"),owner=owner(partial);respond(partial,4,99);callback(refund(partial,99),200);assertEquals(900000,credits.snapshot(owner).topup());callback(refund(partial,99),200);assertEquals(900000,credits.snapshot(owner).topup());
        String full=purchase("ai-topup");respond(full,8,990);callback(refund(full,990),200);assertEquals(900000,credits.snapshot(owner).topup());
    }
    @Test void legacyTwoMillionOrderKeepsItsOriginalGrantAndRefundAmount()throws Exception {
        String id=purchase("ai-topup"),owner=owner(id);
        db.update("UPDATE pdf_purchase SET credits=2000000 WHERE id=?",id);
        respond(id,2,0);callback(delivery(id),200);callback(delivery(id),200);
        assertEquals(2000000,credits.snapshot(owner).topup());
        respond(id,4,99);callback(refund(id,99),200);assertEquals(1800000,credits.snapshot(owner).topup());
        respond(id,8,990);callback(refund(id,990),200);assertEquals(0,credits.snapshot(owner).topup());
    }
    @Test void mismatchedAmountsAndEnvironmentsNeverDeliver()throws Exception {
        String id=purchase("monthly");var wrong=(com.fasterxml.jackson.databind.node.ObjectNode)receipt(id,2,0);wrong.put("order_fee",989);when(pay.query(anyString(),anyString(),anyInt(),anyString())).thenReturn(wrong);callback(delivery(id),500);assertFalse(credits.snapshot(owner(id)).vip());
        wrong.put("order_fee",990).put("env_type",2);callback(delivery(id),500);assertFalse(credits.snapshot(owner(id)).vip());
        mvc.perform(post("/api/pdf/virtual/notify").contentType("application/json").content(json.writeValueAsString(delivery(id)))).andExpect(status().is5xxServerError());
    }
    @Test void monthlyRenewalsAndRefundsPreserveOtherPurchasesAndConsumption()throws Exception {
        String first=purchase("monthly"),owner=owner(first);respond(first,2,0);callback(delivery(first),200);credits.reserve(owner,1000);
        String second=purchase("monthly");respond(second,2,0);callback(delivery(second),200);
        LocalDateTime two=db.queryForObject("SELECT expires_at FROM pdf_membership WHERE visitor_id=?",Timestamp.class,owner).toLocalDateTime();assertEquals(1999000,credits.snapshot(owner).monthly());
        respond(second,8,990);callback(refund(second,990),200);var one=db.queryForObject("SELECT expires_at FROM pdf_membership WHERE visitor_id=?",Timestamp.class,owner).toLocalDateTime();assertTrue(one.isBefore(two));assertEquals(1999000,credits.snapshot(owner).monthly());callback(refund(second,990),200);assertEquals(one,db.queryForObject("SELECT expires_at FROM pdf_membership WHERE visitor_id=?",Timestamp.class,owner).toLocalDateTime());
    }
    @Test void concurrentNotificationsAndQueryThrottlingDoNotDuplicateBenefits()throws Exception {
        String id=purchase("ai-topup");respond(id,2,0);var executor=Executors.newFixedThreadPool(3);
        try{var futures=new ArrayList<Future<?>>();for(int i=0;i<3;i++)futures.add(executor.submit(()->payments.notification(delivery(id))));for(var task:futures)task.get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
        assertEquals(1000000,credits.snapshot(owner(id)).topup());
        clearInvocations(pay);for(int i=0;i<10;i++)payments.refresh(id);verify(pay,never()).query(anyString(),anyString(),anyInt(),anyString());
    }
    @Test void cloudrunPathProbeAndForwardedEventAreSupported()throws Exception {
        mvc.perform(post("/api/pdf/virtual/cloudrun-notify").contentType("application/json").content("{\"action\":\"CheckContainerPath\"}")).andExpect(status().isOk()).andExpect(content().string("success"));
        String id=purchase("ai-topup");respond(id,2,0);
        doThrow(new SecurityException()).when(pay).cloudrunMessage(anyString(),isNull(),any());
        mvc.perform(post("/api/pdf/virtual/cloudrun-notify").contentType("application/json").content(json.writeValueAsString(delivery(id)))).andExpect(status().isForbidden());
        doReturn(delivery(id)).when(pay).cloudrunMessage(anyString(),eq("wx"),any());
        mvc.perform(post("/api/pdf/virtual/cloudrun-notify").header("x-wx-source","wx").contentType("application/json").content(json.writeValueAsString(delivery(id)))).andExpect(status().isOk());
        assertEquals(1000000,credits.snapshot(owner(id)).topup());
        respond(id,1,0);String unpaid=purchase("ai-topup");respond(unpaid,1,0);doReturn(delivery(unpaid)).when(pay).cloudrunMessage(anyString(),eq("wx"),any());
        mvc.perform(post("/api/pdf/virtual/cloudrun-notify").header("x-wx-source","wx").contentType("application/json").content(json.writeValueAsString(delivery(unpaid)))).andExpect(status().isForbidden());
        assertEquals(1000000,credits.snapshot(owner(id)).topup());
    }
    @Test void safeCallbackUrlCanBeVerified()throws Exception {
        String timestamp=""+Instant.now().getEpochSecond();mvc.perform(get("/api/pdf/virtual/notify").param("timestamp",timestamp).param("nonce","nonce").param("signature",WechatVirtualPaymentTest.signature(timestamp,"nonce")).param("echostr","12345")).andExpect(status().isOk()).andExpect(content().string("12345"));
        mvc.perform(get("/api/pdf/virtual/notify").param("timestamp",timestamp).param("nonce","nonce").param("signature","0".repeat(40)).param("echostr","12345")).andExpect(status().isForbidden());
    }
}
