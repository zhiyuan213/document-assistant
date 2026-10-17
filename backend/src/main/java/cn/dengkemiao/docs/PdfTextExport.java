package cn.dengkemiao.docs;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

final class PdfTextExport {
    @FunctionalInterface interface Recognize { byte[] run(PDDocument scanned)throws Exception; }
    static PdfTools.Output export(PDDocument source)throws Exception {
        return extract(source,scanned->PdfExtras.run("ocr",scanned,List.of(),Map.of()).bytes());
    }
    static PdfTools.Output extract(PDDocument source,Recognize recognize)throws Exception {
        var pages=new ArrayList<String>();var scans=new ArrayList<Integer>();var stripper=new PDFTextStripper();stripper.setSortByPosition(true);
        int characters=0;
        for(int i=0;i<source.getNumberOfPages();i++){
            stripper.setStartPage(i+1);stripper.setEndPage(i+1);String text=stripper.getText(source).strip();
            pages.add(text);characters+=text.length();
            if(characters>4_000_000)throw new ApiError(400,"文字输出过大，请拆分后处理");
            if(text.isBlank()&&source.getPage(i).hasContents())scans.add(i);
        }
        if(scans.size()>20)throw new ApiError(400,"扫描文字识别最多支持20页，请先拆分");
        if(!scans.isEmpty())try(var scanned=new PDDocument()){
            for(int index:scans)scanned.importPage(source.getPage(index)).setResources(source.getPage(index).getResources());
            try(var recognized=Loader.loadPDF(recognize.run(scanned))){
                if(recognized.getNumberOfPages()!=scans.size())throw new ApiError(500,"文字识别页数异常，请重试");
                for(int i=0;i<scans.size();i++){
                    stripper.setStartPage(i+1);stripper.setEndPage(i+1);String text=stripper.getText(recognized).strip();
                    if(text.isBlank())throw new ApiError(400,"第"+(scans.get(i)+1)+"页未识别到文字，请检查扫描清晰度");
                    characters+=text.length();if(characters>4_000_000)throw new ApiError(400,"文字输出过大，请拆分后处理");
                    pages.set(scans.get(i),text);
                }
            }
        }
        if(pages.stream().allMatch(String::isBlank))throw new ApiError(400,"此PDF没有可提取文字");
        String text=String.join("\n\n",pages).replaceAll("\\r\\n|\\r|\\n","\r\n");
        // BOM keeps Chinese readable in older Windows text editors.
        return new PdfTools.Output(("\uFEFF"+text+"\r\n").getBytes(StandardCharsets.UTF_8),"txt","text/plain;charset=UTF-8");
    }
}
