package cn.dengkemiao.docs;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/pdf/virtual")
public class PdfVirtualNotifyController {
    private final WechatVirtualPayment wechat;private final PdfVirtualPayments payments;
    public PdfVirtualNotifyController(WechatVirtualPayment wechat,PdfVirtualPayments payments){this.wechat=wechat;this.payments=payments;}
    @GetMapping("/notify") public ResponseEntity<String> verify(
        @RequestParam(required=false)String signature,@RequestParam(name="msg_signature",required=false)String msgSignature,
        @RequestParam String timestamp,@RequestParam String nonce,@RequestParam String echostr){
        try{return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(wechat.pushCrypto().verifyUrl(signature,msgSignature,timestamp,nonce,echostr));}
        catch(Exception e){return ResponseEntity.status(403).body("verification failed");}
    }
    @PostMapping("/notify") public ResponseEntity<String> receive(
        @RequestParam(name="msg_signature",required=false)String msgSignature,@RequestParam(required=false)String timestamp,
        @RequestParam(required=false)String nonce,@RequestBody String body){
        try {
            var event=wechat.pushCrypto().read(body,msgSignature,timestamp,nonce);
            if("xpay_subscribe_ios_refund_query_notify".equals(event.path("Event").asText()))return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{\"result_code\":0,\"result_info\":\"同意退款\",\"evidence\":\"按平台退款流程处理，到账后回收未使用权益\"}");
            payments.notification(event);
            boolean xml=body.stripLeading().startsWith("<");return ResponseEntity.ok().contentType(xml?MediaType.APPLICATION_XML:MediaType.APPLICATION_JSON).body(xml?"<xml><ErrCode>0</ErrCode><ErrMsg><![CDATA[success]]></ErrMsg></xml>":"{\"ErrCode\":0,\"ErrMsg\":\"success\"}");
        } catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Virtual payment notification deferred ({})",e.getClass().getSimpleName());return ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON).body("{\"ErrCode\":1,\"ErrMsg\":\"retry later\"}");}
    }
    @PostMapping("/cloudrun-notify") public ResponseEntity<String> cloudrun(
        @RequestHeader(value="x-wx-source",required=false)String source,
        @RequestHeader(value="x-wx-sources",required=false)String sources,
        @RequestHeader(value="x-wx-appid",required=false)String forwardedAppId,
        @RequestBody String body){
        try {
            // Path-validation probes are harmless and do not perform any business operation.
            var probe=WechatPushCrypto.parse(body,new com.fasterxml.jackson.databind.ObjectMapper());
            if("CheckContainerPath".equals(probe.path("action").asText()))return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("success");
            var event=wechat.cloudrunMessage(body,source!=null&&!source.isBlank()?source:sources,forwardedAppId);
            if("xpay_subscribe_ios_refund_query_notify".equals(event.path("Event").asText()))return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{\"result_code\":0,\"result_info\":\"同意退款\",\"evidence\":\"按平台退款流程处理\"}");
            payments.notification(event);
            boolean xml=body.stripLeading().startsWith("<");return ResponseEntity.ok().contentType(xml?MediaType.APPLICATION_XML:MediaType.APPLICATION_JSON).body(xml?"<xml><ErrCode>0</ErrCode><ErrMsg><![CDATA[success]]></ErrMsg></xml>":"{\"ErrCode\":0,\"ErrMsg\":\"success\"}");
        }catch(SecurityException e){return ResponseEntity.status(403).body("forbidden");}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Cloudrun payment notification deferred ({})",e.getClass().getSimpleName());return ResponseEntity.status(500).body("retry later");}
    }

}
