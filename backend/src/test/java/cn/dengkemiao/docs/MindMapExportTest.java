package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MindMapExportTest {
    static final String CONTENT="{\"title\":\"供应商责任章程\",\"children\":[{\"title\":\"环境责任\",\"children\":[{\"title\":\"节约能源与减少排放 [A-P2]\"},{\"title\":\"采购可持续材料 [A-P3]\"}]},{\"title\":\"商业道德\",\"children\":[{\"title\":\"遵守法规，保护信息 [A-P4]\"}]}]}";
    @Test void exportsRealPngPdfAndEditableXmindWithMatchingTree()throws Exception {
        Path dir=Path.of("target/mindmap-preview");Files.createDirectories(dir);
        var png=MindMapExport.export(CONTENT,"png");assertEquals("image/png",png.getHeaders().getContentType().toString());
        var image=ImageIO.read(new ByteArrayInputStream(png.getBody()));assertNotNull(image);assertTrue(image.getWidth()>500);assertTrue(image.getHeight()>100);
        Files.write(dir.resolve("mindmap.png"),png.getBody());image.flush();
        var pdf=MindMapExport.export(CONTENT,"pdf");
        try(var doc=Loader.loadPDF(pdf.getBody())){assertEquals(1,doc.getNumberOfPages());assertTrue(doc.getPage(0).getResources().getXObjectNames().iterator().hasNext());}
        Files.write(dir.resolve("mindmap.pdf"),pdf.getBody());
        var archive=MindMapExport.export(CONTENT,"xmind");var entries=new HashMap<String,byte[]>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(archive.getBody()))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)entries.put(entry.getName(),zip.readAllBytes());}
        assertEquals(Set.of("content.json","manifest.json","metadata.json"),entries.keySet());
        var json=new ObjectMapper();var root=json.readTree(entries.get("content.json")).get(0).path("rootTopic");
        assertEquals("供应商责任章程",root.path("title").asText());assertEquals(2,root.path("children").path("attached").size());
        assertEquals("节约能源与减少排放 [A-P2]",root.path("children").path("attached").get(0).path("children").path("attached").get(0).path("title").asText());
        assertTrue(json.readTree(entries.get("manifest.json")).path("file-entries").has("content.json"));
        Files.write(dir.resolve("mindmap.xmind"),archive.getBody());
    }
    @Test void rejectsBrokenAndUnboundedTreesBeforeRendering()throws Exception {
        for(String bad:List.of("{}","not json","{\"title\":\"empty\"}","{\"title\":\"test\",\"children\":\"bad\"}"))assertThrows(ApiError.class,()->MindMapExport.parse(bad));
        var json=new ObjectMapper();var leaves=new ArrayList<>();for(int i=0;i<61;i++)leaves.add(Map.of("title","leaf"));
        String many=json.writeValueAsString(Map.of("title","root","children",leaves));assertThrows(ApiError.class,()->MindMapExport.parse(many));
        Object deep=Map.of("title","leaf");for(int i=0;i<6;i++)deep=Map.of("title","parent","children",List.of(deep));
        String nested=json.writeValueAsString(deep);assertThrows(ApiError.class,()->MindMapExport.parse(nested));
        assertThrows(ApiError.class,()->MindMapExport.export(CONTENT,"docx"));
    }
    @Test void wideAndLongChineseBranchesRenderWithoutCropping()throws Exception {
        var children=new ArrayList<>();for(int i=0;i<12;i++)children.add(Map.of("title","这是较长的分支说明，需要自动换行，完整保留所有节点内容。第"+i+"项 [A-P2]"));
        String content=new ObjectMapper().writeValueAsString(Map.of("title","测试主题","children",children));
        var image=MindMapExport.image(MindMapExport.parse(content));assertTrue(image.getHeight()>1000);assertTrue(image.getWidth()<2000);image.flush();
    }
}
