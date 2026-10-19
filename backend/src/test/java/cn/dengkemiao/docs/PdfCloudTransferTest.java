package cn.dengkemiao.docs;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PdfCloudTransferTest {
    @TempDir Path directory;
    @Test void chunksRetainBytesAndAuthAndRejectOtherOwners() throws Exception {
        var server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        byte[] input = new byte[110000]; new Random(42).nextBytes(input);
        var received = new java.util.concurrent.CompletableFuture<byte[]>();
        var responder = new Thread(() -> {
            try (var socket = server.accept()) {
                socket.setSoTimeout(5000); var stream = socket.getInputStream(); var headers = new java.io.ByteArrayOutputStream(); int b;
                while ((b = stream.read()) != -1) { headers.write(b); if (headers.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) break; }
                String text = headers.toString(StandardCharsets.US_ASCII); assertTrue(text.contains("Authorization: Bearer test"));
                received.complete(stream.readNBytes(input.length));
                socket.getOutputStream().write("HTTP/1.1 201 Created\r\nContent-Length: 16\r\nConnection: close\r\n\r\nsynthetic result".getBytes(StandardCharsets.US_ASCII));
            } catch (Throwable e) { received.completeExceptionally(e); }
        }); responder.setDaemon(true); responder.start();
        var db = mock(JdbcTemplate.class); when(db.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of("owner"));
        var context = mock(ServletWebServerApplicationContext.class); var webServer = mock(WebServer.class);
        when(context.getWebServer()).thenReturn(webServer); when(webServer.getPort()).thenReturn(server.getLocalPort());
        var service = new PdfCloudTransfer(db, context); ReflectionTestUtils.setField(service, "directory", directory.toString());
        try {
            String id = service.create("Bearer test", new PdfCloudTransfer.Start("/api/pdf/jobs", "POST", "application/octet-stream", input.length)).get("id").toString();
            assertEquals(401, assertThrows(ApiError.class, () -> service.status(id, null)).status);
            assertEquals(409, assertThrows(ApiError.class, () -> service.run(id, "Bearer test")).status);
            assertEquals(409, assertThrows(ApiError.class, () -> service.append(id, "Bearer test", new PdfCloudTransfer.Chunk(1, "AA=="))).status);
            assertEquals(400, assertThrows(ApiError.class, () -> service.append(id, "Bearer test", new PdfCloudTransfer.Chunk(0, "A".repeat(70000)))).status);
            String extra = service.create("Bearer test", new PdfCloudTransfer.Start("/api/pdf/jobs", "POST", "application/json", 0)).get("id").toString();
            assertEquals(429, assertThrows(ApiError.class, () -> service.create("Bearer test", new PdfCloudTransfer.Start("/api/pdf/jobs", "POST", "application/json", 0))).status);
            service.remove(extra, "Bearer test");
            for (int offset = 0; offset < input.length; offset += PdfCloudTransfer.CHUNK) {
                var part = new PdfCloudTransfer.Chunk(offset, Base64.getEncoder().encodeToString(Arrays.copyOfRange(input, offset, Math.min(input.length, offset + PdfCloudTransfer.CHUNK))));
                service.append(id, "Bearer test", part); service.append(id, "Bearer test", part);
            }
            assertEquals(409, assertThrows(ApiError.class, () -> service.append(id, "Bearer test", new PdfCloudTransfer.Chunk(0, "AA=="))).status);
            service.run(id, "Bearer test");
            Map<String,Object> status = null;
            for (int i = 0; i < 100; i++) { status = service.status(id, "Bearer test"); if (!status.get("state").equals("processing")) break; Thread.sleep(20); }
            assertEquals("completed", status.get("state")); assertEquals(201, status.get("statusCode"));
            assertArrayEquals(input, received.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("synthetic result", new String(Base64.getDecoder().decode(service.read(id, "Bearer test", 0).get("data").toString())));
            when(db.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of("other-owner"));
            assertEquals(404, assertThrows(ApiError.class, () -> service.read(id, "Bearer other", 0)).status);
            when(db.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of("owner"));
            service.remove(id, "Bearer test"); assertEquals(404, assertThrows(ApiError.class, () -> service.status(id, "Bearer test")).status);
        } finally { service.stop(); server.close(); }
    }
    @Test void rejectsProxyRecursionTraversalAndOversizedBodies() {
        for (String path : List.of("https://example.com", "/api/pdf/cloud-transfers", "/api/pdf/../admin", "/api/pdf/pay/notify", "/api/pdf/jobs?redirect=http://example.com"))
            assertThrows(ApiError.class, () -> PdfCloudTransfer.validate(new PdfCloudTransfer.Start(path, "POST", "application/json", 0)));
        assertThrows(ApiError.class, () -> PdfCloudTransfer.validate(new PdfCloudTransfer.Start("/api/pdf/jobs", "POST", "application/json", PdfCloudTransfer.MAX_INPUT + 1)));
    }
}
