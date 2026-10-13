package cn.dengkemiao.docs;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.Color;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.*;
import org.springframework.stereotype.Service;

@Service
public class PdfService {
    public byte[] images(List<byte[]> images, boolean landscape, boolean high) throws IOException {
        if (images.isEmpty() || images.size()>20) throw new ApiError(400,"请选择1至20张图片");
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (byte[] bytes : images) {
                BufferedImage source = readImage(bytes);
                if (source.getColorModel().hasAlpha()) {
                    BufferedImage rgb = new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_RGB);
                    var graphics = rgb.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0,0,rgb.getWidth(),rgb.getHeight()); graphics.drawImage(source,0,0,null); graphics.dispose(); source=rgb;
                }
                PDRectangle size = landscape ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()) : PDRectangle.A4;
                PDPage page = new PDPage(size); doc.addPage(page);
                PDImageXObject image = JPEGFactory.createFromImage(doc, source, high ? 0.95f : 0.75f);
                float ratio = Math.min((size.getWidth()-40)/image.getWidth(), (size.getHeight()-40)/image.getHeight());
                float width = image.getWidth()*ratio, height = image.getHeight()*ratio;
                try (PDPageContentStream stream = new PDPageContentStream(doc,page)) { stream.drawImage(image,(size.getWidth()-width)/2,(size.getHeight()-height)/2,width,height); }
            }
            doc.save(out); return out.toByteArray();
        }
    }
    public byte[] compress(byte[] original) throws IOException {
        try (PDDocument doc = load(original); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            validate(doc);
            long pixels=0;
            for (PDPage page : doc.getPages()) {
                var resources = page.getResources();
                for (var name : resources.getXObjectNames()) {
                    if (resources.getXObject(name) instanceof PDImageXObject image && !image.isStencil() && image.getSoftMask()==null && image.getMask()==null) {
                        pixels+=(long)image.getWidth()*image.getHeight();
                        if ((long)image.getWidth()*image.getHeight()>25_000_000 || pixels>100_000_000) throw new ApiError(400,"PDF图片过大，请拆分文件后处理");
                        BufferedImage bitmap = image.getImage();
                        PDImageXObject smaller = JPEGFactory.createFromImage(doc, bitmap, 0.65f);
                        if (smaller.getCOSObject().getLength()<image.getCOSObject().getLength()) resources.put(name,smaller);
                    }
                }
            }
            doc.save(out); byte[] result = out.toByteArray();
            return result.length<original.length ? result : original;
        }
    }
    public byte[] merge(List<byte[]> files) throws IOException {
        if (files.size()<2 || files.size()>10) throw new ApiError(400,"请选择2至10个PDF");
        try (PDDocument result = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (byte[] file : files) try (PDDocument source = load(file)) {
                validate(source);
                if (result.getNumberOfPages()+source.getNumberOfPages()>200) throw new ApiError(400,"合并最多支持200页");
                new PDFMergerUtility().appendDocument(result,source);
            }
            result.save(out); return out.toByteArray();
        }
    }
    private void validate(PDDocument doc) { if (doc.isEncrypted()) throw new ApiError(400,"请先解除PDF密码"); if (doc.getNumberOfPages()>200) throw new ApiError(400,"单个PDF最多支持200页"); }
    private PDDocument load(byte[] bytes) {
        try { return Loader.loadPDF(bytes); }
        catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) { throw new ApiError(400,"请先解除PDF密码"); }
        catch (IOException e) { throw new ApiError(400,"PDF文件无效或已损坏"); }
    }
    public BufferedImage readImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ApiError(400,"仅支持有效的JPG或PNG图片");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!List.of("JPEG","PNG").contains(reader.getFormatName().toUpperCase())) throw new ApiError(400,"仅支持JPG或PNG图片");
                if ((long)reader.getWidth(0)*reader.getHeight(0)>25_000_000) throw new ApiError(400,"图片像素过大，请缩小后上传");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
}
