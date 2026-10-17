package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class PdfVirtualPayments {
    private final JdbcTemplate db;private final TransactionTemplate tx;private final WechatVirtualPayment wechat;private final PdfAiCredits credits;
    public PdfVirtualPayments(JdbcTemplate db,PlatformTransactionManager manager,WechatVirtualPayment wechat,PdfAiCredits credits){this.db=db;this.tx=new TransactionTemplate(manager);this.wechat=wechat;this.credits=credits;}
    public boolean loginEnabled(){return wechat.loginEnabled();}
    public boolean enabled(){return wechat.enabled();}
    public String bind(String visitor,String code){return bindSession(visitor,wechat.login(code));}
    private String bindSession(String visitor,WechatVirtualPayment.Session session){
        if(db.update("UPDATE pdf_visitor SET wechat_openid=? WHERE id=? AND (wechat_openid IS NULL OR wechat_openid=?)",session.openId(),visitor,session.openId())!=1)throw new ApiError(401,"微信账号已变更，请重新进入小程序");
        return credits.owner(visitor);
    }
    public Map<String,Object> purchase(String visitor,String packageId,String code){
        if(!Set.of("monthly","ai-topup").contains(packageId))throw new ApiError(400,"请选择有效套餐");
        if(!enabled())throw new ApiError(503,"虚拟支付配置未完成，暂不收款");
        var session=wechat.login(code);String owner=bindSession(visitor,session);String id=UUID.randomUUID().toString();String trade="DV"+UUID.randomUUID().toString().replace("-","").substring(0,30);
        int amount=990;var pay=wechat.parameters(trade,packageId,amount,session);
        tx.executeWithoutResult(s->{
            db.queryForList("SELECT id FROM pdf_visitor WHERE id=? FOR UPDATE",owner);
            if(packageId.equals("monthly"))credits.requireMonthlyPurchaseAllowed(owner);
            Integer pending=db.queryForObject("SELECT COUNT(*) FROM pdf_purchase WHERE visitor_id=? AND status='pending' AND created_at>?",Integer.class,owner,Timestamp.valueOf(now().minusMinutes(30)));
            if(pending!=null&&pending>=10)throw new ApiError(429,"未完成订单较多，请稍后再试");
            db.update("INSERT INTO pdf_purchase(id,visitor_id,package_id,amount_fen,credits,status,out_trade_no) VALUES (?,?,?,?,?,'pending',?)",id,owner,packageId,amount,packageId.equals("ai-topup")?PdfAiCredits.TOPUP_CREDITS:1,trade);
            db.update("INSERT INTO pdf_virtual_order(purchase_id,openid,offer_id,product_id,environment,next_check_at) VALUES (?,?,?,?,?,?)",id,session.openId(),wechat.offerId(),wechat.product(packageId),wechat.environment(),Timestamp.valueOf(now().plusSeconds(3)));
        });
        var result=new HashMap<String,Object>(pay);result.put("orderId",id);result.put("paymentProvider","virtual");return result;
    }
    public boolean isVirtual(String id){return !db.queryForList("SELECT purchase_id FROM pdf_virtual_order WHERE purchase_id=?",id).isEmpty();}
    public void refresh(String id){
        // Claim briefly before I/O: concurrent callbacks, polls and replicas do not query repeatedly.
        if(db.update("UPDATE pdf_virtual_order SET next_check_at=? WHERE purchase_id=? AND next_check_at<=?",Timestamp.valueOf(now().plusSeconds(12)),id,Timestamp.valueOf(now()))==0)return;
        try {reconcile(id);}
        catch(Exception e){db.update("UPDATE pdf_virtual_order SET last_error='query_deferred',next_check_at=? WHERE purchase_id=?",Timestamp.valueOf(now().plusSeconds(30)),id);org.slf4j.LoggerFactory.getLogger(getClass()).warn("Virtual order reconciliation deferred for {} ({})",id,e.getClass().getSimpleName());}
    }
    private Map<String,Object> read(String id){var list=db.queryForList("SELECT p.*,v.openid,v.offer_id,v.product_id,v.environment,v.refunded_fen,v.member_start,v.member_end,v.delivered_confirmed FROM pdf_purchase p JOIN pdf_virtual_order v ON p.id=v.purchase_id WHERE p.id=?",id);if(list.isEmpty())throw new SecurityException("unknown order");return list.get(0);}
    private void reconcile(String id){
        var stored=read(id);String trade=stored.get("out_trade_no").toString();int env=number(stored,"environment");String offer=stored.get("offer_id").toString();
        JsonNode order=wechat.query(stored.get("openid").toString(),trade,env,offer);
        if(!trade.equals(order.path("order_id").asText())||order.path("order_fee").asInt(-1)!=number(stored,"amount_fen"))throw new SecurityException("order mismatch");
        if(order.has("env_type")&&order.path("env_type").asInt(-1)!=(env==0?1:2))throw new SecurityException("environment mismatch");
        if(order.has("openid")&&!stored.get("openid").equals(order.path("openid").asText()))throw new SecurityException("identity mismatch");
        int type=order.path("order_type").asInt(-1);if(type!=0&&type!=7)throw new SecurityException("not a payment order");
        int state=order.path("status").asInt(-1),amount=number(stored,"amount_fen");
        String platform=order.path("wx_order_id").asText();
        if(Set.of(2,3,4,5,8).contains(state)){
            if(platform.isBlank()||platform.length()>64)throw new SecurityException("missing platform order id");
            int refunded=0;
            if(order.has("left_fee")){int left=order.path("left_fee").asInt(-1);if(left<0||left>amount)throw new SecurityException("refund amount mismatch");refunded=amount-left;}
            else if(state==5||state==8)throw new SecurityException("missing refund amount");
            if((state==5||state==8)&&refunded==0)throw new SecurityException("refund not confirmed");
            int refund=refunded;
            tx.executeWithoutResult(s->{
                String owner=stored.get("visitor_id").toString();db.queryForList("SELECT id FROM pdf_visitor WHERE id=? FOR UPDATE",owner);db.queryForList("SELECT id FROM pdf_purchase WHERE id=? FOR UPDATE",id);var current=read(id);
                String status=current.get("status").toString();String existing=(String)current.get("transaction_id");
                if(existing!=null&&!existing.equals(platform))throw new SecurityException("transaction mismatch");
                if("pending".equals(status)){
                    if(refund<amount)grant(current);
                    db.update("UPDATE pdf_purchase SET status=?,transaction_id=?,paid_at=? WHERE id=?",refund==amount?"refunded":"paid",platform,Timestamp.valueOf(now()),id);
                    // Fully refunded before delivery: never grant and never create a negative balance.
                    if(refund==amount)db.update("UPDATE pdf_virtual_order SET refunded_fen=? WHERE purchase_id=?",amount,id);
                }else if(!Set.of("paid","refunded").contains(status))throw new SecurityException("invalid order status");
                current=read(id);int previous=number(current,"refunded_fen");
                if(refund>previous){revoke(current,refund-previous);db.update("UPDATE pdf_virtual_order SET refunded_fen=? WHERE purchase_id=?",refund,id);if(refund==amount)db.update("UPDATE pdf_purchase SET status='refunded' WHERE id=?",id);}
                db.update("UPDATE pdf_virtual_order SET last_error=NULL,next_check_at=? WHERE purchase_id=?",Timestamp.valueOf(now().plusHours(24)),id);
            });
            // A successful callback acknowledgement or this API lets WeChat stop delivery retries.
            if(refunded<amount&&state!=4){wechat.delivered(trade,env,offer);}
            db.update("UPDATE pdf_virtual_order SET delivered_confirmed=TRUE WHERE purchase_id=?",id);
        }else if(state==6){db.update("UPDATE pdf_purchase SET status='closed' WHERE id=? AND status='pending'",id);db.update("UPDATE pdf_virtual_order SET next_check_at=?,last_error=NULL WHERE purchase_id=?",Timestamp.valueOf(now().plusYears(10)),id);}
        else if(state==0||state==1||state==7){db.update("UPDATE pdf_virtual_order SET next_check_at=?,last_error=NULL WHERE purchase_id=?",Timestamp.valueOf(now().plusMinutes(JdbcDates.local(stored.get("created_at")).isBefore(now().minusHours(1))?60:5)),id);}
        else throw new SecurityException("unrecognized payment status");
    }
    private void grant(Map<String,Object> order){
        String owner=order.get("visitor_id").toString(),id=order.get("id").toString();
        if("ai-topup".equals(order.get("package_id"))){credits.grantTopup(owner,PdfAiCredits.topupGrant(((Number)order.get("credits")).longValue()));return;}
        if(!"monthly".equals(order.get("package_id")))throw new SecurityException("unknown package");
        var members=db.queryForList("SELECT expires_at FROM pdf_membership WHERE visitor_id=?",owner);LocalDateTime start=now();
        if(!members.isEmpty()){var previous=JdbcDates.local(members.get(0).get("expires_at"));if(previous.isAfter(start))start=previous;}
        boolean renewing=start.isAfter(now());if(renewing)credits.snapshot(owner);LocalDateTime end=start.plusMonths(1);
        db.update("INSERT INTO pdf_membership(visitor_id,expires_at) VALUES (?,?) ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)",owner,Timestamp.valueOf(end));credits.activateMonth(owner,end,renewing);
        db.update("UPDATE pdf_virtual_order SET member_start=?,member_end=? WHERE purchase_id=?",Timestamp.valueOf(start),Timestamp.valueOf(end),id);
    }
    private void revoke(Map<String,Object> order,int difference){
        String owner=order.get("visitor_id").toString();int amount=number(order,"amount_fen");credits.snapshot(owner);
        if("ai-topup".equals(order.get("package_id"))){long previous=number(order,"refunded_fen");long grant=PdfAiCredits.topupGrant(((Number)order.get("credits")).longValue());long delta=grant*(previous+difference)/amount-grant*previous/amount;db.update("UPDATE pdf_ai_wallet SET topup_balance=topup_balance-? WHERE visitor_id=?",delta,owner);return;}
        if(order.get("member_start")==null||order.get("member_end")==null)return;
        LocalDateTime start=JdbcDates.local(order.get("member_start")),end=JdbcDates.local(order.get("member_end")),now=now();
        // Revoke only the not-yet-consumed portion; later valid renewals remain valid.
        long available=Math.max(0,Duration.between(start.isAfter(now)?start:now,end).getSeconds());
        int outstanding=amount-number(order,"refunded_fen");long seconds=available*difference/outstanding;
        if(seconds==0)return;
        var rows=db.queryForList("SELECT expires_at FROM pdf_membership WHERE visitor_id=?",owner);if(rows.isEmpty())return;
        LocalDateTime expiry=JdbcDates.local(rows.get(0).get("expires_at")).minusSeconds(seconds);if(expiry.isBefore(now))expiry=now;
        db.update("UPDATE pdf_membership SET expires_at=? WHERE visitor_id=?",Timestamp.valueOf(expiry),owner);
        db.update("UPDATE pdf_ai_wallet SET period_end=CASE WHEN period_end>? THEN ? ELSE period_end END WHERE visitor_id=?",Timestamp.valueOf(expiry),Timestamp.valueOf(expiry),owner);
        var future=db.queryForList("SELECT v.purchase_id,v.member_start,v.member_end FROM pdf_virtual_order v JOIN pdf_purchase p ON p.id=v.purchase_id WHERE p.visitor_id=? AND v.member_start>=? AND v.purchase_id<>?",owner,Timestamp.valueOf(end),order.get("id"));
        for(var next:future)db.update("UPDATE pdf_virtual_order SET member_start=?,member_end=? WHERE purchase_id=?",Timestamp.valueOf(JdbcDates.local(next.get("member_start")).minusSeconds(seconds)),Timestamp.valueOf(JdbcDates.local(next.get("member_end")).minusSeconds(seconds)),next.get("purchase_id"));
        db.update("UPDATE pdf_virtual_order SET member_end=? WHERE purchase_id=?",Timestamp.valueOf(end.minusSeconds(seconds)),order.get("id"));
    }
    public void notification(JsonNode event){
        String type=event.path("Event").asText();if(!Set.of("xpay_goods_deliver_notify","xpay_refund_notify").contains(type))return;
        String trade=event.path(type.equals("xpay_refund_notify")?"MchOrderId":"OutTradeNo").asText();
        var rows=db.queryForList("SELECT p.id,v.openid,v.environment,v.product_id FROM pdf_purchase p JOIN pdf_virtual_order v ON p.id=v.purchase_id WHERE p.out_trade_no=?",trade);
        if(rows.isEmpty())throw new SecurityException("unknown order");var row=rows.get(0);
        if(!row.get("openid").equals(event.path("OpenId").asText())||number(row,"environment")!=event.path("Env").asInt(-1))throw new SecurityException("notification mismatch");
        if(type.equals("xpay_goods_deliver_notify")){
            var goods=event.path("GoodsInfo");if(!row.get("product_id").equals(goods.path("ProductId").asText())||goods.path("Quantity").asInt(-1)!=1||goods.path("OrigPrice").asInt(-1)!=990)throw new SecurityException("product mismatch");
        }
        String id=row.get("id").toString();
        if(type.equals("xpay_refund_notify") && event.path("RetCode").asInt(-1)!=0)return;
        reconcile(id); // Never trust event amount or front-end success alone.
        var checked=read(id);
        if(type.equals("xpay_goods_deliver_notify")&&"pending".equals(checked.get("status")))throw new SecurityException("payment not yet confirmed");
        if(type.equals("xpay_refund_notify")&&(event.path("RefundFee").asInt(-1)<1||number(checked,"refunded_fen")<event.path("RefundFee").asInt()))throw new SecurityException("refund not yet confirmed");
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000) public void recover(){
        if(!enabled())return;
        try{var rows=db.queryForList("SELECT p.id FROM pdf_purchase p JOIN pdf_virtual_order v ON p.id=v.purchase_id WHERE p.status IN ('pending','paid') AND p.created_at>? AND v.next_check_at<=? ORDER BY v.next_check_at LIMIT 10",Timestamp.valueOf(now().minusDays(365)),Timestamp.valueOf(now()));for(var row:rows)refresh(row.get("id").toString());}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Virtual payment recovery deferred ({})",e.getClass().getSimpleName());}
    }
    static int number(Map<String,Object> row,String key){return ((Number)row.get(key)).intValue();}
    private static LocalDateTime now(){return PdfAiCredits.now().withNano(0);}
}
