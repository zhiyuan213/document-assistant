package cn.dengkemiao.docs;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Conservative removal of isolated graphics blocks, never image inpainting. */
public final class PdfWatermarks {
    private static final byte[] KEY=new byte[32];
    static {new java.security.SecureRandom().nextBytes(KEY);}
    record Candidate(String id,int page,int start,int end,int annotation,String kind) {}
    record Scan(List<List<Object>> tokens,List<Candidate> candidates) {}
    static PDDocument load(byte[] bytes) throws IOException {
        var doc=Loader.loadPDF(bytes);
        if(doc.isEncrypted()||doc.getNumberOfPages()<1||doc.getNumberOfPages()>30){doc.close();throw new ApiError(400,"去水印支持未加密的1至30页PDF，请先解密或拆分");}
        return doc;
    }
    static Scan scan(PDDocument doc) throws IOException {
        var pages=new ArrayList<List<Object>>();var candidates=new ArrayList<Candidate>();
        for(int p=0;p<doc.getNumberOfPages();p++) {
            var page=doc.getPage(p);var tokens=new PDFStreamParser(page).parse();pages.add(tokens);
            if(tokens.size()>200000)throw new ApiError(400,"页面对象过多，请先拆分PDF");
            var stack=new ArrayDeque<Integer>();boolean text=false;
            for(int i=0;i<tokens.size();i++)if(tokens.get(i) instanceof Operator op){
                String name=op.getName();if(name.equals("BT"))text=true;if(name.equals("ET"))text=false;
                if(name.equals("q"))stack.push(text?-1:i);
                if(name.equals("Q")&&!stack.isEmpty()){
                    int start=stack.pop();if(start<0)continue;
                    String kind=classify(tokens,start,i,page);
                    if(kind!=null){int pageIndex=p;int end=i;candidates.removeIf(c->c.page==pageIndex&&c.annotation<0&&c.start>=start&&c.end<=end);candidates.add(new Candidate("p"+p+"b"+start,p,start,i,-1,kind));}
                }
            }
            var annotations=page.getAnnotations();
            for(int a=0;a<annotations.size();a++)if("Watermark".equals(annotations.get(a).getSubtype()))candidates.add(new Candidate("p"+p+"a"+a,p,-1,-1,a,"明确标记的水印注释"));
        }
        if(candidates.size()>200)throw new ApiError(400,"独立对象较多，请拆分后处理");
        return new Scan(pages,candidates);
    }
    private static String classify(List<Object> t,int start,int end,PDPage page)throws IOException {
        float alpha=1;var states=new ArrayDeque<Float>();boolean painted=false,hasText=false,hasImage=false;int textDepth=0;
        for(int i=start;i<=end;i++)if(t.get(i) instanceof Operator op){String n=op.getName();
            switch(n){
                case "q" -> states.push(alpha);
                case "Q" -> {if(states.isEmpty())return null;alpha=states.pop();}
                case "gs" -> {if(i<1||!(t.get(i-1) instanceof COSName key)||page.getResources()==null)return null;var gs=page.getResources().getExtGState(key);if(gs==null)return null;if(gs.getNonStrokingAlphaConstant()!=null)alpha=gs.getNonStrokingAlphaConstant();}
                case "BT" -> textDepth++;
                case "ET" -> textDepth--;
                case "Tr" -> {if(i<1||!(t.get(i-1) instanceof COSNumber number)||number.intValue()!=0)return null;}
                case "Tj","TJ","'","\"" -> {if(alpha<=0||alpha>=0.95f)return null;painted=true;hasText=true;}
                case "Do" -> {if(alpha<=0||alpha>=0.95f||i<1||!(t.get(i-1) instanceof COSName key)||page.getResources()==null||!(page.getResources().getXObject(key) instanceof PDImageXObject))return null;painted=true;hasImage=true;}
                case "S","s","f","F","f*","B","B*","b","b*","sh","BI","W","W*" -> {return null;}
                default -> { }
            }
            if(textDepth<0)return null;
        }
        return painted&&textDepth==0&&states.isEmpty()?(hasText&&hasImage?"独立半透明文字及图片":hasText?"独立半透明文字":"独立半透明图片"):null;
    }
    public static Map<String,Object> inspect(byte[] input)throws Exception {
        try(var doc=load(input)){var scan=scan(doc);return Map.of("candidates",scan.candidates.stream().map(c->Map.of("id",c.id,"page",c.page+1,"kind",c.kind)).toList(),"message",scan.candidates.isEmpty()?"未找到可独立分离的水印。扫描件融合水印、嵌套复杂对象暂不支持。":"候选对象也可能是正文或装饰，请逐项预览确认。","pages",doc.getNumberOfPages());}
    }
    static List<Candidate> select(Scan scan,String ids){
        var keys=new LinkedHashSet<>(Arrays.asList(ids.split(",")));var selected=scan.candidates.stream().filter(c->keys.contains(c.id)).toList();
        if(keys.isEmpty()||selected.size()!=keys.size()||selected.isEmpty())throw new ApiError(400,"请选择有效的水印候选对象");
        if(selected.stream().map(Candidate::page).distinct().count()>5)throw new ApiError(400,"每次最多预览处理5页，请分批选择");return selected;
    }
    static void apply(PDDocument doc,Scan scan,List<Candidate> selected)throws IOException {
        for(int p=0;p<doc.getNumberOfPages();p++){
            int index=p;var items=selected.stream().filter(c->c.page==index).toList();if(items.isEmpty())continue;
            var tokens=scan.tokens.get(p);var kept=new ArrayList<Object>();
            for(int i=0;i<tokens.size();i++){int position=i;if(items.stream().noneMatch(c->c.annotation<0&&position>=c.start&&position<=c.end))kept.add(tokens.get(i));}
            var stream=new PDStream(doc);try(var out=stream.createOutputStream(COSName.FLATE_DECODE)){new ContentStreamWriter(out).writeTokens(kept);}doc.getPage(p).setContents(stream);
            var annotations=doc.getPage(p).getAnnotations();var remaining=new ArrayList<org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation>();
            for(int a=0;a<annotations.size();a++){int ai=a;if(items.stream().noneMatch(c->c.annotation==ai))remaining.add(annotations.get(a));}doc.getPage(p).setAnnotations(remaining);
        }
    }
    private static String image(PDDocument doc,int page)throws IOException {
        var box=doc.getPage(page).getCropBox();float longest=Math.max(box.getWidth(),box.getHeight());if(!Float.isFinite(longest)||longest<=0)throw new ApiError(400,"页面尺寸无效");
        var renderer=new PDFRenderer(doc);renderer.setSubsamplingAllowed(true);var image=renderer.renderImage(page,Math.min(1f,900f/longest));
        try(var bytes=new ByteArrayOutputStream()){ImageIO.write(image,"png",bytes);return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());}finally{image.flush();}
    }
    public static Map<String,Object> preview(byte[] input,String ids)throws Exception {
        try(var doc=load(input)){var scan=scan(doc);var selected=select(scan,ids);var pages=selected.stream().map(Candidate::page).distinct().sorted().toList();var before=new ArrayList<String>();for(int p:pages)before.add(image(doc,p));apply(doc,scan,selected);var previews=new ArrayList<Map<String,Object>>();for(int i=0;i<pages.size();i++)previews.add(Map.of("page",pages.get(i)+1,"before",before.get(i),"after",image(doc,pages.get(i))));long expires=System.currentTimeMillis()+10*60*1000;return Map.of("previews",previews,"proof",expires+":"+signature(input,ids,expires));}
    }
    private static String signature(byte[] input,String ids,long expiry)throws Exception {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(KEY,"HmacSHA256"));mac.update(MessageDigest.getInstance("SHA-256").digest(input));return HexFormat.of().formatHex(mac.doFinal((ids+":"+expiry).getBytes(StandardCharsets.UTF_8)));}
    public static byte[] remove(byte[] input,String ids,String proof)throws Exception {
        try{var parts=proof.split(":");long expiry=Long.parseLong(parts[0]);if(parts.length!=2||expiry<System.currentTimeMillis()||!MessageDigest.isEqual(signature(input,ids,expiry).getBytes(StandardCharsets.UTF_8),parts[1].getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException();}catch(Exception e){throw new ApiError(400,"预览已失效，请重新预览并确认");}
        try(var doc=load(input);var out=new ByteArrayOutputStream()){var scan=scan(doc);apply(doc,scan,select(scan,ids));doc.save(out);return out.toByteArray();}
    }
}
