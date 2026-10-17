package cn.dengkemiao.docs;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PdfTextExportTest {
    static byte[] textPdf(String... pages)throws Exception {
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            for(String text:pages){var page=new PDPage();doc.addPage(page);if(!text.isEmpty())try(var stream=new PDPageContentStream(doc,page)){stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(40,700);stream.showText(text);stream.endText();}}
            doc.save(out);return out.toByteArray();
        }
    }
    @Test void exportsRealUtf8TxtInPageOrderWithoutOcr()throws Exception {
        var tools=new PdfTools(new PdfService());var result=tools.run("pdf-txt",List.of(textPdf("First page","Second page")),"a.pdf",Map.of());
        assertEquals("txt",result.extension());assertEquals("text/plain;charset=UTF-8",result.type());
        String text=new String(result.bytes(),StandardCharsets.UTF_8);assertEquals("\uFEFFFirst page\r\n\r\nSecond page\r\n",text);
        assertEquals((byte)0xef,result.bytes()[0]);
    }
    @Test void mixedNativeAndScannedPagesKeepTheirOrderAndOnlyScansUseOcr()throws Exception {
        byte[] images=new PdfService().images(List.of(PdfServiceTest.image()),false,false);
        try(var nativeDoc=Loader.loadPDF(textPdf("Native first","Native last"));var scan=Loader.loadPDF(images);var source=new PDDocument()){
            source.importPage(nativeDoc.getPage(0));source.importPage(scan.getPage(0));source.importPage(nativeDoc.getPage(1));
            int[] calls={0};var result=PdfTextExport.extract(source,scanned->{calls[0]++;assertEquals(1,scanned.getNumberOfPages());return textPdf("Recognized scan");});
            assertEquals(1,calls[0]);assertEquals("\uFEFFNative first\r\n\r\nRecognized scan\r\n\r\nNative last\r\n",new String(result.bytes(),StandardCharsets.UTF_8));
        }
    }
    @Test void blankPdfAndUnrecognizedScansDoNotSilentlyExportEmptyFiles()throws Exception {
        try(var blank=Loader.loadPDF(textPdf(""))){assertThrows(ApiError.class,()->PdfTextExport.extract(blank,scan->{fail("Blank pages should not invoke OCR");return null;}));}
        try(var scan=Loader.loadPDF(new PdfService().images(List.of(PdfServiceTest.image()),false,false))){assertThrows(ApiError.class,()->PdfTextExport.extract(scan,doc->textPdf("")));}
    }
    @Test void excessiveScanCountIsRejectedBeforeCallingOcr()throws Exception {
        try(var scan=Loader.loadPDF(new PdfService().images(List.of(PdfServiceTest.image()),false,false));var doc=new PDDocument()){
            for(int i=0;i<21;i++)doc.importPage(scan.getPage(0));
            assertThrows(ApiError.class,()->PdfTextExport.extract(doc,pages->{fail("Should reject before OCR");return null;}));
        }
    }
}
