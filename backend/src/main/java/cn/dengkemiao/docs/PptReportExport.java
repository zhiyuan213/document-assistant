package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.sl.usermodel.ShapeType;
import org.springframework.http.*;

/** AI supplies bounded slide content; Java owns all editable shapes and layout. */
final class PptReportExport {
    static final String PROMPT="请根据以下PDF制作适合汇报的简体中文PPT内容，重新组织要点，不逐页抄写。只输出JSON对象，不要Markdown。格式：{\"title\":\"演示主题\",\"subtitle\":\"一句话概述\",\"slides\":[{\"title\":\"本页结论\",\"bullets\":[\"要点及关键数据 [A-P1]\",\"另一要点 [A-P2]\"]}]}。生成4至8个内容页，按概览、重点、结论和行动建议组织；每页2至4条，每条尽量不超过40字，标题不超过30字。只依据原文，关键事实引用来源页码，不编造数据、图表或建议依据。信息不足时减少页数，不凑页。\n\n";
    record Slide(String title,List<String> bullets){}
    record Deck(String title,String subtitle,List<Slide> slides){}
    static Deck parse(String content)throws Exception {
        if(content==null||content.length()>40000)throw new ApiError(400,"PPT内容过大，请拆分文档");
        JsonNode value;
        try{value=new ObjectMapper().readTree(content);}catch(Exception e){throw new ApiError(502,"PPT内容结构不完整，请重新生成");}
        if(value==null||!value.isObject()||!value.path("slides").isArray()||value.path("slides").isEmpty()||value.path("slides").size()>20)throw new ApiError(502,"PPT页面结构异常，请重新生成");
        String title=text(value.get("title"),100);String subtitle=value.hasNonNull("subtitle")&&!value.path("subtitle").asText().isBlank()?text(value.get("subtitle"),200):"";
        var slides=new ArrayList<Slide>();
        for(var item:value.path("slides")){
            var bullets=item.path("bullets");
            if(!bullets.isArray()||bullets.isEmpty()||bullets.size()>8)throw new ApiError(502,"PPT页面要点异常，请重新生成");
            var rows=new ArrayList<String>();for(var row:bullets)rows.add(text(row,300));
            slides.add(new Slide(text(item.get("title"),100),List.copyOf(rows)));
        }
        return new Deck(title,subtitle,List.copyOf(slides));
    }
    private static String text(JsonNode value,int max){
        if(value==null||!value.isTextual())throw new ApiError(502,"PPT文字格式异常，请重新生成");
        String text=value.asText().strip();
        if(text.isEmpty()||text.codePointCount(0,text.length())>max||text.codePoints().anyMatch(Character::isISOControl))throw new ApiError(502,"PPT文字长度或格式异常，请重新生成");
        return text;
    }
    static ResponseEntity<byte[]> export(String content,String format)throws Exception {
        if(!"pptx".equals(format))throw new ApiError(400,"请选择PPTX格式");
        byte[] bytes=write(parse(content));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation")).header("Content-Disposition","attachment; filename=\"AI-presentation.pptx\"").body(bytes);
    }
    private static final Color NAVY=new Color(0x18345F),BLUE=new Color(0x246BFD),INK=new Color(0x253047);
    static List<String> wrap(String value,int size,int width){
        var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{g.setFont(new Font("Microsoft YaHei",Font.PLAIN,size));var metrics=g.getFontMetrics();var rows=new ArrayList<String>();var row=new StringBuilder();
            for(int offset=0;offset<value.length();){int cp=value.codePointAt(offset);String next=new String(Character.toChars(cp));if(metrics.stringWidth(row+next)>width&&!row.isEmpty()){rows.add(row.toString());row.setLength(0);}row.append(next);offset+=Character.charCount(cp);}
            if(!row.isEmpty())rows.add(row.toString());return rows;
        }finally{g.dispose();image.flush();}
    }
    static byte[] write(Deck deck)throws Exception {
        try(var ppt=new XMLSlideShow();var out=new ByteArrayOutputStream()){
            ppt.setPageSize(new Dimension(960,540));
            var cover=ppt.createSlide();cover.getBackground().setFillColor(NAVY);
            shape(cover,ShapeType.RECT,0,0,960,540,NAVY);
            shape(cover,ShapeType.RECT,58,106,76,6,BLUE);
            int coverSize=fitTitle(deck.title,42,780,185);
            text(cover,String.join("\n",wrap(deck.title,coverSize,780)),58,148,844,190,coverSize,Color.WHITE,true);
            if(!deck.subtitle.isEmpty())text(cover,String.join("\n",wrap(deck.subtitle,22,820)),58,350,844,158,22,new Color(0xD2DFF2),false);
            for(var source:deck.slides){
                XSLFSlide slide=null;int y=132;
                for(String bullet:source.bullets){
                    var lines=wrap(bullet,22,800);int offset=0;
                    while(offset<lines.size()){
                        if(slide==null||y+42>478){slide=contentSlide(ppt,source.title);y=132;}
                        int count=Math.min(lines.size()-offset,Math.max(1,(478-y-18)/29));
                        int height=count*29+8;
                        shape(slide,ShapeType.ELLIPSE,58,y+8,7,7,BLUE);
                        text(slide,String.join("\n",lines.subList(offset,offset+count)),82,y,820,height,22,INK,false);
                        y+=height+18;offset+=count;
                    }
                }
            }
            for(int i=1;i<ppt.getSlides().size();i++)text(ppt.getSlides().get(i),String.format("%02d",i),850,505,55,22,12,new Color(0x73839B),false);
            ppt.write(out);return out.toByteArray();
        }
    }
    private static XSLFSlide contentSlide(XMLSlideShow ppt,String title){
        var slide=ppt.createSlide();slide.getBackground().setFillColor(Color.WHITE);
        shape(slide,ShapeType.RECT,58,46,5,62,BLUE);
        int size=fitTitle(title,32,760,83);
        text(slide,String.join("\n",wrap(title,size,760)),80,40,822,87,size,NAVY,true);return slide;
    }
    private static int fitTitle(String title,int start,int width,int height){int size=start;while(size>16&&wrap(title,size,width).size()*size*1.2>height)size-=2;return size;}
    private static void shape(XSLFSlide slide,ShapeType type,int x,int y,int w,int h,Color color){var shape=slide.createAutoShape();shape.setShapeType(type);shape.setAnchor(new Rectangle2D.Double(x,y,w,h));shape.setFillColor(color);shape.setLineColor(null);}
    private static void text(XSLFSlide slide,String value,int x,int y,int w,int h,int size,Color color,boolean bold){
        var box=slide.createTextBox();box.setAnchor(new Rectangle2D.Double(x,y,w,h));box.setLeftInset(0);box.setRightInset(0);box.setTopInset(0);box.setBottomInset(0);box.setWordWrap(false);box.setText(value);
        for(var paragraph:box.getTextParagraphs()){paragraph.setSpaceBefore(0.0);paragraph.setSpaceAfter(0.0);paragraph.setLineSpacing(110.0);for(var run:paragraph.getTextRuns()){run.setFontFamily("Microsoft YaHei");run.setFontSize((double)size);run.setFontColor(color);run.setBold(bold);}}
    }
}
