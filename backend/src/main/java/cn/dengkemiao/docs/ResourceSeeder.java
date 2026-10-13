package cn.dengkemiao.docs;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResourceSeeder implements ApplicationRunner {
    private final JdbcTemplate db;private final Storage storage;
    @Value("${app.seed-demo}") private boolean enabled;
    @Value("${app.dev-login}") private boolean development;
    public ResourceSeeder(JdbcTemplate db,Storage storage){this.db=db;this.storage=storage;}
    @Override public void run(ApplicationArguments arguments) throws Exception {
        if(!enabled||!development)return;
        add("graduate","study","考研复习资料整理清单（示例）","复习资料整理清单\n\n1. 按科目建立资料目录。\n2. 标注资料来源、版本与年份。\n3. 为笔记、练习和复习计划分别命名。\n4. 核对报名材料清晰度。\n5. 每周备份重要文件。","考研,资料整理,清单");
        add("civil","study","公考报名材料准备清单（示例）","报名材料准备清单\n\n身份证明、学历证明、个人照片、报考岗位要求的其他材料。\n请以所报考试的官方公告为准。\n文件提交前核对页序、大小和清晰度。","公考,报名,清单");
        add("teacher","notes","教师资格学习笔记模板（示例）","学习笔记模板\n\n日期：\n学习主题：\n关键概念：\n例子与理解：\n复习问题：\n下一次复习时间：","教资,笔记,模板");
        add("english","notes","英语词汇复习记录表（示例）","英语词汇复习记录\n\n单词：\n词性与释义：\n例句：\n首次学习日期：\n复习日期：\n掌握情况：","英语,词汇,笔记");
        add("college","courses","大学课程资料目录模板（示例）","课程资料目录\n\n课程名称：\n教材：\n授课笔记：\n作业：\n练习：\n参考资料来源：\n学期：","大学,课程,目录");
        add("career","experience","职业考试备考复盘表（示例）","备考复盘\n\n本周完成：\n遇到的问题：\n需要补充的知识：\n下一周计划：\n资料与参考来源：","职业考试,经验,复盘");
        add("templates","study","每周学习计划表（示例PDF）",null,"学习计划,模板,PDF");
        add("other","study","资料文件命名规范（示例）","文件命名建议\n\n科目_主题_版本_日期\n示例：英语_词汇笔记_v1_20261013.txt\n避免同名覆盖，保留来源说明，及时备份。","文件整理,命名,规范");
    }
    private void add(String category,String topic,String name,String text,String tags) throws Exception {
        if(db.queryForObject("SELECT COUNT(*) FROM resource_item WHERE name=? AND is_demo=TRUE",Integer.class,name)>0)return;
        String id=UUID.randomUUID().toString(),kind=text==null?"pdf":"txt",key=id+"."+kind;
        byte[] bytes=text==null?planner():text.getBytes(StandardCharsets.UTF_8);
        storage.put(key,bytes,kind.equals("pdf")?"application/pdf":"text/plain; charset=utf-8");
        String description="原创示例资料，可用于体验搜索、分类、收藏和下载。"+(text==null?"本文件为英文每周学习计划PDF模板。":"本文件为可复制编辑的文字模板。")+"示例不包含考试真题，正式资料将由内容维护方提供。";
        try { db.update("INSERT INTO resource_item(id,category_id,topic,name,description,kind,storage_key,size_bytes,tags,is_free,is_demo,published) VALUES (?,?,?,?,?,?,?,?,?,TRUE,TRUE,TRUE)",id,category,topic,name,description,kind,key,bytes.length,tags); }
        catch(Exception e){storage.delete(key);throw e;}
    }
    private byte[] planner() throws Exception {
        try(PDDocument doc=new PDDocument();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            PDPage page=new PDPage();doc.addPage(page);
            try(PDPageContentStream stream=new PDPageContentStream(doc,page)){
                stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),24);stream.newLineAtOffset(42,730);stream.showText("WEEKLY STUDY PLANNER");stream.endText();
                String[] rows={"Original sample template - Dengke Doc Assistant","Week: ____________________","Monday: __________________________________________","Tuesday: __________________________________________","Wednesday: _______________________________________","Thursday: _________________________________________","Friday: ___________________________________________","Saturday: _________________________________________","Sunday: ___________________________________________","Review and next steps: ______________________________"};
                stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.setLeading(43);stream.newLineAtOffset(42,675);
                for(String row:rows){stream.showText(row);stream.newLine();}stream.endText();
            }
            doc.save(output);return output.toByteArray();
        }
    }
}
