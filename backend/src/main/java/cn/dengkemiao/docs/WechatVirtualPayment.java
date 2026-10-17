package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class WechatVirtualPayment {
    private final ObjectMapper json;
    private HttpClient client;
    private String accessToken="";
    private Instant tokenExpiry=Instant.EPOCH;
    @Value("${WECHAT_APP_ID:}") private String appId;
    @Value("${WECHAT_APP_SECRET:}") private String appSecret;
    @Value("${WECHAT_VIRTUAL_OFFER_ID:}") private String offerId;
    @Value("${WECHAT_VIRTUAL_APP_KEY:}") private String appKey;
    @Value("${WECHAT_VIRTUAL_SANDBOX_APP_KEY:}") private String sandboxKey;
    @Value("${WECHAT_VIRTUAL_ALLOW_SANDBOX:false}") private boolean allowSandbox;
    @Value("${WECHAT_VIRTUAL_ENV:0}") private int environment;
    @Value("${WECHAT_VIRTUAL_MONTHLY_PRODUCT_ID:ai_monthly}") private String monthlyProduct;
    @Value("${WECHAT_VIRTUAL_TOPUP_PRODUCT_ID:ai_topup}") private String topupProduct;
    @Value("${WECHAT_VIRTUAL_NOTIFY_MODE:cloudrun}") private String notifyMode;
    @Value("${WECHAT_VIRTUAL_NOTIFY_TOKEN:}") private String notifyToken;
    @Value("${WECHAT_VIRTUAL_ENCODING_AES_KEY:}") private String encodingKey;
    public WechatVirtualPayment(ObjectMapper json){this.json=json;}
    public boolean loginEnabled(){return present(appId)&&present(appSecret);}
    public boolean enabled(){return loginEnabled()&&present(offerId)&&(environment==0||(environment==1&&allowSandbox))&&present(key())&&present(monthlyProduct)&&present(topupProduct)&&!monthlyProduct.equals(topupProduct)&&(cloudrunEnabled()||("http".equals(notifyMode)&&callbackEnabled()));}
    private boolean validEncodingKey(){try{return encodingKey.length()==43&&Base64.getDecoder().decode(encodingKey+"=").length==32;}catch(Exception e){return false;}}
    public boolean cloudrunEnabled(){return "cloudrun".equals(notifyMode)&&present(appId);}
    public JsonNode cloudrunMessage(String body,String source,String forwardedAppId){
        if(!cloudrunEnabled()||source==null||source.isBlank()||source.length()>256)throw new SecurityException("missing cloudrun source");
        if(forwardedAppId!=null&&!forwardedAppId.isBlank()&&!appId.equals(forwardedAppId))throw new SecurityException("wrong appid");
        try{return WechatPushCrypto.parse(body,json);}catch(Exception e){throw new SecurityException("invalid cloudrun message",e);}
    }
    public boolean callbackEnabled(){return present(appId)&&notifyToken.matches("[A-Za-z0-9]{3,32}")&&validEncodingKey();}
    private static boolean present(String s){return s!=null&&!s.isBlank();}
    public int environment(){return environment;}
    public String offerId(){return offerId;}
    public String product(String packageId){return switch(packageId){case "monthly"->monthlyProduct;case "ai-topup"->topupProduct;default->throw new ApiError(400,"请选择有效套餐");};}
    private String key(){return environment==1?sandboxKey:appKey;}
    public record Session(String openId,String sessionKey){}
    public Session login(String code){
        if(!loginEnabled())throw new ApiError(503,"微信登录配置未完成");
        if(code==null||code.isBlank()||code.length()>256)throw new ApiError(400,"请重新登录微信");
        var result=get("/sns/jscode2session?appid="+enc(appId)+"&secret="+enc(appSecret)+"&js_code="+enc(code)+"&grant_type=authorization_code");
        if(result.path("openid").asText().isBlank()||result.path("session_key").asText().isBlank()){
            int error=result.path("errcode").asInt(-1);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("WeChat login rejected with code {}",error);
            if(error==40013)throw new ApiError(503,"微信AppID配置无效，请检查后端配置（40013）");
            if(error==40001||error==40125)throw new ApiError(503,"微信AppSecret校验失败，请检查后端配置（"+error+"）");
            if(error==40164)throw new ApiError(503,"后端出口IP未获微信允许，请检查IP白名单（40164）");
            if(error==40029||error==40163)throw new ApiError(400,"微信登录凭证已失效，请重新点击购买（"+error+"）");
            throw new ApiError(502,"微信登录校验失败（错误码 "+error+"），请稍后重试");
        }
        return new Session(result.path("openid").asText(),result.path("session_key").asText());
    }
    public Map<String,String> parameters(String tradeNo,String packageId,int amount,Session session){
        if(!enabled())throw new ApiError(503,"虚拟支付配置未完成，暂不收款");
        try {
            var data=new LinkedHashMap<String,Object>();data.put("offerId",offerId);data.put("buyQuantity",1);data.put("env",environment);data.put("currencyType","CNY");data.put("productId",product(packageId));data.put("goodsPrice",amount);data.put("outTradeNo",tradeNo);data.put("attach",tradeNo);
            String signData=json.writeValueAsString(data);
            return Map.of("mode","short_series_goods","signData",signData,"paySig",hmac(key(),"requestVirtualPayment&"+signData),"signature",hmac(session.sessionKey(),signData));
        } catch(Exception e){throw new ApiError(503,"无法准备虚拟支付，请稍后重试");}
    }
    public JsonNode query(String openId,String tradeNo,int env,String offer){
        checkSnapshot(env,offer);
        return post("/xpay/query_order",Map.of("openid",openId,"env",env,"order_id",tradeNo)).path("order");
    }
    public void delivered(String tradeNo,int env,String offer){checkSnapshot(env,offer);post("/xpay/notify_provide_goods",Map.of("order_id",tradeNo,"env",env));}
    private void checkSnapshot(int env,String offer){if(env!=environment||!offerId.equals(offer)||!present(key()))throw new ApiError(503,"支付环境已变更，请联系管理员核对订单");}
    private JsonNode post(String path,Map<String,?> fields){
        try{
            String body=json.writeValueAsString(fields);String signature=hmac(key(),path+"&"+body);
            for(int attempt=0;attempt<2;attempt++){
                String token=token();
                var request=HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com"+path+"?access_token="+enc(token)+"&pay_sig="+signature)).timeout(Duration.ofSeconds(12)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
                JsonNode result=execute(request);
                int error=result.path("errcode").asInt(-1);
                if(attempt==0&&Set.of(40001,40014,42001).contains(error)){invalidate(token);continue;}
                if(error!=0){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Virtual payment {} failed with code {}",path,error);throw new ApiError(502,"微信支付状态暂时无法确认，请稍后刷新");}
                return result;
            }
        }catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(502,"无法连接微信虚拟支付，请稍后重试");}
        throw new ApiError(502,"微信支付状态暂时无法确认");
    }
    private synchronized String token(){
        if(Instant.now().isBefore(tokenExpiry))return accessToken;
        var result=get("/cgi-bin/token?grant_type=client_credential&appid="+enc(appId)+"&secret="+enc(appSecret));
        String value=result.path("access_token").asText();int seconds=result.path("expires_in").asInt();
        if(value.isBlank()||seconds<120)throw new ApiError(503,"无法获取微信接口凭证，请检查AppID、AppSecret和IP白名单");
        accessToken=value;tokenExpiry=Instant.now().plusSeconds(seconds-90);return value;
    }
    private synchronized void invalidate(String old){if(accessToken.equals(old))tokenExpiry=Instant.EPOCH;}
    private JsonNode get(String path){return execute(HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com"+path)).timeout(Duration.ofSeconds(12)).GET().build());}
    private synchronized HttpClient client(){if(client==null)client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();return client;}
    // Keep URLs, response bodies and exception messages out of logs: they may contain credentials.
    JsonNode execute(HttpRequest request){
        String path=request.uri().getPath(),stage=stage(path);
        try {
            var response=send(request);
            if(response.statusCode()!=200){
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("WeChat API {} returned HTTP {}",path,response.statusCode());
                throw new ApiError(502,stage+"请求失败（HTTP "+response.statusCode()+"），请查看后端日志");
            }
            JsonNode body;
            try{body=json.readTree(response.body());if(body==null||!body.isObject())throw new IllegalArgumentException();}
            catch(Exception invalid){org.slf4j.LoggerFactory.getLogger(getClass()).warn("WeChat API {} returned non-JSON data",path);throw new ApiError(502,stage+"返回格式异常，请检查后端访问微信接口的网络配置");}
            return body;
        } catch(ApiError e){throw e;}
        catch(Exception e){
            var types=new ArrayList<String>();Throwable cause=e;for(int i=0;i<6&&cause!=null;i++,cause=cause.getCause())types.add(cause.getClass().getSimpleName());
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("WeChat API {} transport failed [{}]",path,String.join(" -> ",types));
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            String failure=types.stream().anyMatch(x->x.contains("SSL")||x.contains("Certificate"))?"TLS":types.stream().anyMatch(x->x.contains("Timeout"))?"TIMEOUT":types.stream().anyMatch(x->x.contains("UnknownHost")||x.contains("UnresolvedAddress"))?"DNS":"NETWORK";
            throw new ApiError(502,stage+"连接失败（"+failure+"），请查看后端日志");
        }
    }
    HttpResponse<String> send(HttpRequest request)throws java.io.IOException,InterruptedException {return client().send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));}
    private static String stage(String path){return switch(path){case "/sns/jscode2session"->"微信登录接口";case "/cgi-bin/token"->"微信接口凭证服务";default->"微信支付接口";};}
    static String hmac(String key,String message)throws Exception {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    public WechatPushCrypto pushCrypto(){if(!callbackEnabled())throw new ApiError(503,"发货推送校验配置未完成");return new WechatPushCrypto(appId,notifyToken,encodingKey,json);}
}
