package cn.dengkemiao.docs;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.*;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

final class PdfExtras {
    static PdfTools.Output compare(List<byte[]> files)throws Exception {
        if(files.size()!=2)throw new ApiError(400,"请选择两个PDF进行比较");
        try(var a=Loader.loadPDF(files.get(0));var b=Loader.loadPDF(files.get(1));var bytes=new ByteArrayOutputStream();var zip=new ZipOutputStream(bytes)){
            if(a.isEncrypted()||b.isEncrypted())throw new ApiError(400,"请先解除PDF密码");
            int count=Math.max(a.getNumberOfPages(),b.getNumberOfPages());if(count<1||count>20)throw new ApiError(400,"比较最多支持20页，请先拆分");
            var ra=new PDFRenderer(a);var rb=new PDFRenderer(b);
            for(int i=0;i<count;i++){
                for(var doc:List.of(a,b))if(i<doc.getNumberOfPages()){var box=doc.getPage(i).getCropBox();if(box.getWidth()*box.getHeight()>4_000_000)throw new ApiError(400,"比较页面尺寸过大");}
                var left=i<a.getNumberOfPages()?ra.renderImageWithDPI(i,72):null;var right=i<b.getNumberOfPages()?rb.renderImageWithDPI(i,72):null;
                int w=Math.max(left==null?0:left.getWidth(),right==null?0:right.getWidth()),h=Math.max(left==null?0:left.getHeight(),right==null?0:right.getHeight());
                var image=new java.awt.image.BufferedImage(w*2,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<h;y++)for(int x=0;x<w;x++){int l=left!=null&&x<left.getWidth()&&y<left.getHeight()?left.getRGB(x,y):0xffffff;int r=right!=null&&x<right.getWidth()&&y<right.getHeight()?right.getRGB(x,y):0xffffff;image.setRGB(x,y,l);int distance=Math.abs((l>>16&255)-(r>>16&255))+Math.abs((l>>8&255)-(r>>8&255))+Math.abs((l&255)-(r&255));image.setRGB(x+w,y,distance>60?0xff5599:r);}
                zip.putNextEntry(new ZipEntry("compare-page-"+(i+1)+".png"));ImageIO.write(image,"png",zip);zip.closeEntry();image.flush();if(left!=null)left.flush();if(right!=null)right.flush();if(bytes.size()>80_000_000)throw new ApiError(400,"比较结果过大，请先拆分");
            }zip.finish();return new PdfTools.Output(bytes.toByteArray(),"zip","application/zip");
        }catch(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e){throw new ApiError(400,"请先解除PDF密码");}
    }
    static PdfTools.Output run(String tool,PDDocument doc,List<byte[]> files,Map<String,String> options)throws Exception {
        switch(tool){
            case "redact":
                if(doc.getNumberOfPages()>20)throw new ApiError(400,"永久遮盖最多支持20页，请先拆分");
                float rx=percent(options,"redactX",10),ry=percent(options,"redactY",10),rw=percent(options,"redactWidth",30),rh=percent(options,"redactHeight",10);
                if(rw<=0||rh<=0||rx+rw>100||ry+rh>100)throw new ApiError(400,"遮盖范围需在页面内，且宽高大于0");
                try(var result=new PDDocument();var bytes=new ByteArrayOutputStream()){
                    var renderer=new PDFRenderer(doc);
                    for(int i=0;i<doc.getNumberOfPages();i++){var box=doc.getPage(i).getCropBox();if(box.getWidth()*box.getHeight()*4>16_000_000)throw new ApiError(400,"页面尺寸过大");var bitmap=renderer.renderImageWithDPI(i,144);var graphics=bitmap.createGraphics();graphics.setColor(java.awt.Color.BLACK);graphics.fillRect((int)Math.floor(bitmap.getWidth()*rx/100),(int)Math.floor(bitmap.getHeight()*ry/100),(int)Math.ceil(bitmap.getWidth()*(rx+rw)/100)-(int)Math.floor(bitmap.getWidth()*rx/100),(int)Math.ceil(bitmap.getHeight()*(ry+rh)/100)-(int)Math.floor(bitmap.getHeight()*ry/100));graphics.dispose();var page=new PDPage(new PDRectangle(bitmap.getWidth()/2f,bitmap.getHeight()/2f));result.addPage(page);var image=LosslessFactory.createFromImage(result,bitmap);bitmap.flush();try(var stream=new PDPageContentStream(result,page)){stream.drawImage(image,0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}}
                    result.save(bytes);return new PdfTools.Output(bytes.toByteArray(),"pdf","application/pdf");
                }
            case "markdown":
                var stripper=new org.apache.pdfbox.text.PDFTextStripper();var markdown=new StringBuilder();
                for(int i=1;i<=doc.getNumberOfPages();i++){stripper.setStartPage(i);stripper.setEndPage(i);String text=stripper.getText(doc).strip();if(!text.isEmpty())markdown.append("## Page ").append(i).append("\n\n").append(text.replace("\\","\\\\").replace("*","\\*").replace("#","\\#").replace("<","&lt;").replace(">","&gt;")).append("\n\n");}
                if(markdown.isEmpty())throw new ApiError(400,"此PDF没有可提取文字，请先使用OCR");
                try(var bytes=new ByteArrayOutputStream();var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("document.md"));zip.write(markdown.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();zip.finish();return new PdfTools.Output(bytes.toByteArray(),"zip","application/zip");}
            case "crop":
                float left=percent(options,"left",5),right=percent(options,"right",5),top=percent(options,"top",5),bottom=percent(options,"bottom",5);
                if(left+right>=95||top+bottom>=95)throw new ApiError(400,"裁剪后需保留至少5%的页面宽高");
                for(PDPage page:doc.getPages()){if(page.getRotation()!=0)throw new ApiError(400,"裁剪暂不支持旋转页面，请先恢复页面方向");var box=page.getCropBox();page.setCropBox(new PDRectangle(box.getLowerLeftX()+box.getWidth()*left/100,box.getLowerLeftY()+box.getHeight()*bottom/100,box.getWidth()*(1-(left+right)/100),box.getHeight()*(1-(top+bottom)/100)));}break;
            case "sign":
                if(files.size()!=2)throw new ApiError(400,"请选择PDF和签名图片");
                int pageNumber;try{pageNumber=Integer.parseInt(options.getOrDefault("signPage","1"));}catch(NumberFormatException e){throw new ApiError(400,"签名页码需为整数");}
                if(pageNumber<1||pageNumber>doc.getNumberOfPages())throw new ApiError(400,"签名页码超出文件总页数");
                var page=doc.getPage(pageNumber-1);if(page.getRotation()!=0)throw new ApiError(400,"签名暂不支持旋转页面");
                float x=percent(options,"signX",60),y=percent(options,"signY",60),width=percent(options,"signWidth",25);
                if(width<=0)throw new ApiError(400,"签名宽度必须大于0");
                var bitmap=new PdfService().readImage(files.get(1));var image=LosslessFactory.createFromImage(doc,bitmap);bitmap.flush();var box=page.getCropBox();float w=box.getWidth()*width/100,h=w*image.getHeight()/image.getWidth();
                if(x+width>100||y*box.getHeight()/100+h>box.getHeight())throw new ApiError(400,"签名超出页面，请调整位置或宽度");
                try(var stream=new PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true)){stream.drawImage(image,box.getLowerLeftX()+box.getWidth()*x/100,box.getUpperRightY()-box.getHeight()*y/100-h,w,h);}break;
            case "extract-images":
                try(var bytes=new ByteArrayOutputStream();var zip=new ZipOutputStream(bytes)){int[] count={0};var seen=Collections.newSetFromMap(new IdentityHashMap<org.apache.pdfbox.cos.COSBase,Boolean>());for(var p:doc.getPages())extract(p.getResources(),zip,bytes,count,seen,0);if(count[0]==0)throw new ApiError(400,"此PDF没有可提取的嵌入图片");zip.finish();return new PdfTools.Output(bytes.toByteArray(),"zip","application/zip");}
            case "ocr": return ocr(doc);
            case "repair": break;
            default:throw new ApiError(400,"未知工具");
        }
        try(var out=new ByteArrayOutputStream()){doc.save(out);return new PdfTools.Output(out.toByteArray(),"pdf","application/pdf");}
    }
    private static float percent(Map<String,String> options,String key,float fallback){try{float value=Float.parseFloat(options.getOrDefault(key,Float.toString(fallback)));if(!Float.isFinite(value)||value<0||value>100)throw new NumberFormatException();return value;}catch(NumberFormatException e){throw new ApiError(400,"位置和边距需为0至100之间的百分比");}}
    private static void extract(PDResources resources,ZipOutputStream zip,ByteArrayOutputStream bytes,int[] count,Set<org.apache.pdfbox.cos.COSBase> seen,int depth)throws IOException {
        if(resources==null)return;if(depth>20)throw new ApiError(400,"PDF图片嵌套过深");
        for(var name:resources.getXObjectNames()){var object=resources.getXObject(name);if(object==null||!seen.add(object.getCOSObject()))continue;
            if(object instanceof PDImageXObject image){if((long)image.getWidth()*image.getHeight()>25_000_000)throw new ApiError(400,"嵌入图片尺寸过大");if(++count[0]>200)throw new ApiError(400,"最多提取200张图片，请先拆分文件");zip.putNextEntry(new ZipEntry("image-"+count[0]+".png"));var bitmap=image.getImage();ImageIO.write(bitmap,"png",zip);bitmap.flush();zip.closeEntry();if(bytes.size()>80_000_000)throw new ApiError(400,"图片输出过大，请先拆分文件");}
            else if(object instanceof PDFormXObject form)extract(form.getResources(),zip,bytes,count,seen,depth+1);
        }
    }
    private static PdfTools.Output ocr(PDDocument source)throws Exception {
        if(source.getNumberOfPages()>20)throw new ApiError(400,"OCR最多支持20页，请先拆分");
        Path dir=Files.createTempDirectory("dengke-ocr-");long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
        try(var target=new PDDocument();var output=new ByteArrayOutputStream()){
            var renderer=new PDFRenderer(source);
            for(int i=0;i<source.getNumberOfPages();i++){
                var box=source.getPage(i).getCropBox();if(box.getWidth()*box.getHeight()*4>16_000_000)throw new ApiError(400,"页面尺寸过大，无法OCR");
                var bitmap=renderer.renderImageWithDPI(i,144);Path input=dir.resolve("page.png"),base=dir.resolve("recognized");ImageIO.write(bitmap,"png",input.toFile());bitmap.flush();
                Process process;try{process=new ProcessBuilder("tesseract",input.toString(),base.toString(),"-l","chi_sim+eng","--dpi","144","pdf").redirectErrorStream(true).redirectOutput(dir.resolve("ocr.log").toFile()).start();}catch(IOException e){throw new ApiError(503,"OCR组件未安装，请更新服务器镜像");}
                long remaining=Math.max(0,deadline-System.nanoTime());if(!process.waitFor(remaining,TimeUnit.NANOSECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);throw new ApiError(504,"OCR处理超时，请减少页数后重试");}
                Path pdf=dir.resolve("recognized.pdf");if(process.exitValue()!=0||!Files.isRegularFile(pdf))throw new ApiError(400,"OCR识别失败，请检查文件清晰度或语言组件");
                try(var recognized=Loader.loadPDF(Files.readAllBytes(pdf))){new org.apache.pdfbox.multipdf.PDFMergerUtility().appendDocument(target,recognized);}Files.delete(pdf);
            }
            target.save(output);return new PdfTools.Output(output.toByteArray(),"pdf","application/pdf");
        }finally{try(var paths=Files.walk(dir)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
