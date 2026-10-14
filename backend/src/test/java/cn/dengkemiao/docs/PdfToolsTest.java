package cn.dengkemiao.docs;

import java.util.*;
import java.io.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import static org.junit.jupiter.api.Assertions.*;

class PdfToolsTest {
    @Test void splittingPreservesInheritedFontsAndRejectsTrailingSeparator() throws Exception {
        byte[] input;
        try(var doc=new org.apache.pdfbox.pdmodel.PDDocument();var out=new ByteArrayOutputStream()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();doc.addPage(page);
            try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,page)) {
                stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(30,100);stream.showText("Inherited font text");stream.endText();
            }
            doc.getPages().getCOSObject().setItem(org.apache.pdfbox.cos.COSName.RESOURCES,page.getResources());
            page.getCOSObject().removeItem(org.apache.pdfbox.cos.COSName.RESOURCES);doc.save(out);input=out.toByteArray();
        }
        var split=tools.run("split",List.of(input),"a.pdf",Map.of());
        try(var zip=new ZipInputStream(new ByteArrayInputStream(split.bytes()))){assertNotNull(zip.getNextEntry());try(var doc=Loader.loadPDF(zip.readAllBytes())){assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(doc).contains("Inherited font text"));}}
        assertThrows(ApiError.class,()->tools.run("split",List.of(input),"a.pdf",Map.of("ranges","1,")));
    }
    @Test void separatesImagesAndAcceptsImageWatermark() throws Exception {
        var output=tools.run("images",List.of(PdfServiceTest.image(),PdfServiceTest.image()),"image.png",Map.of("imageOutput","separate"));
        try(var zip=new ZipInputStream(new ByteArrayInputStream(output.bytes()))){int count=0;while(zip.getNextEntry()!=null){try(var doc=Loader.loadPDF(zip.readAllBytes())){assertEquals(1,doc.getNumberOfPages());}count++;}assertEquals(2,count);}
        var marked=tools.run("watermark",List.of(source(),PdfServiceTest.image()),"a.pdf",Map.of("watermarkMode","image"));
        try(var doc=Loader.loadPDF(marked.bytes())){assertEquals(2,doc.getNumberOfPages());}
    }
    private final PdfService pdf=new PdfService();private final PdfTools tools=new PdfTools(pdf);
    private byte[] source() throws Exception{return pdf.images(List.of(PdfServiceTest.image(),PdfServiceTest.image()),false,false);}
    @Test void splitPreservesPageCountsAndRejectsInvalidRanges() throws Exception {
        var result=tools.run("split",List.of(source()),"sample.pdf",Map.of("ranges","1-2,2"));
        try(var zip=new ZipInputStream(new ByteArrayInputStream(result.bytes()))){int count=0;while(zip.getNextEntry()!=null){try(var doc=Loader.loadPDF(zip.readAllBytes())){assertEquals(count==0?2:1,doc.getNumberOfPages());}count++;}assertEquals(2,count);}
        assertThrows(ApiError.class,()->tools.run("split",List.of(source()),"sample.pdf",Map.of("ranges","1-9")));
    }
    @Test void encryptionRequiresCorrectPasswordAndDecrypts() throws Exception {
        var encrypted=tools.run("encrypt",List.of(source()),"a.pdf",Map.of("password","valid-password"));
        assertThrows(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException.class,()->Loader.loadPDF(encrypted.bytes()));
        assertThrows(ApiError.class,()->tools.run("decrypt",List.of(encrypted.bytes()),"a.pdf",Map.of("password","incorrect")));
        var clear=tools.run("decrypt",List.of(encrypted.bytes()),"a.pdf",Map.of("password","valid-password"));
        try(var doc=Loader.loadPDF(clear.bytes())){assertFalse(doc.isEncrypted());assertEquals(2,doc.getNumberOfPages());}
    }
    @Test void rendersZipAndAddsWatermarkWithoutChangingPages() throws Exception {
        var images=tools.run("pdf-images",List.of(source()),"a.pdf",Map.of("format","png"));
        try(var zip=new ZipInputStream(new ByteArrayInputStream(images.bytes()))){int count=0;while(zip.getNextEntry()!=null){assertNotNull(javax.imageio.ImageIO.read(new ByteArrayInputStream(zip.readAllBytes())));count++;}assertEquals(2,count);}
        var marked=tools.run("watermark",List.of(source()),"a.pdf",Map.of("text","DENGKE"));
        try(var doc=Loader.loadPDF(marked.bytes())){assertEquals(2,doc.getNumberOfPages());assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(doc).contains("DENGKE"));}
    }
    @Test void pdfWordRejectsScansAndExportsExtractedText() throws Exception {
        assertThrows(ApiError.class,()->tools.run("pdf-word",List.of(source()),"a.pdf",Map.of()));
        var textPdf=tools.run("watermark",List.of(source()),"a.pdf",Map.of("text","HELLO"));
        var word=tools.run("pdf-word",List.of(textPdf.bytes()),"a.pdf",Map.of());
        try(var doc=new XWPFDocument(new ByteArrayInputStream(word.bytes()))){assertTrue(doc.getParagraphs().stream().anyMatch(p->p.getText().contains("HELLO")));}
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/smoke"));
        java.nio.file.Files.write(java.nio.file.Path.of("target/smoke/sample.docx"),word.bytes());
        java.nio.file.Files.write(java.nio.file.Path.of("target/smoke/sample.pdf"),textPdf.bytes());
    }
}
