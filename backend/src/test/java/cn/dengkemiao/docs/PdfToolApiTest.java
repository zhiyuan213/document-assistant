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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:pdf-tools;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","PDF_TEMP_DIR=target/pdf-test-results","PDF_BILLING_ENABLED=true"})
@AutoConfigureMockMvc
class PdfToolApiTest {
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
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.remaining").value(0));
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
        mvc.perform(multipart("/api/pdf/jobs").file(new MockMultipartFile("files","a.pdf","application/pdf",source)).param("tool","compress").header("Authorization",a)).andExpect(status().isPaymentRequired());
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.remaining").value(0));
        mvc.perform(delete("/api/pdf/jobs/"+id).header("Authorization",a)).andExpect(status().isOk());
        mvc.perform(get("/api/pdf/jobs/"+id+"/file").header("Authorization",a)).andExpect(status().isNotFound());
    }
    @Test void failuresRefundAndExpiredFilesAreDeleted()throws Exception{
        String a=token();String id=start(a,"compress",new byte[]{1,2,3});waitJob(id,a,"failed");
        mvc.perform(get("/api/pdf/quota").header("Authorization",a)).andExpect(jsonPath("$.remaining").value(1));
        db.update("UPDATE pdf_tool_job SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),id);controller.cleanup();
        mvc.perform(get("/api/pdf/jobs/"+id).header("Authorization",a)).andExpect(status().isNotFound());
    }
    @Test void noClientClaimCanGrantPaymentOrAdCredits()throws Exception{
        mvc.perform(post("/api/pdf/purchase")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/pdf/ad-reward")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/pdf/config")).andExpect(jsonPath("$.packages[0].priceFen").value(600)).andExpect(jsonPath("$.packages[1].credits").value(18));
    }
}
