package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WechatVirtualPaymentTest {
    final ObjectMapper json=new ObjectMapper();
    static final String AES=Base64.getEncoder().withoutPadding().encodeToString(new byte[32]);
    WechatVirtualPayment service(){var s=new WechatVirtualPayment(json);for(var e:Map.of("appId","wx-test","appSecret","secret-test","offerId","12345","appKey","live-key","sandboxKey","sandbox-key","monthlyProduct","ai_monthly","topupProduct","ai_topup","notifyToken","testToken123","encodingKey",AES).entrySet())ReflectionTestUtils.setField(s,e.getKey(),e.getValue());ReflectionTestUtils.setField(s,"environment",0);ReflectionTestUtils.setField(s,"notifyMode","cloudrun");return s;}
    @Test void signsExactSignDataAndSelectsEnvironmentKey()throws Exception {
        var s=service();assertTrue(s.enabled());
        var pay=s.parameters("DV123","monthly",990,new WechatVirtualPayment.Session("openid","session-secret"));
        var data=json.readTree(pay.get("signData"));assertEquals("ai_monthly",data.path("productId").asText());assertEquals(990,data.path("goodsPrice").asInt());assertEquals(0,data.path("env").asInt());assertEquals(1,data.path("buyQuantity").asInt());assertFalse(pay.containsKey("sessionKey"));
        assertEquals(hmac("live-key","requestVirtualPayment&"+pay.get("signData")),pay.get("paySig"));assertEquals(hmac("session-secret",pay.get("signData")),pay.get("signature"));
        ReflectionTestUtils.setField(s,"environment",1);assertFalse(s.enabled());ReflectionTestUtils.setField(s,"allowSandbox",true);var sandbox=s.parameters("DV124","ai-topup",990,new WechatVirtualPayment.Session("openid","session-secret"));assertEquals(hmac("sandbox-key","requestVirtualPayment&"+sandbox.get("signData")),sandbox.get("paySig"));
        ReflectionTestUtils.setField(s,"environment",2);assertFalse(s.enabled());
    }
    @Test void queryUsesSignedBodyAndRetriesExpiredAccessToken()throws Exception {
        var s=spy(service());var requests=new ArrayList<HttpRequest>();
        doAnswer(inv->{HttpRequest req=inv.getArgument(0);requests.add(req);if(req.uri().getPath().contains("token"))return json.readTree("{\"access_token\":\"access-"+requests.size()+"\",\"expires_in\":7200}");if(requests.size()==2)return json.readTree("{\"errcode\":40001}");return json.readTree("{\"errcode\":0,\"order\":{\"order_id\":\"DV123\",\"status\":2}}");}).when(s).execute(any());
        var result=s.query("openid","DV123",0,"12345");assertEquals(2,result.path("status").asInt());assertEquals(4,requests.size());
        String body=json.writeValueAsString(Map.of("openid","openid","env",0,"order_id","DV123"));
        assertTrue(requests.get(3).uri().getQuery().contains("pay_sig="+hmac("live-key","/xpay/query_order&"+body)));assertThrows(ApiError.class,()->s.query("openid","DV123",1,"12345"));
    }
    @Test void cloudrunPushRequiresSourceAndMatchingApp()throws Exception {
        var s=service();assertTrue(s.enabled());ReflectionTestUtils.setField(s,"notifyToken","");ReflectionTestUtils.setField(s,"encodingKey","");assertTrue(s.enabled());
        assertEquals("event",s.cloudrunMessage("{\"MsgType\":\"event\"}","wx", "wx-test").path("MsgType").asText());
        assertThrows(SecurityException.class,()->s.cloudrunMessage("{}",null,"wx-test"));
        assertThrows(SecurityException.class,()->s.cloudrunMessage("{}","wx","wrong-app"));
        ReflectionTestUtils.setField(s,"notifyMode","http");assertFalse(s.enabled());assertThrows(SecurityException.class,()->s.cloudrunMessage("{}","wx","wx-test"));
    }
    @Test void safePushRejectsTamperingReplayWrongAppAndPlaintext()throws Exception {
        var crypto=service().pushCrypto();String timestamp=""+Instant.now().getEpochSecond(),nonce="nonce1";
        String message="{\"Event\":\"xpay_goods_deliver_notify\",\"OpenId\":\"user1\"}",encrypted=encrypt(message,"wx-test");String signature=signature(timestamp,nonce,encrypted);
        assertEquals("user1",crypto.read(json.writeValueAsString(Map.of("Encrypt",encrypted)),signature,timestamp,nonce).path("OpenId").asText());
        String xml="<xml><Encrypt><![CDATA["+encrypted+"]]></Encrypt></xml>";assertEquals("xpay_goods_deliver_notify",crypto.read(xml,signature,timestamp,nonce).path("Event").asText());
        assertThrows(SecurityException.class,()->crypto.read(message,signature,timestamp,nonce));
        assertThrows(SecurityException.class,()->crypto.read(xml,"0".repeat(40),timestamp,nonce));
        String wrong=encrypt(message,"other-app");assertThrows(SecurityException.class,()->crypto.read(json.writeValueAsString(Map.of("Encrypt",wrong)),signature(timestamp,nonce,wrong),timestamp,nonce));
        String stale=""+(Instant.now().getEpochSecond()-3600);assertThrows(SecurityException.class,()->crypto.read(xml,signature(stale,nonce,encrypted),stale,nonce));
        String echo="echo-test";assertEquals(echo,crypto.verifyUrl(signature(timestamp,nonce),null,timestamp,nonce,echo));
        String encryptedEcho=encrypt(echo,"wx-test");assertEquals(echo,crypto.verifyUrl(null,signature(timestamp,nonce,encryptedEcho),timestamp,nonce,encryptedEcho));
    }
    @Test void encryptedXmlPayloadDisablesExternalEntities()throws Exception {
        var crypto=service().pushCrypto();String timestamp=""+Instant.now().getEpochSecond(),nonce="nonce1";
        String x=encrypt("<!DOCTYPE xml [<!ENTITY xxe SYSTEM 'file:///never-read'>]><xml><Event>&xxe;</Event></xml>","wx-test");assertThrows(SecurityException.class,()->crypto.read(json.writeValueAsString(Map.of("Encrypt",x)),signature(timestamp,nonce,x),timestamp,nonce));
    }
    static String signature(String timestamp,String nonce,String...extra)throws Exception{var parts=new ArrayList<>(List.of("testToken123",timestamp,nonce));parts.addAll(Arrays.asList(extra));Collections.sort(parts);return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(String.join("",parts).getBytes(StandardCharsets.UTF_8)));}
    static String encrypt(String message,String appId)throws Exception {
        byte[] text=message.getBytes(StandardCharsets.UTF_8),app=appId.getBytes(StandardCharsets.UTF_8);var buffer=ByteBuffer.allocate(20+text.length+app.length);buffer.put(new byte[16]).putInt(text.length).put(text).put(app);int pad=32-buffer.capacity()%32;byte[] plain=Arrays.copyOf(buffer.array(),buffer.capacity()+pad);Arrays.fill(plain,buffer.capacity(),plain.length,(byte)pad);
        var cipher=Cipher.getInstance("AES/CBC/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(new byte[32],"AES"),new IvParameterSpec(new byte[16]));return Base64.getEncoder().encodeToString(cipher.doFinal(plain));
    }
    static String hmac(String key,String body)throws Exception{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));}
}
