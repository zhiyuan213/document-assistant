package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class DeepSeekService {
    private final ObjectMapper json;
    private HttpClient http;
    private synchronized HttpClient http() {
        if (http == null) http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
        return http;
    }
    @Value("${DEEPSEEK_API_URL:}") private String apiUrl;
    @Value("${DEEPSEEK_API_KEY:}") private String apiKey;
    @Value("${DEEPSEEK_MODEL:deepseek-flash}") private String model;

    public DeepSeekService(ObjectMapper json) { this.json = json; }

    public boolean enabled() {
        if (apiUrl == null || apiUrl.isBlank() || apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) return false;
        try { return "https".equalsIgnoreCase(URI.create(apiUrl).getScheme()); }
        catch (Exception e) { return false; }
    }

    public Result complete(String system, String user, int maxTokens, boolean jsonMode) {
        var messages = List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user));
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model); body.put("messages", messages); body.put("max_tokens", maxTokens); body.put("stream", false);
        body.put("thinking", Map.of("type", "disabled"));
        if (jsonMode) body.put("response_format", Map.of("type", "json_object"));
        return call(body);
    }

    public Result inspectImages(String prompt, List<String> jpegBase64, int maxTokens) {
        var content = new ArrayList<Map<String, Object>>();
        content.add(Map.of("type", "text", "text", prompt));
        for (String image : jpegBase64) content.add(Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64," + image, "detail", "high")));
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model); body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("max_tokens", Math.max(256, maxTokens)); body.put("stream", false);
        body.put("thinking", Map.of("type", "disabled"));
        return call(body);
    }

    private Result call(Map<String, Object> body) {
        if (!enabled()) throw new ApiError(503, "AI 服务尚未配置");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl)).timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("DeepSeek request failed with HTTP {}", response.statusCode());
                throw new ApiError(response.statusCode() == 429 ? 429 : 502, response.statusCode() == 429 ? "AI 服务繁忙，请稍后重试" : "AI 服务暂时不可用，请稍后重试");
            }
            JsonNode result = json.readTree(response.body());
            String answer = result.path("choices").path(0).path("message").path("content").asText("");
            if (answer.isBlank()) throw new ApiError(502, "AI 没有返回有效内容，请调整问题后重试");
            if ("length".equals(result.path("choices").path(0).path("finish_reason").asText())) answer += "\n\n【内容未完整生成：已达到单次输出上限，请拆分文档后处理。本次按实际用量结算。】";
            JsonNode usage = result.path("usage");
            if (!usage.path("prompt_tokens").canConvertToInt() || !usage.path("completion_tokens").canConvertToInt()
                    || usage.path("prompt_tokens").asInt()<0 || usage.path("completion_tokens").asInt()<0)
                throw new ApiError(502, "AI 未返回有效用量记录，已保留预占额度，请稍后查看");
            return new Result(answer, result.path("usage").path("prompt_tokens").asInt(0), result.path("usage").path("completion_tokens").asInt(0));
        } catch (ApiError e) { throw e; }
        catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("DeepSeek request failed ({})", e.getClass().getSimpleName());
            throw new ApiError(502, "连接 AI 服务失败，请检查接口地址、网络和配置");
        }
    }

    public record Result(String answer, int inputTokens, int outputTokens) {}
}
