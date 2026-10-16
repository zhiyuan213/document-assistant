package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** WeChat safe-mode push authentication. Plaintext POST messages are rejected. */
final class WechatPushCrypto {
    private final String appId,token;private final byte[] key;private final ObjectMapper json;
    WechatPushCrypto(String appId,String token,String encodingKey,ObjectMapper json){this.appId=appId;this.token=token;this.key=Base64.getDecoder().decode(encodingKey+"=");this.json=json;}
    String verifyUrl(String signature,String msgSignature,String timestamp,String nonce,String echo){
        if(echo==null||echo.length()>10000)throw new SecurityException("invalid echo");
        if(msgSignature!=null&&!msgSignature.isBlank()){verify(msgSignature,timestamp,nonce,echo);return decrypt(echo);}
        verify(signature,timestamp,nonce);return echo;
    }
    JsonNode read(String body,String msgSignature,String timestamp,String nonce){
        try{JsonNode envelope=parse(body,json);String encrypted=envelope.path("Encrypt").asText();if(encrypted.isBlank())throw new SecurityException("encrypted push required");verify(msgSignature,timestamp,nonce,encrypted);return parse(decrypt(encrypted),json);}
        catch(SecurityException e){throw e;}catch(Exception e){throw new SecurityException("invalid push",e);}
    }
    private void verify(String signature,String timestamp,String nonce,String...extra){
        try{if(timestamp==null||nonce==null||nonce.length()>256||signature==null||!signature.matches("[a-fA-F0-9]{40}")||Math.abs(Instant.now().getEpochSecond()-Long.parseLong(timestamp))>600)throw new SecurityException("stale push");
            var values=new ArrayList<>(List.of(token,timestamp,nonce));values.addAll(Arrays.asList(extra));Collections.sort(values);
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(String.join("",values).getBytes(StandardCharsets.UTF_8)));
            if(!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))throw new SecurityException("bad signature");
        }catch(SecurityException e){throw e;}catch(Exception e){throw new SecurityException("invalid signature",e);}
    }
    private String decrypt(String encrypted){
        try{byte[] cipherText=Base64.getDecoder().decode(encrypted);if(cipherText.length<32||cipherText.length>200000)throw new SecurityException();
            var cipher=Cipher.getInstance("AES/CBC/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(Arrays.copyOf(key,16)));byte[] padded=cipher.doFinal(cipherText);
            int pad=padded[padded.length-1]&255;if(pad<1||pad>32||pad>padded.length)throw new SecurityException();for(int i=padded.length-pad;i<padded.length;i++)if((padded[i]&255)!=pad)throw new SecurityException();
            int length=ByteBuffer.wrap(padded,16,4).getInt();int end=20+length;if(length<0||end<20||end>padded.length-pad)throw new SecurityException();
            if(!appId.equals(new String(padded,end,padded.length-pad-end,StandardCharsets.UTF_8)))throw new SecurityException("appid mismatch");
            return new String(padded,20,length,StandardCharsets.UTF_8);
        }catch(Exception e){throw new SecurityException("invalid encrypted push",e);}
    }
    static JsonNode parse(String text,ObjectMapper json)throws Exception {
        if(text==null||text.length()>150000)throw new SecurityException("oversized push");
        if(text.stripLeading().startsWith("{"))return json.readTree(text);
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        var document=factory.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));return element(document.getDocumentElement(),0,json);
    }
    private static JsonNode element(Element element,int depth,ObjectMapper json){
        if(depth>8)throw new SecurityException("nested push");ObjectNode node=json.createObjectNode();var children=element.getChildNodes();boolean hasElements=false;
        for(int i=0;i<children.getLength();i++)if(children.item(i) instanceof Element child){hasElements=true;if(node.has(child.getTagName()))throw new SecurityException("duplicate field");node.set(child.getTagName(),element(child,depth+1,json));}
        return hasElements?node:json.getNodeFactory().textNode(element.getTextContent());
    }
}
