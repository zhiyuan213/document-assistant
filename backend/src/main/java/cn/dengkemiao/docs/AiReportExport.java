package cn.dengkemiao.docs;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

@Service
public class AiReportExport {
    private final PdfTools tools;
    private final Semaphore slots = new Semaphore(2);
    public AiReportExport(PdfTools tools) { this.tools=tools; }
    public ResponseEntity<byte[]> export(String content, String format) throws Exception {
        if (!List.of("docx","pdf").contains(format)) throw new ApiError(400,"请选择 Word 或 PDF 格式");
        if(content.isBlank() || content.length()>40000) throw new ApiError(400,"导出内容不能为空且不能超过40000字");
        if(!slots.tryAcquire()) throw new ApiError(429,"导出繁忙，请稍后重试");
        try {
            byte[] bytes=word(content);
            if(format.equals("pdf")) bytes=tools.run("word-pdf",List.of(bytes),"summary.docx",Map.of()).bytes();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(format.equals("pdf")?"application/pdf":"application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header("Content-Disposition","attachment; filename=\"AI-summary."+format+"\"").body(bytes);
        } finally { slots.release(); }
    }
    static byte[] word(String content) throws Exception {
        return word(content,"PDF 文档总结报告");
    }
    static byte[] word(String content,String headingText) throws Exception {
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()) {
            var title=doc.createParagraph();title.setAlignment(ParagraphAlignment.CENTER);
            var tr=title.createRun();tr.setText(headingText);tr.setBold(true);tr.setFontSize(20);tr.setColor("245BAD");tr.setFontFamily("WenQuanYi Micro Hei");
            for(String line:content.split("\\R",-1)) {
                var p=doc.createParagraph();p.setSpacingAfter(120);p.setSpacingBetween(1.4);
                var run=p.createRun();run.setFontFamily("WenQuanYi Micro Hei");
                boolean heading=line.matches("^(#{1,3} .*|[一二三四]、.*)$");
                run.setBold(heading);run.setFontSize(heading?14:11);run.setColor(heading?"245BAD":"253047");
                run.setText(line.replaceFirst("^#{1,3}\\s+", ""));
            }
            var p=doc.createParagraph();var note=p.createRun();note.setText(headingText.equals("Translated Document")?"AI-generated translation. Verify against the source document. Page markers refer to the original PDF.":"AI 生成内容，请结合原文核对。来源页码标记对应原 PDF。");note.setFontSize(9);note.setColor("777777");
            doc.write(out);return out.toByteArray();
        }
    }
}
