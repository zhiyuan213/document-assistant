package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;

@Service
public class WechatPayService {
    private final ObjectMapper json;
    private HttpClient http;
    private synchronized HttpClient http() {
        if (http == null) http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
        return http;
    }
    @Value("${WECHAT_APP_ID:}") private String appId;
    @Value("${WECHAT_APP_SECRET:}") private String appSecret;
    @Value("${WECHAT_PAY_MCH_ID:}") private String mchId;
    @Value("${WECHAT_PAY_MCH_SERIAL_NO:}") private String mchSerial;
    @Value("${WECHAT_PAY_MCH_PRIVATE_KEY_BASE64:}") private String privateKeyBase64;
    @Value("${WECHAT_PAY_API_V3_KEY:}") private String apiV3Key;
    @Value("${WECHAT_PAY_PLATFORM_CERT_BASE64:}") private String platformCertBase64;
    @Value("${WECHAT_PAY_PLATFORM_SERIAL:}") private String platformSerial;
    @Value("${WECHAT_PAY_NOTIFY_URL:}") private String notifyUrl;

    public WechatPayService(ObjectMapper json) { this.json = json; }

    public boolean enabled() {
        if (!(present(appId) && present(appSecret) && present(mchId) && present(mchSerial)
                && present(privateKeyBase64) && present(apiV3Key) && apiV3Key.getBytes(StandardCharsets.UTF_8).length == 32
                && present(platformCertBase64) && present(platformSerial) && present(notifyUrl))) return false;
        try { readPrivateKey(privateKeyBase64); readCertificate(platformCertBase64); return true; }
        catch (Exception e) { return false; }
    }
    private static boolean present(String v) { return v != null && !v.isBlank(); }
    public boolean matchesMerchant(String notifiedAppId, String notifiedMchId) { return appId.equals(notifiedAppId) && mchId.equals(notifiedMchId); }

    public String openId(String code) {
        if (!enabled()) throw new ApiError(503, "微信支付配置未完成");
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + enc(appId) + "&secret=" + enc(appSecret)
                    + "&js_code=" + enc(code) + "&grant_type=authorization_code";
            JsonNode response = json.readTree(http().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
            if (response.has("errcode") || !response.hasNonNull("openid")) throw new ApiError(401, "微信身份校验失败，请重试");
            return response.get("openid").asText();
        } catch (ApiError e) { throw e; }
        catch (Exception e) { throw new ApiError(502, "无法校验微信身份，请稍后重试"); }
    }

    public Map<String, String> createMiniProgramOrder(String outTradeNo, String openId, int amountFen, String description) {
        try {
            String body = json.writeValueAsString(Map.of(
                    "appid", appId, "mchid", mchId, "description", description,
                    "out_trade_no", outTradeNo, "notify_url", notifyUrl,
                    "amount", Map.of("total", amountFen, "currency", "CNY"),
                    "payer", Map.of("openid", openId)));
            JsonNode response = request("POST", "/v3/pay/transactions/jsapi", body);
            String prepayId = response.path("prepay_id").asText();
            if (prepayId.isBlank()) throw new IllegalStateException("missing prepay_id");
            String timeStamp = Long.toString(Instant.now().getEpochSecond()), nonce = UUID.randomUUID().toString().replace("-", "");
            String pkg = "prepay_id=" + prepayId;
            String message = appId + "\n" + timeStamp + "\n" + nonce + "\n" + pkg + "\n";
            return Map.of("timeStamp", timeStamp, "nonceStr", nonce, "package", pkg,
                    "signType", "RSA", "paySign", sign(message));
        } catch (ApiError e) { throw e; }
        catch (Exception e) { throw new ApiError(502, "微信支付下单失败，请稍后重试"); }
    }

    private JsonNode request(String method, String path, String body) throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond()), nonce = UUID.randomUUID().toString().replace("-", "");
        String signature = sign(method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n");
        String authorization = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + mchId + "\",nonce_str=\"" + nonce
                + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + mchSerial + "\",signature=\"" + signature + "\"";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mch.weixin.qq.com" + path))
                .timeout(java.time.Duration.ofSeconds(15)).header("Authorization", authorization)
                .header("Accept", "application/json").header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("WeChat Pay API error " + response.statusCode());
        String responseTimestamp = response.headers().firstValue("Wechatpay-Timestamp").orElseThrow();
        String responseNonce = response.headers().firstValue("Wechatpay-Nonce").orElseThrow();
        String responseSerial = response.headers().firstValue("Wechatpay-Serial").orElseThrow();
        String responseSignature = response.headers().firstValue("Wechatpay-Signature").orElseThrow();
        if (!platformSerial.equalsIgnoreCase(responseSerial)
                || Math.abs(Instant.now().getEpochSecond() - Long.parseLong(responseTimestamp)) > 300
                || !verify(readCertificate(platformCertBase64), responseTimestamp + "\n" + responseNonce + "\n" + response.body() + "\n", responseSignature))
            throw new SecurityException("Invalid WeChat Pay API response signature");
        return json.readTree(response.body());
    }

    public JsonNode verifyAndDecrypt(String timestamp, String nonce, String serial, String signature, String body) {
        try {
            if (!platformSerial.equalsIgnoreCase(serial)) throw new SecurityException("unexpected platform certificate serial");
            if (Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) > 300) throw new SecurityException("stale notification");
            PublicKey platformKey = readCertificate(platformCertBase64);
            if (!verify(platformKey, timestamp + "\n" + nonce + "\n" + body + "\n", signature)) throw new SecurityException("invalid signature");
            JsonNode resource = json.readTree(body).path("resource");
            byte[] ciphertext = Base64.getDecoder().decode(resource.path("ciphertext").asText());
            byte[] iv = resource.path("nonce").asText().getBytes(StandardCharsets.UTF_8);
            String aad = resource.path("associated_data").asText("");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return json.readTree(cipher.doFinal(ciphertext));
        } catch (Exception e) { throw new SecurityException("Invalid WeChat Pay notification", e); }
    }

    private String sign(String message) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(readPrivateKey(privateKeyBase64));
        signer.update(message.getBytes(StandardCharsets.UTF_8)); return Base64.getEncoder().encodeToString(signer.sign());
    }
    private static boolean verify(PublicKey key, String message, String signature) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
        verifier.update(message.getBytes(StandardCharsets.UTF_8)); return verifier.verify(Base64.getDecoder().decode(signature));
    }
    private static String pemFromBase64(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8).replaceAll("-----BEGIN ([^-]+)-----|-----END ([^-]+)-----|\\s", "");
    }
    private static PrivateKey readPrivateKey(String value) throws Exception {
        String pem = pemFromBase64(value);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }
    private PublicKey readCertificate(String value) throws Exception {
        String pem = pemFromBase64(value);
        var cert = (java.security.cert.X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(pem)));
        if (!cert.getSerialNumber().toString(16).equalsIgnoreCase(platformSerial)) throw new SecurityException("platform certificate serial mismatch");
        return cert.getPublicKey();
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
