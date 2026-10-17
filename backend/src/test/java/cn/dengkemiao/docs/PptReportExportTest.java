package cn.dengkemiao.docs;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.poi.xslf.usermodel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PptReportExportTest {
    static final String CONTENT="{\"title\":\"供应商责任汇报\",\"subtitle\":\"合规经营与可持续采购\",\"slides\":[{\"title\":\"以责任采购推动长期合作\",\"bullets\":[\"供应商应遵守法律法规与商业道德 [A-P1]\",\"采购应兼顾环境保护和资源节约 [A-P2]\",\"关键措施需要明确责任和执行安排 [A-P3]\"]},{\"title\":\"把原则落实到具体行动\",\"bullets\":[\"开展供应商定期评估 [A-P4]\",\"跟踪整改事项，形成闭环 [A-P5]\"]}]}";
    @Test void createsEditableWideScreenSlidesAndRendersChinese()throws Exception {
        var response=PptReportExport.export(CONTENT,"pptx");assertTrue(response.getHeaders().getContentType().toString().contains("presentationml"));
        var dir=Path.of("target/ppt-preview");Files.createDirectories(dir);Files.write(dir.resolve("presentation.pptx"),response.getBody());
        try(var ppt=new XMLSlideShow(new ByteArrayInputStream(response.getBody()))){
            assertEquals(new Dimension(960,540),ppt.getPageSize());assertEquals(3,ppt.getSlides().size());
            assertTrue(slideText(ppt.getSlides().get(1)).contains("供应商应遵守法律法规与商业道德"));
            for(int i=0;i<ppt.getSlides().size();i++){
                var slide=ppt.getSlides().get(i);assertTrue(slide.getShapes().stream().anyMatch(XSLFTextShape.class::isInstance));assertFalse(slide.getShapes().stream().anyMatch(XSLFPictureShape.class::isInstance));
                var image=new BufferedImage(960,540,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();try{g.setColor(Color.WHITE);g.fillRect(0,0,960,540);slide.draw(g);}finally{g.dispose();}
                if(i==0)assertEquals(new Color(0x18345F).getRGB(),image.getRGB(10,10));
                ImageIO.write(image,"png",dir.resolve("slide-"+i+".png").toFile());image.flush();
                for(var shape:slide.getShapes()){var box=shape.getAnchor();assertTrue(box.getX()>=0&&box.getY()>=0);assertTrue(box.getMaxX()<=960&&box.getMaxY()<=540);}
            }
        }
    }
    static String slideText(XSLFSlide slide){return slide.getShapes().stream().filter(XSLFTextShape.class::isInstance).map(s->((XSLFTextShape)s).getText()).reduce("",(a,b)->a+b);}
    @Test void longBulletsContinueOnNewSlidesWithoutLosingText()throws Exception {
        String bullet="长内容要完整保留且不能越过页面边界。".repeat(15);
        var deck=new PptReportExport.Deck("长标题".repeat(30),"",List.of(new PptReportExport.Slide("章节标题".repeat(20),List.of(bullet,bullet,bullet,bullet,bullet,bullet,bullet,bullet))));
        try(var ppt=new XMLSlideShow(new ByteArrayInputStream(PptReportExport.write(deck)))){
            assertTrue(ppt.getSlides().size()>2);String text=ppt.getSlides().stream().skip(1).map(PptReportExportTest::slideText).reduce("",String::concat).replaceAll("\\s+","");
            assertEquals(120,text.split("长内容要完整保留且不能越过页面边界。",-1).length-1);
        }
    }
    @Test void malformedOrUnboundedDataIsRejected()throws Exception {
        for(String bad:List.of("{}","[]","bad","{\"title\":\"test\",\"slides\":[]}","{\"title\":\"test\",\"slides\":[{\"title\":\"test\",\"bullets\":[]}]}"))assertThrows(ApiError.class,()->PptReportExport.parse(bad));
        assertThrows(ApiError.class,()->PptReportExport.export(CONTENT,"pdf"));
    }
}
