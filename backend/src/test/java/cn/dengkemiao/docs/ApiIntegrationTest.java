package cn.dengkemiao.docs;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:dengke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","app.storage-dir=target/test-files","app.free-daily-limit=2"})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired ObjectMapper json;
    @MockitoBean Auth auth; @MockitoBean StringRedisTemplate redis;
    @BeforeEach void setup(){
        db.update("DELETE FROM ocr_record");db.update("DELETE FROM file_info");db.update("DELETE FROM file_task");db.update("DELETE FROM user_quota");
        db.update("INSERT INTO app_user(id,open_id,nickname) VALUES(1,'test1','test'),(2,'test2','test') ON DUPLICATE KEY UPDATE nickname='test'");
        when(auth.user("Bearer one")).thenReturn(1L);when(auth.user("Bearer two")).thenReturn(2L);
        when(auth.user(null)).thenThrow(new ApiError(401,"请先登录"));
    }
    @Test void createsDownloadsIsolatesAndDeletesFile() throws Exception {
        var response=mvc.perform(multipart("/api/process/images").file(new MockMultipartFile("files","one.png","image/png",PdfServiceTest.image())).header("Authorization","Bearer one")).andExpect(status().isOk()).andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).path("file").path("id").asText();
        mvc.perform(get("/api/files/"+id+"/download").header("Authorization","Bearer one")).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/api/files/"+id+"/download").header("Authorization","Bearer two")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/files/"+id).header("Authorization","Bearer two")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/files/"+id).header("Authorization","Bearer one")).andExpect(status().isOk());
        mvc.perform(get("/api/files/"+id+"/download").header("Authorization","Bearer one")).andExpect(status().isNotFound());
    }
    @Test void failedOperationRefundsQuota() throws Exception {
        mvc.perform(multipart("/api/process/images").file(new MockMultipartFile("files","broken.png","image/png",new byte[]{1,2,3})).header("Authorization","Bearer one")).andExpect(status().isBadRequest());
        assertEquals(0,db.queryForObject("SELECT used_count FROM user_quota WHERE user_id=1",Integer.class));
        assertEquals("failed",db.queryForObject("SELECT status FROM file_task WHERE user_id=1",String.class));
    }
    @Test void quotaLimitAndAuthenticationAreEnforced() throws Exception {
        mvc.perform(get("/api/files")).andExpect(status().isUnauthorized());
        for(int i=0;i<2;i++)mvc.perform(multipart("/api/process/images").file(new MockMultipartFile("files","one.png","image/png",PdfServiceTest.image())).header("Authorization","Bearer one")).andExpect(status().isOk());
        mvc.perform(multipart("/api/process/images").file(new MockMultipartFile("files","one.png","image/png",PdfServiceTest.image())).header("Authorization","Bearer one")).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/me").header("Authorization","Bearer one")).andExpect(jsonPath("$.quota.remaining").value(0));
    }
    @Test void highQualityCannotBypassEntitlement() throws Exception {
        mvc.perform(multipart("/api/process/images").file(new MockMultipartFile("files","one.png","image/png",PdfServiceTest.image())).param("highQuality","true").header("Authorization","Bearer one")).andExpect(status().isForbidden());
    }
}
