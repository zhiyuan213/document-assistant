package cn.dengkemiao.docs;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:pdf-tools;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.hikari.maximum-pool-size=1","PDF_TEMP_DIR=target/pdf-test-results","PDF_BILLING_ENABLED=true"})
@AutoConfigureMockMvc
class PdfToolApiTest {
    @Test void membershipExpiresOnCorrespondingCalendarDateIncludingMonthEnds(){
        assertEquals(java.time.LocalDateTime.of(2026,11,14,20,30),PdfToolController.monthEnd(java.time.LocalDateTime.of(2026,10,14,20,30)));
        assertEquals(java.time.LocalDateTime.of(2026,2,28,12,0),PdfToolController.monthEnd(java.time.LocalDateTime.of(2026,1,31,12,0)));
        assertEquals(java.time.LocalDateTime.of(2028,2,29,12,0),PdfToolController.monthEnd(java.time.LocalDateTime.of(2028,1,31,12,0)));
    }
    @Test void memberCountsInputFilesAndFallsBackToFreePlanAfterExpiry()throws Exception {
        String a=token();byte[] source=new PdfService().images(List.of(PdfServiceTest.image()),false,false);
        String first=start(a,"compress",source);waitJob(first,a,"completed");
        String owner=db.queryForObject("SELECT visitor_id FROM pdf_tool_job WHERE id=?",String.class,first);
        var expires=PdfToolController.monthEnd(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        db.update("INSERT INTO pdf_membership(visitor_id,expires_at) VALUES (?,?)",owner,Timestamp.valueOf(expires));
        db.update("UPDATE pdf_daily_quota SET used_files=98 WHERE visitor_id=?",owner);
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.membership.active").value(true)).andExpect(jsonPath("$.remaining").value(2)).andExpect(jsonPath("$.unit").value("files"));
        String job=json.readTree(mvc.perform(multipart("/api/pdf/jobs").file(new MockMultipartFile("files","a.pdf","application/pdf",source)).file(new MockMultipartFile("files","b.pdf","application/pdf",source)).param("tool","merge").header("Authorization",a)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();waitJob(job,a,"completed");
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.remaining").value(0));
        mvc.perform(multipart("/api/pdf/jobs").file(new MockMultipartFile("files","a.pdf","application/pdf",source)).param("tool","compress").header("Authorization",a)).andExpect(status().isTooManyRequests());
        db.update("UPDATE pdf_membership SET expires_at=? WHERE visitor_id=?",Timestamp.valueOf(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusSeconds(1)),owner);
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.membership.active").value(false)).andExpect(jsonPath("$.remaining").value(1)).andExpect(jsonPath("$.unit").value("jobs"));
    }
    @Test void failedMemberMergeRefundsEveryInputFile()throws Exception {
        String a=token();String first=start(a,"compress",new byte[]{1});waitJob(first,a,"failed");
        String owner=db.queryForObject("SELECT visitor_id FROM pdf_tool_job WHERE id=?",String.class,first);
        db.update("INSERT INTO pdf_membership(visitor_id,expires_at) VALUES (?,?)",owner,Timestamp.valueOf(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusMonths(1)));
        db.update("UPDATE pdf_daily_quota SET used_files=98 WHERE visitor_id=?",owner);
        String job=json.readTree(mvc.perform(multipart("/api/pdf/jobs").file(new MockMultipartFile("files","a.pdf","application/pdf",new byte[]{1})).file(new MockMultipartFile("files","b.pdf","application/pdf",new byte[]{2})).param("tool","merge").header("Authorization",a)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();waitJob(job,a,"failed");
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.remaining").value(2));
    }
    @Test void failedJobsPreserveExpiryEvenWithLegacyAutoUpdateColumn()throws Exception {
        db.execute("ALTER TABLE pdf_tool_job ALTER COLUMN expires_at SET ON UPDATE CURRENT_TIMESTAMP");
        try {
            String a=token();String id=start(a,"compress",new byte[]{1,2,3});waitJob(id,a,"failed");
            mvc.perform(get("/api/pdf/jobs/"+id).header("Authorization",a)).andExpect(status().isOk()).andExpect(jsonPath("$.error_message").value("PDF文件无效或已损坏"));
            assertTrue(db.queryForObject("SELECT TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP,expires_at) FROM pdf_tool_job WHERE id=?",Integer.class,id)>1700);
        } finally {db.execute("ALTER TABLE pdf_tool_job ALTER COLUMN expires_at DROP ON UPDATE");}
    }
    @Test void failedJobsStayReadableWhenDatabaseTimezoneDiffers()throws Exception {
        db.execute("SET TIME ZONE '+14:00'");
        try {String a=token();String id=start(a,"compress",new byte[]{1,2,3});waitJob(id,a,"failed");mvc.perform(get("/api/pdf/jobs/"+id).header("Authorization",a)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("failed"));}
        finally {db.execute("SET TIME ZONE 'Asia/Shanghai'");}
    }
    @Test void disabledBillingAllowsRepeatedJobsWithoutChangingExistingQuota()throws Exception {
        String a=token();byte[] source=new PdfService().images(List.of(PdfServiceTest.image()),false,false);
        String first=start(a,"compress",source);waitJob(first,a,"completed");
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"billingEnabled",false);
        try {
            mvc.perform(get("/api/pdf/config")).andExpect(jsonPath("$.billingEnabled").value(false));
            mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.unlimited").value(true));
            for(int i=0;i<2;i++)waitJob(start(a,"compress",source),a,"completed");
            waitJob(start(a,"compress",new byte[]{1,2}),a,"failed");
        } finally {org.springframework.test.util.ReflectionTestUtils.setField(controller,"billingEnabled",true);}
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.remaining").value(2));
    }
    @Test void healthStaysPublicAndLegacyRoutesAreGone()throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(get("/__tcb_probe__")).andExpect(status().isOk());
        mvc.perform(get("/api/resources")).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}")).andExpect(status().isNotFound());
    }
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired PdfToolController controller;
    private String token()throws Exception{return "Bearer "+json.readTree(mvc.perform(post("/api/pdf/visitor")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();}
    private String start(String token,String tool,byte[] file)throws Exception{return json.readTree(mvc.perform(multipart("/api/pdf/jobs").file(new MockMultipartFile("files","test.pdf","application/pdf",file)).param("tool",tool).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();}
    private void waitJob(String id,String token,String expected)throws Exception{for(int i=0;i<80;i++){var job=json.readTree(mvc.perform(get("/api/pdf/jobs/"+id).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());if(job.get("status").asText().equals(expected))return;Thread.sleep(50);}fail("Job did not reach "+expected);}
    @Test void anonymousProcessingOwnsResultsAndQuotaCannotBeBypassed()throws Exception{
        String a=token(),b=token();byte[] source=new PdfService().images(List.of(PdfServiceTest.image()),false,false);String id=start(a,"compress",source);waitJob(id,a,"completed");
        mvc.perform(get("/api/pdf/jobs/"+id+"/file").header("Authorization",a)).andExpect(status().isOk());
        mvc.perform(get("/api/pdf/jobs/"+id+"/file").header("Authorization",b)).andExpect(status().isNotFound());
        waitJob(start(a,"compress",source),a,"completed");waitJob(start(a,"compress",source),a,"completed");
        mvc.perform(multipart("/api/pdf/jobs").file(new MockMultipartFile("files","a.pdf","application/pdf",source)).param("tool","compress").header("Authorization",a)).andExpect(status().isPaymentRequired());
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.remaining").value(0));
        mvc.perform(delete("/api/pdf/jobs/"+id).header("Authorization",a)).andExpect(status().isOk());
        mvc.perform(get("/api/pdf/jobs/"+id+"/file").header("Authorization",a)).andExpect(status().isNotFound());
    }
    @Test void failuresRefundAndExpiredFilesAreDeleted()throws Exception{
        String a=token();String id=start(a,"compress",new byte[]{1,2,3});waitJob(id,a,"failed");
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.remaining").value(3));
        db.update("UPDATE pdf_tool_job SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),id);controller.cleanup();
        mvc.perform(get("/api/pdf/jobs/"+id).header("Authorization",a)).andExpect(status().isNotFound());
    }
    @Test void noClientClaimCanGrantPaymentOrAdCredits()throws Exception{
        mvc.perform(post("/api/pdf/purchase")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/pdf/ad-reward")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/pdf/config")).andExpect(jsonPath("$.dailyFree").value(3)).andExpect(jsonPath("$.packages[0].priceFen").value(990)).andExpect(jsonPath("$.packages[0].dailyFileLimit").value(100)).andExpect(jsonPath("$.packages[0].months").value(1));
    }
}
