package cn.dengkemiao.docs;

import java.io.*;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.PreDestroy;

/** Bounded, owner-scoped transport for the small callContainer envelope.
 * Reuses existing HTTP handlers on loopback, including their authorization and multipart validation.
 * Like existing PDF jobs, transfers require a single service instance with local temporary storage.
 */
@RestController
@RequestMapping("/api/pdf/cloud-transfers")
public class PdfCloudTransfer {
    static final int CHUNK = 48 * 1024, MAX_INPUT = 64 * 1024 * 1024, MAX_OUTPUT = 110 * 1024 * 1024;
    private final JdbcTemplate db;
    private final ApplicationContext context;
    private final Map<String, Transfer> transfers = new HashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    @Value("${PDF_TEMP_DIR:./data/pdf-temp}") private String directory;
    public PdfCloudTransfer(JdbcTemplate db, ApplicationContext context) { this.db = db; this.context = context; }
    static class Transfer {
        final String owner, path, method, contentType, authorization;
        final int size; final long expires = System.currentTimeMillis() + 30 * 60_000;
        final Path input, output;
        int received, statusCode; String state = "uploading", error = "", responseType = ""; long outputSize;
        Transfer(String owner, Start start, String authorization, Path root, String id) {
            this.owner = owner; path = start.path(); method = start.method(); contentType = start.contentType();
            size = start.size(); this.authorization = authorization; input = root.resolve(id + ".in"); output = root.resolve(id + ".out");
        }
    }
    public record Start(String path, String method, String contentType, int size) {}
    public record Chunk(int offset, String data) {}
    private String owner(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) throw new ApiError(401, "设备凭证已过期，请重试");
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(auth.substring(7).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var ids = db.queryForList("SELECT id FROM pdf_visitor WHERE token_hash=?", String.class, hash);
            if (ids.isEmpty()) throw new ApiError(401, "设备凭证已过期，请重试");
            return ids.getFirst();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static void validate(Start start) {
        // Explicit routes prevent use as a proxy, notification relay, or recursive transfer.
        if (start.path() == null || !start.path().matches("/api/pdf/(jobs(?:/[a-zA-Z0-9-]+/file)?|watermarks/(?:inspect|preview)|ai(?:/estimate|/export|/translations/[a-zA-Z0-9-]+/file\\?format=(?:pdf|docx|txt))?)")
                || !Set.of("GET", "POST").contains(start.method()) || start.size() < 0 || start.size() > MAX_INPUT
                || start.contentType() == null || start.contentType().length() > 200 || start.contentType().contains("\r") || start.contentType().contains("\n"))
            throw new ApiError(400, "文件传输参数无效");
    }
    @PostMapping public synchronized Map<String,Object> create(@RequestHeader(value="Authorization", required=false) String auth, @RequestBody Start start) throws IOException {
        String owner = owner(auth); validate(start); cleanup();
        if (transfers.size() >= 8 || transfers.values().stream().filter(t -> t.owner.equals(owner)).count() >= 2) throw new ApiError(429, "文件传输繁忙，请稍后重试");
        Path root = Path.of(directory).toAbsolutePath().resolve("cloud-transfers"); Files.createDirectories(root);
        String id = UUID.randomUUID().toString(); var transfer = new Transfer(owner, start, auth, root, id);
        Files.createFile(transfer.input); transfers.put(id, transfer); return Map.of("id", id, "chunkSize", CHUNK);
    }
    private synchronized Transfer owned(String id, String auth) {
        String owner = owner(auth); var t = transfers.get(id);
        if (t == null || t.expires <= System.currentTimeMillis() || !t.owner.equals(owner)) throw new ApiError(404, "文件传输已过期，请重新处理");
        return t;
    }
    @PostMapping("/{id}/chunks") public Map<String,Object> append(@PathVariable String id, @RequestHeader(value="Authorization", required=false) String auth, @RequestBody Chunk chunk) throws IOException {
        var t = owned(id, auth);
        synchronized(t) {
            if (!t.state.equals("uploading") || chunk.data() == null || chunk.data().length() > CHUNK * 4 / 3) throw new ApiError(400, "文件分块无效");
            byte[] bytes; try { bytes = Base64.getDecoder().decode(chunk.data()); } catch (IllegalArgumentException e) { throw new ApiError(400, "文件分块无效"); }
            if (bytes.length == 0 || bytes.length > CHUNK || chunk.offset() < 0 || chunk.offset() + bytes.length > t.size) throw new ApiError(400, "文件分块超出范围");
            // A response may be lost after writing: identical retries are safe, conflicting retries are rejected.
            try (var file = new RandomAccessFile(t.input.toFile(), "rw")) {
                if (chunk.offset() < t.received) {
                    if (chunk.offset() + bytes.length > t.received) throw new ApiError(409, "文件分块顺序错误");
                    byte[] previous = new byte[bytes.length]; file.seek(chunk.offset()); file.readFully(previous);
                    if (!Arrays.equals(previous, bytes)) throw new ApiError(409, "文件分块内容不一致");
                } else {
                    if (chunk.offset() != t.received) throw new ApiError(409, "文件分块顺序错误");
                    file.seek(t.received); file.write(bytes); t.received += bytes.length;
                }
            }
            return Map.of("received", t.received);
        }
    }
    @PostMapping("/{id}/run") public Map<String,Object> run(@PathVariable String id, @RequestHeader(value="Authorization", required=false) String auth) {
        var t = owned(id, auth);
        synchronized(t) {
            if (t.received != t.size) throw new ApiError(409, "文件尚未上传完成");
            if (t.state.equals("uploading")) { t.state = "processing"; workers.execute(() -> execute(t)); }
            return Map.of("state", t.state);
        }
    }
    private void execute(Transfer t) {
        HttpURLConnection connection = null;
        try {
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + t.path).toURL().openConnection(java.net.Proxy.NO_PROXY);
            connection.setConnectTimeout(5000); connection.setReadTimeout(120000); connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(t.method); connection.setRequestProperty("Authorization", t.authorization); connection.setRequestProperty("Content-Type", t.contentType);
            if (t.size > 0) {
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(t.size);
                try (var input = Files.newInputStream(t.input); var output = connection.getOutputStream()) { input.transferTo(output); }
            }
            int code = connection.getResponseCode();
            InputStream response = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            try (var input = response == null ? InputStream.nullInputStream() : response; var output = Files.newOutputStream(t.output)) {
                byte[] buffer = new byte[8192]; long total = 0; int count;
                while ((count = input.read(buffer)) != -1) { total += count; if (total > MAX_OUTPUT) throw new IOException("Output too large"); output.write(buffer, 0, count); }
            }
            synchronized(t) { t.outputSize = Files.size(t.output); t.statusCode = code; t.responseType = Objects.toString(connection.getContentType(), ""); t.state = "completed"; }
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            synchronized(t) { t.error = "云端处理未完成，请查询原任务或稍后重试"; t.state = "failed"; }
        } finally { if (connection != null) connection.disconnect(); try { Files.deleteIfExists(t.input); } catch (IOException ignored) {} }
    }
    @GetMapping("/{id}") public Map<String,Object> status(@PathVariable String id, @RequestHeader(value="Authorization", required=false) String auth) {
        var t = owned(id, auth); synchronized(t) { return Map.of("state", t.state, "statusCode", t.statusCode, "size", t.outputSize, "contentType", t.responseType, "error", t.error); }
    }
    @GetMapping("/{id}/chunks") public Map<String,Object> read(@PathVariable String id, @RequestHeader(value="Authorization", required=false) String auth, @RequestParam int offset) throws IOException {
        var t = owned(id, auth); synchronized(t) {
            if (!t.state.equals("completed") || offset < 0 || offset >= t.outputSize) throw new ApiError(400, "下载分块无效");
            byte[] bytes = new byte[(int)Math.min(CHUNK, t.outputSize - offset)];
            try (var file = new RandomAccessFile(t.output.toFile(), "r")) { file.seek(offset); file.readFully(bytes); }
            return Map.of("data", Base64.getEncoder().encodeToString(bytes));
        }
    }
    @DeleteMapping("/{id}") public synchronized Map<String,Object> remove(@PathVariable String id, @RequestHeader(value="Authorization", required=false) String auth) throws IOException {
        var t = owned(id, auth); synchronized(t) { if (t.state.equals("processing")) throw new ApiError(409, "文件正在处理"); delete(t); transfers.remove(id); }
        return Map.of("ok", true);
    }
    private void delete(Transfer t) throws IOException { Files.deleteIfExists(t.input); Files.deleteIfExists(t.output); }
    @Scheduled(fixedDelay=60000) public synchronized void cleanup() throws IOException {
        var it = transfers.values().iterator();
        while (it.hasNext()) { var t = it.next(); synchronized(t) { if (t.expires < System.currentTimeMillis() && !t.state.equals("processing")) { delete(t); it.remove(); } } }
        // Remove orphaned files left by a process restart, without touching active transfers.
        Path root = Path.of(directory).toAbsolutePath().resolve("cloud-transfers");
        if (Files.isDirectory(root)) try (var files = Files.list(root)) { for (Path p : files.toList()) if (Files.getLastModifiedTime(p).toMillis() < System.currentTimeMillis() - 30 * 60_000 && transfers.values().stream().noneMatch(t -> t.input.equals(p) || t.output.equals(p))) Files.deleteIfExists(p); }
    }
    @PreDestroy public void stop() { workers.shutdownNow(); }
}
