package cn.dengkemiao.docs;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import static org.junit.jupiter.api.Assertions.*;

class PdfExtrasTest {
    @Test void redactionRemovesOriginalTextAndComparisonHighlightsChanges()throws Exception {
        var marked=tools.run("watermark",List.of(source()),"a.pdf",Map.of("text","SECRET"));
        var redacted=tools.run("redact",List.of(marked.bytes()),"a.pdf",Map.of("redactX","0","redactY","0","redactWidth","100","redactHeight","100"));
        try(var doc=Loader.loadPDF(redacted.bytes())){assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(doc).isBlank());var bitmap=new PDFRenderer(doc).renderImage(0);assertEquals(0,bitmap.getRGB(bitmap.getWidth()/2,bitmap.getHeight()/2)&0xffffff);bitmap.flush();}
        var compared=tools.run("compare",List.of(source(),marked.bytes()),"a.pdf",Map.of());
        try(var zip=new ZipInputStream(new ByteArrayInputStream(compared.bytes()))){assertNotNull(zip.getNextEntry());var bitmap=javax.imageio.ImageIO.read(new ByteArrayInputStream(zip.readAllBytes()));assertNotNull(bitmap);int highlights=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=bitmap.getWidth()/2;x<bitmap.getWidth();x++)if((bitmap.getRGB(x,y)&0xffffff)==0xff5599)highlights++;assertTrue(highlights>0);bitmap.flush();}
        assertThrows(ApiError.class,()->tools.run("redact",List.of(source()),"a.pdf",Map.of("redactWidth","0")));
    }
    private final PdfTools tools=new PdfTools(new PdfService());
    private byte[] source()throws Exception{return new PdfService().images(List.of(PdfServiceTest.image()),false,false);}
    @Test void cropChangesVisibleBoundsAndRejectsInvalidMargins()throws Exception{
        var result=tools.run("crop",List.of(source()),"a.pdf",Map.of("left","10","right","10","top","20","bottom","20"));
        try(var doc=Loader.loadPDF(result.bytes())){assertEquals(doc.getPage(0).getMediaBox().getWidth()*.8,doc.getPage(0).getCropBox().getWidth(),.01);assertEquals(doc.getPage(0).getMediaBox().getHeight()*.6,doc.getPage(0).getCropBox().getHeight(),.01);assertNotNull(new PDFRenderer(doc).renderImage(0));}
        assertThrows(ApiError.class,()->tools.run("crop",List.of(source()),"a.pdf",Map.of("left","60","right","40")));
        assertThrows(ApiError.class,()->tools.run("crop",List.of(source()),"a.pdf",Map.of("left","NaN")));
    }
    @Test void signatureDrawsAnImageAndRejectsOffPagePlacement()throws Exception{
        var result=tools.run("sign",List.of(source(),PdfServiceTest.image()),"a.pdf",Map.of("signX","10","signY","10","signWidth","20"));
        try(var doc=Loader.loadPDF(result.bytes())){int count=0;for(var name:doc.getPage(0).getResources().getXObjectNames())if(doc.getPage(0).getResources().getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject)count++;assertEquals(2,count);assertNotNull(new PDFRenderer(doc).renderImage(0));}
        assertThrows(ApiError.class,()->tools.run("sign",List.of(source(),PdfServiceTest.image()),"a.pdf",Map.of("signX","95")));
        assertThrows(ApiError.class,()->tools.run("sign",List.of(source(),PdfServiceTest.image()),"a.pdf",Map.of("signPage","2")));
    }
    @Test void extractionReturnsActualImagesAndRepairPreservesPages()throws Exception{
        var result=tools.run("extract-images",List.of(source()),"a.pdf",Map.of());
        try(var zip=new ZipInputStream(new ByteArrayInputStream(result.bytes()))){assertNotNull(zip.getNextEntry());assertNotNull(javax.imageio.ImageIO.read(new ByteArrayInputStream(zip.readAllBytes())));assertNull(zip.getNextEntry());}
        var repaired=tools.run("repair",List.of(source()),"a.pdf",Map.of());try(var doc=Loader.loadPDF(repaired.bytes())){assertEquals(1,doc.getNumberOfPages());assertNotNull(new PDFRenderer(doc).renderImage(0));}
        assertThrows(Exception.class,()->tools.run("repair",List.of(new byte[]{1,2,3}),"a.pdf",Map.of()));
    }
    @Test void markdownExtractsTextAndRejectsScans()throws Exception{
        var text=tools.run("watermark",List.of(source()),"a.pdf",Map.of("text","MARKDOWN TEST"));
        var result=tools.run("markdown",List.of(text.bytes()),"a.pdf",Map.of());
        try(var zip=new ZipInputStream(new ByteArrayInputStream(result.bytes()))){assertEquals("document.md",zip.getNextEntry().getName());String contents=new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(contents.contains("## Page 1"));assertTrue(contents.contains("MARKDOWN TEST"));}
        assertThrows(ApiError.class,()->tools.run("markdown",List.of(source()),"a.pdf",Map.of()));
    }
    @Test void writesSyntheticOfficeAndScannedFixtures()throws Exception{
        Path dir=Path.of("target/expanded-smoke");Files.createDirectories(dir);
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var out=Files.newOutputStream(dir.resolve("sample.xlsx"))){book.createSheet("Test").createRow(0).createCell(0).setCellValue("EXCEL CONVERSION TEST");book.write(out);}
        try(var deck=new org.apache.poi.xslf.usermodel.XMLSlideShow();var out=Files.newOutputStream(dir.resolve("sample.pptx"))){var text=deck.createSlide().createTextBox();text.setAnchor(new java.awt.Rectangle(50,50,500,100));text.setText("POWERPOINT CONVERSION TEST");deck.write(out);}
        var bitmap=new java.awt.image.BufferedImage(1200,500,java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=bitmap.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,1200,500);graphics.setColor(java.awt.Color.BLACK);graphics.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,55));graphics.drawString("DOCUMENT OCR TEST",50,180);graphics.drawString("1234567890",50,270);graphics.dispose();
        try(var out=new ByteArrayOutputStream()){javax.imageio.ImageIO.write(bitmap,"png",out);Files.write(dir.resolve("scan.pdf"),new PdfService().images(List.of(out.toByteArray()),false,false));}bitmap.flush();
        Files.write(dir.resolve("crop.pdf"),tools.run("crop",List.of(source()),"a.pdf",Map.of()).bytes());
        Files.write(dir.resolve("sign.pdf"),tools.run("sign",List.of(source(),PdfServiceTest.image()),"a.pdf",Map.of()).bytes());
    }
}
