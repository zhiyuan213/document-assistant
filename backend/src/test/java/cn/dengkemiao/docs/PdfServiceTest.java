package cn.dengkemiao.docs;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PdfServiceTest {
    PdfService service=new PdfService();
    static byte[] image() throws Exception {
        BufferedImage image=new BufferedImage(240,320,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,240,320);g.setColor(Color.BLUE);g.drawString("Dengke",40,80);g.dispose();
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    static byte[] textPdf(String text) throws Exception {
        try(PDDocument doc=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            PDPage page=new PDPage();doc.addPage(page);
            try(PDPageContentStream stream=new PDPageContentStream(doc,page)){stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(20,100);stream.showText(text);stream.endText();}
            doc.save(out);return out.toByteArray();
        }
    }
    @Test void imagesGenerateLandscapePages() throws Exception {
        byte[] result=service.images(List.of(image(),image()),true,false);
        try(PDDocument doc=Loader.loadPDF(result)){assertEquals(2,doc.getNumberOfPages());assertTrue(doc.getPage(0).getMediaBox().getWidth()>doc.getPage(0).getMediaBox().getHeight());}
    }
    @Test void mergePreservesPageOrderAndText() throws Exception {
        try(PDDocument doc=Loader.loadPDF(service.merge(List.of(textPdf("first"),textPdf("second"))))){assertEquals(2,doc.getNumberOfPages());assertEquals("first\nsecond",new PDFTextStripper().getText(doc).trim().replace("\r",""));assertTrue(doc.getPage(0).getResources().getFontNames().iterator().hasNext());assertEquals("Helvetica",doc.getPage(0).getResources().getFont(doc.getPage(0).getResources().getFontNames().iterator().next()).getName());}
    }
    @Test void compressionNeverGrowsOrDestroysText() throws Exception {
        byte[] input=textPdf("Keep this text"),result=service.compress(input);assertTrue(result.length<=input.length);
        try(PDDocument doc=Loader.loadPDF(result)){assertTrue(new PDFTextStripper().getText(doc).contains("Keep this text"));}
    }
    @Test void invalidImageAndMergeCountAreRejected(){assertThrows(ApiError.class,()->service.images(List.of(new byte[]{1,2}),false,false));assertThrows(ApiError.class,()->service.merge(List.of(new byte[]{1})));}
}
