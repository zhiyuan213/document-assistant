package cn.dengkemiao.docs;

import java.io.*;
import java.nio.file.*;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PdfTools {
    public record Output(byte[] bytes,String extension,String type) {}
    private final PdfService pdf;
    @Value("${PDF_FONT:}") private String fontPath;
    @Value("${OFFICE_COMMAND:soffice}") private String office;
    public PdfTools(PdfService pdf) { this.pdf=pdf; }
    public Output run(String tool,List<byte[]> files,String filename,Map<String,String> options) throws Exception {
        if(files.isEmpty()) throw new ApiError(400,"请选择文件");
        if(tool.equals("images")) {
            if("separate".equals(options.get("imageOutput"))) {
                try(var result=new ByteArrayOutputStream();var zip=new ZipOutputStream(result)) {int i=0;for(byte[] image:files){zip.putNextEntry(new ZipEntry("image-"+(++i)+".pdf"));zip.write(pdf.images(List.of(image),"landscape".equals(options.get("orientation")),false));zip.closeEntry();}zip.finish();return out(result.toByteArray(),"zip");}
            }
            return out(pdf.images(files,"landscape".equals(options.get("orientation")),false),"pdf");
        }
        if(tool.equals("merge")) return out(pdf.merge(files),"pdf");
        if(tool.equals("compare")) return PdfExtras.compare(files);
        boolean imageWatermark=tool.equals("sign")||tool.equals("watermark")&&"image".equals(options.get("watermarkMode"));
        if(files.size()!=(imageWatermark?2:1)) throw new ApiError(400,imageWatermark?"请选择PDF和水印图片":"此工具每次处理一个文件");
        byte[] input=files.get(0);
        if(tool.equals("remove-watermark"))return out(PdfWatermarks.remove(input,options.getOrDefault("candidateIds",""),options.getOrDefault("previewProof","")),"pdf");
        if(tool.equals("compress")) return out(pdf.compress(input,switch(options.getOrDefault("compression","standard")){case "light"->0.85f;case "strong"->0.40f;default->0.65f;}),"pdf");
        if(Set.of("word-pdf","excel-pdf","ppt-pdf").contains(tool)) return office(input,filename,tool);
        String password=options.getOrDefault("password","");
        try(PDDocument doc=Loader.loadPDF(input,tool.equals("decrypt")?password:"")) {
            if(doc.getNumberOfPages()<1||doc.getNumberOfPages()>200) throw new ApiError(400,"支持1至200页PDF");
            if(doc.isEncrypted()&&!tool.equals("decrypt")) throw new ApiError(400,"请先使用PDF解密工具");
            switch(tool) {
                case "redact": case "markdown": case "crop": case "sign": case "extract-images": case "ocr": case "repair": return PdfExtras.run(tool,doc,files,options);
                case "rotate":
                    int angle;
                    try {angle=Integer.parseInt(options.getOrDefault("angle","90"));}catch(NumberFormatException e){throw new ApiError(400,"旋转角度请选择90、180或270度");}
                    if(!Set.of(90,180,270).contains(angle))throw new ApiError(400,"旋转角度请选择90、180或270度");
                    for(int index:pageSelection(options.getOrDefault("pages",""),doc.getNumberOfPages(),true))doc.getPage(index).setRotation(Math.floorMod(doc.getPage(index).getRotation()+angle,360));
                    break;
                case "extract": case "remove-pages": case "reorder":
                    var selected=pageSelection(options.getOrDefault("pages",""),doc.getNumberOfPages(),false);
                    if(tool.equals("reorder")&&selected.size()!=doc.getNumberOfPages())throw new ApiError(400,"调整页序需填写全部页码，每页仅出现一次");
                    if(tool.equals("remove-pages")){var removed=new HashSet<>(selected);selected=new ArrayList<>();for(int i=0;i<doc.getNumberOfPages();i++)if(!removed.contains(i))selected.add(i);if(selected.isEmpty())throw new ApiError(400,"不能删除全部页面，请至少保留一页");}
                    try(var arranged=new PDDocument();var bytes=new ByteArrayOutputStream()){for(int index:selected)arranged.importPage(doc.getPage(index)).setResources(doc.getPage(index).getResources());arranged.save(bytes);return out(bytes.toByteArray(),"pdf");}
                case "page-numbers":
                    var numberFont=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                    int start;
                    try {start=Integer.parseInt(options.getOrDefault("start","1"));}catch(NumberFormatException e){throw new ApiError(400,"起始页码需为1至9999的整数");}
                    if(start<1||start>9999)throw new ApiError(400,"起始页码需为1至9999的整数");
                    String position=options.getOrDefault("position","bottom");if(!Set.of("bottom","top").contains(position))throw new ApiError(400,"页码位置请选择顶部或底部");
                    int number=start;
                    for(PDPage page:doc.getPages()){
                        if(page.getRotation()!=0)throw new ApiError(400,"此PDF含旋转页面，暂不支持添加页码");
                        var box=page.getCropBox();if(box.getHeight()<48)throw new ApiError(400,"页面高度过小，无法添加页码");
                        String label=Integer.toString(number++);float width=numberFont.getStringWidth(label)/1000*11;
                        try(var stream=new PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true)){stream.beginText();stream.setNonStrokingColor(Color.DARK_GRAY);stream.setFont(numberFont,11);stream.newLineAtOffset(box.getLowerLeftX()+(box.getWidth()-width)/2,position.equals("top")?box.getUpperRightY()-22:box.getLowerLeftY()+16);stream.showText(label);stream.endText();}
                    }
                    break;
                case "pdf-txt": return PdfTextExport.export(doc);
                case "pdf-word": return word(doc);
                case "split": return split(doc,options.getOrDefault("ranges",""));
                case "pdf-images": return images(doc,options.getOrDefault("format","jpg"));
                case "watermark":
                    if(imageWatermark) imageWatermark(doc,files.get(1));else watermark(doc,options.getOrDefault("text","")); break;
                case "encrypt":
                    if(password.length()<6||password.length()>64) throw new ApiError(400,"密码需为6至64位");
                    var policy=new StandardProtectionPolicy(UUID.randomUUID().toString(),password,new AccessPermission());
                    policy.setEncryptionKeyLength(256);doc.protect(policy);break;
                case "decrypt":
                    if(password.isBlank()) throw new ApiError(400,"请输入PDF密码");
                    if(!doc.getCurrentAccessPermission().canModify()) throw new ApiError(403,"该密码没有修改权限，请使用所有者密码");
                    doc.setAllSecurityToBeRemoved(true);break;
                default: throw new ApiError(400,"未知工具");
            }
            try(var result=new ByteArrayOutputStream()){doc.save(result);return out(result.toByteArray(),"pdf");}
        } catch(InvalidPasswordException e){throw new ApiError(400,"PDF密码错误或需要密码");}
    }
    private Output out(byte[] bytes,String ext) {
        return new Output(bytes,ext,switch(ext){case "pdf"->"application/pdf";case "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";default->"application/zip";});
    }
    private List<Integer> pageSelection(String value,int count,boolean allowAll) {
        if(value.isBlank()){if(!allowAll)throw new ApiError(400,"请输入页码，例如1-3,5");var all=new ArrayList<Integer>();for(int i=0;i<count;i++)all.add(i);return all;}
        if(value.length()>1000)throw new ApiError(400,"页码范围过长");
        var pages=new ArrayList<Integer>();var seen=new HashSet<Integer>();
        try {for(String group:value.split(",",-1)){String[] ends=group.trim().split("-",-1);if(ends.length>2)throw new NumberFormatException();int first=Integer.parseInt(ends[0]),last=ends.length==2?Integer.parseInt(ends[1]):first;if(first<1||last<first||last>count)throw new NumberFormatException();for(int i=first;i<=last;i++){if(!seen.add(i-1))throw new ApiError(400,"页码不能重复，请检查输入");pages.add(i-1);}}}
        catch(NumberFormatException e){throw new ApiError(400,"页码格式为1-3,5，且不能超出文件总页数");}
        return pages;
    }
    private Output word(PDDocument source) throws IOException {
        try(var doc=new XWPFDocument();var result=new ByteArrayOutputStream()) {
            var stripper=new PDFTextStripper();stripper.setSortByPosition(true);int characters=0;
            for(int page=1;page<=source.getNumberOfPages();page++) {
                stripper.setStartPage(page);stripper.setEndPage(page);String text=stripper.getText(source);characters+=text.trim().length();
                for(String line:text.split("\\R")){var p=doc.createParagraph();var run=p.createRun();run.setFontFamily("Arial");run.setFontSize(11);run.setText(line);}
                if(page<source.getNumberOfPages())doc.createParagraph().setPageBreak(true);
            }
            if(characters==0)throw new ApiError(400,"此PDF没有可提取文字，扫描件暂不支持转Word");
            doc.write(result);return out(result.toByteArray(),"docx");
        }
    }
    private Output split(PDDocument source,String ranges) throws IOException {
        List<int[]> groups=new ArrayList<>();int n=source.getNumberOfPages();
        if(ranges.isBlank()) { for(int i=1;i<=n;i++)groups.add(new int[]{i,i}); }
        else {
            if(ranges.length()>500)throw new ApiError(400,"页码范围过长");
            try {for(String range:ranges.split(",",-1)){String[] parts=range.trim().split("-",-1);if(parts.length>2)throw new NumberFormatException();int a=Integer.parseInt(parts[0]),b=parts.length==2?Integer.parseInt(parts[1]):a;if(a<1||b<a||b>n)throw new NumberFormatException();groups.add(new int[]{a,b});}}
            catch(NumberFormatException e){throw new ApiError(400,"页码格式示例：1-3,5,8-10，不能超出总页数");}
        }
        if(groups.size()>200)throw new ApiError(400,"最多拆分200个文件");
        try(var result=new ByteArrayOutputStream();var zip=new ZipOutputStream(result)) {
            int index=0;for(int[] group:groups)try(var part=new PDDocument();var bytes=new ByteArrayOutputStream()){
                for(int i=group[0];i<=group[1];i++)part.importPage(source.getPage(i-1)).setResources(source.getPage(i-1).getResources());part.save(bytes);
                zip.putNextEntry(new ZipEntry("part-"+(++index)+".pdf"));zip.write(bytes.toByteArray());zip.closeEntry();
            }zip.finish();return out(result.toByteArray(),"zip");
        }
    }
    private Output images(PDDocument doc,String format) throws IOException {
        if(!Set.of("jpg","png").contains(format))throw new ApiError(400,"图片格式仅支持JPG或PNG");
        if(doc.getNumberOfPages()>50)throw new ApiError(400,"转图片最多支持50页，请先拆分");
        var renderer=new PDFRenderer(doc);
        try(var result=new ByteArrayOutputStream();var zip=new ZipOutputStream(result)) {
            for(int i=0;i<doc.getNumberOfPages();i++) {
                var box=doc.getPage(i).getCropBox();if(box.getWidth()*box.getHeight()*4>16_000_000)throw new ApiError(400,"页面尺寸过大");
                var image=renderer.renderImageWithDPI(i,144);zip.putNextEntry(new ZipEntry("page-"+(i+1)+"."+format));ImageIO.write(image,format,zip);zip.closeEntry();image.flush();
                if(result.size()>80_000_000)throw new ApiError(400,"图片输出过大，请拆分后转换");
            }zip.finish();return out(result.toByteArray(),"zip");
        }
    }
    private void watermark(PDDocument doc,String text) throws IOException {
        if(text.isBlank()||text.length()>40)throw new ApiError(400,"水印文字需为1至40个字符");
        PDFont font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        if(!text.chars().allMatch(c->c>=32&&c<127)) {
            Path p=Path.of(fontPath.isBlank()?"/usr/share/fonts/truetype/wqy/wqy-microhei.ttc":fontPath);
            if(!Files.isRegularFile(p))throw new ApiError(503,"中文水印字体未安装，请配置PDF_FONT");
            if(p.toString().endsWith(".ttc")) {
                var collection=new org.apache.fontbox.ttf.TrueTypeCollection(p.toFile());
                var ttf=collection.getFontByName("WenQuanYiMicroHei");
                if(ttf==null){collection.close();throw new ApiError(503,"中文水印字体不可用");}
                font=PDType0Font.load(doc,ttf,true);
                doc.registerTrueTypeFontForClosing(ttf);
            } else font=PDType0Font.load(doc,p.toFile());
        }
        for(PDPage page:doc.getPages())try(var stream=new PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true)){
            var state=new PDExtendedGraphicsState();state.setNonStrokingAlphaConstant(0.18f);stream.setGraphicsStateParameters(state);stream.setNonStrokingColor(Color.GRAY);
            var box=page.getCropBox();stream.beginText();float size=Math.min(38,box.getWidth()*0.65f/(font.getStringWidth(text)/1000));stream.setFont(font,size);stream.setTextMatrix(Matrix.getRotateInstance(Math.PI/6,box.getLowerLeftX()+box.getWidth()*0.18f,box.getLowerLeftY()+box.getHeight()*0.45f));stream.showText(text);stream.endText();
        }
    }
    private void imageWatermark(PDDocument doc,byte[] bytes)throws IOException {
        var image=org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc,pdf.readImage(bytes));
        for(PDPage page:doc.getPages())try(var stream=new PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true)){
            var box=page.getCropBox();float scale=Math.min(box.getWidth()*0.4f/image.getWidth(),box.getHeight()*0.4f/image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;
            var state=new PDExtendedGraphicsState();state.setNonStrokingAlphaConstant(0.18f);stream.setGraphicsStateParameters(state);stream.drawImage(image,box.getLowerLeftX()+(box.getWidth()-w)/2,box.getLowerLeftY()+(box.getHeight()-h)/2,w,h);
        }
    }
    private Output office(byte[] input,String name,String tool) throws Exception {
        Set<String> allowed=switch(tool){case "excel-pdf"->Set.of("xls","xlsx");case "ppt-pdf"->Set.of("ppt","pptx");default->Set.of("doc","docx");};
        String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase();
        if(!allowed.contains(ext))throw new ApiError(400,"请选择"+String.join("或",allowed)+"文件");
        Path dir=Files.createTempDirectory("dengke-office-");
        try {
            Path source=dir.resolve("input."+ext);Files.write(source,input);
            Process process;
            try {process=new ProcessBuilder(office,"-env:UserInstallation="+dir.resolve("profile").toUri(),"--headless","--convert-to","pdf","--outdir",dir.toString(),source.toString()).redirectErrorStream(true).redirectOutput(dir.resolve("convert.log").toFile()).start();}
            catch(IOException e){throw new ApiError(503,"Office转换组件未安装");}
            if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);throw new ApiError(504,"Office转换超时");}
            Path result=dir.resolve("input.pdf");if(process.exitValue()!=0||!Files.exists(result))throw new ApiError(400,"Office转换失败，请检查文档是否损坏或加密");
            return out(Files.readAllBytes(result),"pdf");
        } finally {try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
