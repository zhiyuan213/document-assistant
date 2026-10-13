package cn.dengkemiao.docs;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:library;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","app.storage-dir=target/library-files","app.seed-demo=false"})
@AutoConfigureMockMvc
class ResourceIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired Storage storage;@Autowired ObjectMapper json;
    @MockitoBean Auth auth;
    String id;
    @BeforeEach void setup() throws Exception {
        db.update("DELETE FROM resource_download");db.update("DELETE FROM resource_favorite");db.update("DELETE FROM resource_item");
        db.update("INSERT INTO app_user(id,open_id,nickname) VALUES(1,'test1','test'),(2,'test2','test') ON DUPLICATE KEY UPDATE nickname='test'");
        when(auth.user("Bearer one")).thenReturn(1L);when(auth.user("Bearer two")).thenReturn(2L);when(auth.user(null)).thenThrow(new ApiError(401,"请先登录"));
        id=UUID.randomUUID().toString();String key=id+".txt";byte[] bytes="原创英语笔记".getBytes(StandardCharsets.UTF_8);storage.put(key,bytes,"text/plain");
        db.update("INSERT INTO resource_item(id,category_id,topic,name,description,kind,storage_key,size_bytes,tags,is_free) VALUES (?,'english','notes','英语词汇笔记','原创示例','txt',?,?,'英语,笔记',TRUE)",id,key,bytes.length);
    }
    @Test void publicSearchFiltersAndDoesNotExposeStorageKeys() throws Exception {
        mvc.perform(get("/api/resources").param("q","词汇").param("category","english").param("topic","notes")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].storage_key").doesNotExist());
        mvc.perform(get("/api/resources").param("q","%" )).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/resources").param("category","graduate")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/resources/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.storage_key").doesNotExist()).andExpect(jsonPath("$.favorite").value(false));
        mvc.perform(get("/api/resources").param("page","0")).andExpect(status().isBadRequest());
    }
    @Test void favoritesAreIdempotentAndIsolated() throws Exception {
        for(int i=0;i<2;i++)mvc.perform(post("/api/resources/"+id+"/favorite").header("Authorization","Bearer one")).andExpect(status().isOk());
        mvc.perform(get("/api/favorites").header("Authorization","Bearer one")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/favorites").header("Authorization","Bearer two")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(delete("/api/resources/"+id+"/favorite").header("Authorization","Bearer two")).andExpect(status().isOk());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM resource_favorite",Integer.class));
        mvc.perform(delete("/api/resources/"+id+"/favorite").header("Authorization","Bearer one")).andExpect(status().isOk());
        mvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized());
    }
    String createDownload() throws Exception {
        var response=mvc.perform(post("/api/downloads").header("Authorization","Bearer one").contentType("application/json").content("{\"resourceId\":\""+id+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.storage_key").doesNotExist()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("id").asText();
    }
    void receiveFile(String ticket) throws Exception {
        var result=mvc.perform(get("/api/downloads/"+ticket+"/file").header("Authorization","Bearer one")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().bytes("原创英语笔记".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void downloadCountAndStatusRequireActualServeAndEnforceOwnership() throws Exception {
        String ticket=createDownload();
        mvc.perform(put("/api/downloads/"+ticket).header("Authorization","Bearer one").contentType("application/json").content("{\"status\":\"completed\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/downloads/"+ticket+"/file").header("Authorization","Bearer two")).andExpect(status().isNotFound());
        for(int i=0;i<2;i++)receiveFile(ticket);
        assertEquals(1L,db.queryForObject("SELECT downloads_count FROM resource_item WHERE id=?",Long.class,id));
        mvc.perform(put("/api/downloads/"+ticket).header("Authorization","Bearer one").contentType("application/json").content("{\"status\":\"completed\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/downloads/"+ticket).header("Authorization","Bearer two").contentType("application/json").content("{\"status\":\"failed\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/downloads").header("Authorization","Bearer two")).andExpect(jsonPath("$.total").value(0));
    }
    @Test void unavailableAndPaidResourcesAreNotDownloadable() throws Exception {
        db.update("UPDATE resource_item SET is_free=FALSE WHERE id=?",id);
        mvc.perform(post("/api/downloads").header("Authorization","Bearer one").contentType("application/json").content("{\"resourceId\":\""+id+"\"}")).andExpect(status().isForbidden());
        db.update("UPDATE resource_item SET published=FALSE WHERE id=?",id);
        mvc.perform(get("/api/resources/"+id)).andExpect(status().isNotFound());
    }
    @Test void pausedDownloadCanRestartButCompletedDownloadIsTerminal() throws Exception {
        String ticket=createDownload();
        mvc.perform(put("/api/downloads/"+ticket).header("Authorization","Bearer one").contentType("application/json").content("{\"status\":\"paused\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/downloads/"+ticket).header("Authorization","Bearer one").contentType("application/json").content("{\"status\":\"started\"}")).andExpect(status().isOk());
        receiveFile(ticket);
        mvc.perform(put("/api/downloads/"+ticket).header("Authorization","Bearer one").contentType("application/json").content("{\"status\":\"completed\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/downloads/"+ticket).header("Authorization","Bearer one").contentType("application/json").content("{\"status\":\"paused\"}")).andExpect(status().isConflict());
    }
    @Test void downloadHistoryIsPaginated() throws Exception {
        for(int i=0;i<21;i++)db.update("INSERT INTO resource_download(id,user_id,resource_id,status) VALUES (?,1,?,'started')",UUID.randomUUID().toString(),id);
        mvc.perform(get("/api/downloads").header("Authorization","Bearer one")).andExpect(jsonPath("$.total").value(21)).andExpect(jsonPath("$.items.length()").value(20));
        mvc.perform(get("/api/downloads").param("page","2").header("Authorization","Bearer one")).andExpect(jsonPath("$.items.length()").value(1));
    }
}
