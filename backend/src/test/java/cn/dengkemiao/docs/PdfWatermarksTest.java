package cn.dengkemiao.docs;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import static org.junit.jupiter.api.Assertions.*;

class PdfWatermarksTest {
    byte[] fixture(boolean watermark, boolean image)throws Exception {
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            var page=new PDPage();doc.addPage(page);
            try(var s=new PDPageContentStream(doc,page)){
                s.beginText();s.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);s.newLineAtOffset(50,700);s.showText("BODY MUST REMAIN");s.endText();
                if(watermark){s.saveGraphicsState();var state=new PDExtendedGraphicsState();state.setNonStrokingAlphaConstant(0.25f);s.setGraphicsStateParameters(state);
                    if(image){var bitmap=new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB);s.drawImage(LosslessFactory.createFromImage(doc,bitmap),100,300,150,100);}
                    else{s.beginText();s.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),40);s.newLineAtOffset(50,400);s.showText("WATERMARK");s.endText();}s.restoreGraphicsState();}
            }doc.save(out);return out.toByteArray();
        }
    }
    @Test void previewRemovalPreservesBodyAndRequiresMatchingProof()throws Exception {
        byte[] input=fixture(true,false);String id;
        try(var doc=PdfWatermarks.load(input)){var candidates=PdfWatermarks.scan(doc).candidates();assertEquals(1,candidates.size());id=candidates.get(0).id();}
        var preview=PdfWatermarks.preview(input,id);assertEquals(1,((List<?>)preview.get("previews")).size());
        var bytes=PdfWatermarks.remove(input,id,(String)preview.get("proof"));
        try(var result=Loader.loadPDF(bytes)){String text=new PDFTextStripper().getText(result);assertTrue(text.contains("BODY MUST REMAIN"));assertFalse(text.contains("WATERMARK"));}
        try(var original=Loader.loadPDF(input)){assertTrue(new PDFTextStripper().getText(original).contains("WATERMARK"));}
        assertThrows(ApiError.class,()->PdfWatermarks.remove(input,id,""));
        assertThrows(ApiError.class,()->PdfWatermarks.remove(fixture(false,false),id,(String)preview.get("proof")));
        assertThrows(ApiError.class,()->PdfWatermarks.remove(input,id+"bad",(String)preview.get("proof")));
    }
    @Test void imageCandidatesAndUnsupportedFilesAreDistinguished()throws Exception {
        try(var doc=PdfWatermarks.load(fixture(true,true))){assertEquals("独立半透明图片",PdfWatermarks.scan(doc).candidates().get(0).kind());}
        try(var doc=PdfWatermarks.load(fixture(false,false))){assertTrue(PdfWatermarks.scan(doc).candidates().isEmpty());}
    }
    @Test void pdfExportUsesOfficeConversionAndDoesNotCallAi()throws Exception {
        var tools=org.mockito.Mockito.mock(PdfTools.class);
        org.mockito.Mockito.when(tools.run(org.mockito.ArgumentMatchers.eq("word-pdf"),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.eq("summary.docx"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(new PdfTools.Output(new byte[]{1,2,3},"pdf","application/pdf"));
        var response=new AiReportExport(tools).export("四、待办事项\n未提及","pdf");
        assertEquals("application/pdf",response.getHeaders().getContentType().toString());
        var files=org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(tools).run(org.mockito.ArgumentMatchers.eq("word-pdf"),files.capture(),org.mockito.ArgumentMatchers.eq("summary.docx"),org.mockito.ArgumentMatchers.anyMap());
        try(var doc=new XWPFDocument(new ByteArrayInputStream((byte[])files.getValue().get(0)))){assertTrue(doc.getParagraphs().stream().anyMatch(p->p.getText().contains("待办事项")));}
    }
    @Test void reportIsAReadableWordDocument()throws Exception {
        byte[] bytes=AiReportExport.word("一、文档概览\n测试报告\n二、重点内容\n数据 [A-P1]\n三、主要结论\n结论\n四、待办事项\n未提及");
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/test-artifacts"));java.nio.file.Files.write(java.nio.file.Path.of("target/test-artifacts/ai-summary.docx"),bytes);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(bytes))){String text=doc.getParagraphs().stream().map(p->p.getText()).reduce("",(a,b)->a+"\n"+b);assertTrue(text.contains("重点内容"));assertTrue(text.contains("[A-P1]"));assertTrue(text.contains("待办事项"));}
    }
}
