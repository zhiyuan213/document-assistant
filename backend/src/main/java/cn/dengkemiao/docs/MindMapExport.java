package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.*;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.http.*;

/** Bounded tree data only: no HTML, script, external images or model-supplied paths. */
final class MindMapExport {
    static final String PROMPT="请将以下文档整理为简体中文脑图，只输出JSON对象，不要Markdown。格式为 {\"title\":\"中心主题\",\"children\":[{\"title\":\"分支主题\",\"children\":[{\"title\":\"要点 [A-P1]\"}]}]}。概括核心内容、关键数据和关系，事实引用提供的来源页码，不编造。总节点不超过30个、最多5层（含中心）、每个标题不超过40字。中心必须有分支，不要空节点。\n\n";
    record Node(String title,List<Node> children){}
    static Node parse(String content)throws Exception {
        if(content==null||content.length()>40000)throw new ApiError(400,"脑图内容过大");
        JsonNode value;
        try{value=new ObjectMapper().readTree(content);}catch(Exception e){throw new ApiError(502,"脑图结构不完整，请重新生成");}
        var root=read(value,0,new int[]{0});
        if(root.children.isEmpty())throw new ApiError(502,"脑图缺少分支，请重新生成");
        return root;
    }
    private static Node read(JsonNode value,int depth,int[] count){
        if(value==null||!value.isObject()||depth>4||++count[0]>60||!value.path("title").isTextual())throw new ApiError(502,"脑图结构异常，请重新生成");
        String title=value.path("title").asText().strip();
        if(title.isEmpty()||title.codePointCount(0,title.length())>100||title.codePoints().anyMatch(Character::isISOControl))throw new ApiError(502,"脑图节点文字异常，请重新生成");
        var children=new ArrayList<Node>();var items=value.get("children");
        if(items!=null&&!items.isNull()){if(!items.isArray())throw new ApiError(502,"脑图分支格式异常，请重新生成");for(var item:items)children.add(read(item,depth+1,count));}
        return new Node(title,List.copyOf(children));
    }
    static ResponseEntity<byte[]> export(String content,String format)throws Exception {
        if(!Set.of("png","pdf","xmind").contains(format))throw new ApiError(400,"请选择PNG、PDF或XMind格式");
        var root=parse(content);byte[] bytes;String type;
        if(format.equals("xmind")){bytes=xmind(root);type="application/octet-stream";}
        else {
            var image=image(root);
            try(var out=new ByteArrayOutputStream()){
                if(format.equals("png")){ImageIO.write(image,"png",out);type="image/png";}
                else {
                    try(var doc=new PDDocument()){
                        var page=new PDPage(new PDRectangle(image.getWidth()*.5f,image.getHeight()*.5f));doc.addPage(page);
                        try(var stream=new PDPageContentStream(doc,page)){stream.drawImage(LosslessFactory.createFromImage(doc,image),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}
                        doc.save(out);
                    }
                    type="application/pdf";
                }
                bytes=out.toByteArray();
            }finally{image.flush();}
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header("Content-Disposition","attachment; filename=\"AI-mindmap."+format+"\"").body(bytes);
    }
    private static Map<String,Object> topic(Node node){
        var result=new LinkedHashMap<String,Object>();result.put("id",UUID.randomUUID().toString());result.put("class","topic");result.put("title",node.title);
        if(!node.children.isEmpty())result.put("children",Map.of("attached",node.children.stream().map(MindMapExport::topic).toList()));
        return result;
    }
    static byte[] xmind(Node root)throws Exception {
        var json=new ObjectMapper();var center=topic(root);center.put("structureClass","org.xmind.ui.logic.right");
        var files=new LinkedHashMap<String,Object>();
        files.put("content.json",List.of(Map.of("id",UUID.randomUUID().toString(),"class","sheet","title",root.title,"rootTopic",center)));
        files.put("metadata.json",Map.of("creator",Map.of("name","登科喵PDF"),"dataStructureVersion","2"));
        files.put("manifest.json",Map.of("file-entries",Map.of("content.json",Map.of(),"metadata.json",Map.of())));
        try(var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){
            for(var file:files.entrySet()){zip.putNextEntry(new ZipEntry(file.getKey()));zip.write(json.writeValueAsBytes(file.getValue()));zip.closeEntry();}
            zip.finish();return out.toByteArray();
        }
    }
    private static final int WIDTH=260,GAP=55,PAD=30;
    private static final Color[] COLORS={new Color(0x246BFD),new Color(0x198B83),new Color(0x8356C4),new Color(0xC47720)};
    private static class Box {
        Node node;List<String> lines;List<Box> children=new ArrayList<>();int height,span,y,depth;
    }
    private static Box layout(Node node,int depth,FontMetrics metrics){
        var box=new Box();box.node=node;box.depth=depth;box.lines=new ArrayList<>();var row=new StringBuilder();
        for(int offset=0;offset<node.title.length();){int cp=node.title.codePointAt(offset);String next=new String(Character.toChars(cp));if(metrics.stringWidth(row+next)>WIDTH-28&&!row.isEmpty()){box.lines.add(row.toString());row.setLength(0);}row.append(next);offset+=Character.charCount(cp);}
        if(!row.isEmpty())box.lines.add(row.toString());box.height=box.lines.size()*30+24;
        for(var child:node.children)box.children.add(layout(child,depth+1,metrics));
        box.span=Math.max(box.height,box.children.stream().mapToInt(b->b.span).sum()+Math.max(0,box.children.size()-1)*20);return box;
    }
    private static void position(Box box,int top){box.y=top+(box.span-box.height)/2;int cursor=top+(box.span-box.children.stream().mapToInt(b->b.span).sum()-Math.max(0,box.children.size()-1)*20)/2;for(var child:box.children){position(child,cursor);cursor+=child.span+20;}}
    static BufferedImage image(Node root){
        var font=new Font(Font.SANS_SERIF,Font.PLAIN,22);var scratch=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);var measure=scratch.createGraphics();measure.setFont(font);var box=layout(root,0,measure.getFontMetrics());measure.dispose();scratch.flush();position(box,PAD);
        int depth=maxDepth(box);var image=new BufferedImage(PAD*2+(depth+1)*WIDTH+depth*GAP,box.span+PAD*2,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();try{g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setFont(font);paint(g,box,0);}finally{g.dispose();}return image;
    }
    private static int maxDepth(Box box){return Math.max(box.depth,box.children.stream().mapToInt(MindMapExport::maxDepth).max().orElse(0));}
    private static void paint(Graphics2D g,Box box,int branch){
        int x=PAD+box.depth*(WIDTH+GAP);var color=COLORS[branch%COLORS.length];int index=0;
        for(var child:box.children){int childBranch=box.depth==0?index++:branch;g.setColor(COLORS[childBranch%COLORS.length]);g.setStroke(new BasicStroke(2));g.draw(new CubicCurve2D.Float(x+WIDTH,box.y+box.height/2f,x+WIDTH+GAP/2f,box.y+box.height/2f,x+WIDTH+GAP/2f,child.y+child.height/2f,x+WIDTH+GAP,child.y+child.height/2f));paint(g,child,childBranch);}
        g.setColor(box.depth==0?new Color(0x18345F):color);g.fillRoundRect(x,box.y,WIDTH,box.height,18,18);
        g.setColor(Color.WHITE);for(int i=0;i<box.lines.size();i++)g.drawString(box.lines.get(i),x+14,box.y+32+i*30);
    }
}
