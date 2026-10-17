package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.*;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PdfLayoutTranslationTest {
    final ObjectMapper json=new ObjectMapper();
    final PdfLayoutTranslation engine=new PdfLayoutTranslation(json);
    static byte[] sample()throws Exception {
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
            var page=new PDPage(PDRectangle.A4);doc.addPage(page);
            var bitmap=new BufferedImage(50,40,BufferedImage.TYPE_INT_RGB);var g=bitmap.createGraphics();g.setColor(Color.BLUE);g.fillRect(0,0,50,40);g.dispose();
            try(var stream=new PDPageContentStream(doc,page)) {
                stream.drawImage(LosslessFactory.createFromImage(doc,bitmap),420,500,50,40);
                stream.setStrokingColor(Color.GREEN);stream.moveTo(40,480);stream.lineTo(550,480);stream.stroke();
                stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.setNonStrokingColor(Color.RED);stream.newLineAtOffset(40,700);stream.showText("Bonjour tout le monde. Texte de test.");stream.endText();
                stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.setNonStrokingColor(Color.BLACK);stream.newLineAtOffset(40,650);stream.showText("Une seconde phrase pour le document.");stream.endText();
            }
            doc.save(out);return out.toByteArray();
        }
    }
    @Test void translatedPdfPreservesPageImageAndNonTextGraphics()throws Exception {
        var input=sample();var plan=engine.inspect(input,30,5,15000);
        assertEquals(1,plan.pages().size());assertFalse(plan.pages().getFirst().scanned());
        assertEquals(0xff0000,plan.pages().getFirst().lines().getFirst().color());
        var text=new HashMap<Integer,String>();for(var r:plan.regions())text.put(r.id(),r.id()==0?"Hello everyone. Test text.":"A second sentence for this document.");
        var result=engine.render(plan,text);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/translation-preview"));
        java.nio.file.Files.write(java.nio.file.Path.of("target/translation-preview/native.pdf"),result.pdf());
        try(var before=Loader.loadPDF(input);var after=Loader.loadPDF(result.pdf())) {
            assertEquals(before.getNumberOfPages(),after.getNumberOfPages());assertEquals(before.getPage(0).getMediaBox().toString(),after.getPage(0).getMediaBox().toString());
            String extracted=new PDFTextStripper().getText(after);assertTrue(extracted.contains("Hello everyone"));assertFalse(extracted.contains("Bonjour"));
            var a=new PDFRenderer(before).renderImage(0);var b=new PDFRenderer(after).renderImage(0);
            // Outside all original and translated text, pixels must be identical.
            for(int y=400;y<800;y++)for(int x=0;x<590;x++)assertEquals(a.getRGB(x,y),b.getRGB(x,y),"non-text pixel "+x+","+y);
            int red=0;for(int y=125;y<150;y++)for(int x=40;x<300;x++){int rgb=b.getRGB(x,y);if((rgb>>16&255)>180&&(rgb>>8&255)<100&&(rgb&255)<100)red++;}assertTrue(red>10);
        }
    }
    @Test void rejectsIncompleteOrDuplicateModelRegionsInsteadOfExportingPartialDocument()throws Exception {
        var plan=engine.inspect(sample(),30,5,15000);
        assertThrows(ApiError.class,()->engine.translate(plan,"English","",(s,u,c)->new DeepSeekService.Result("{\"translations\":[]}",10,5),p->{}));
        assertThrows(ApiError.class,()->engine.translate(plan,"English","",(s,u,c)->new DeepSeekService.Result("{\"translations\":[{\"id\":0,\"text\":\"A\"},{\"id\":0,\"text\":\"B\"}]}",10,5),p->{}));
    }
    @Test void repairsOnlyAmbiguousRegionsAndPreservesAlreadyValidatedText()throws Exception {
        var plan=engine.inspect(sample(),30,5,15000);assertEquals(2,plan.regions().size());
        var requests=new ArrayList<List<Integer>>();
        var output=engine.translate(plan,"English","",(system,user,cap)->{
            try {
                var rows=json.readTree(user.substring(user.lastIndexOf("\n[")+1));var ids=new ArrayList<Integer>();rows.forEach(row->ids.add(row.path("id").asInt()));requests.add(ids);
                if(requests.size()==1)return new DeepSeekService.Result("{\"translations\":[{\"id\":0,\"text\":\"Keep validated text.\"},{\"id\":1,\"text\":\"Wrong A\"},{\"id\":1,\"text\":\"Wrong B\"}]}",10,5);
                assertEquals(List.of(1),ids);assertTrue(system.contains("Never renumber"));
                return new DeepSeekService.Result("{\"translations\":[{\"id\":\"1\",\"text\":\"Repaired text.\"}]}",10,5);
            }catch(Exception e){throw new RuntimeException(e);}
        },p->{});
        assertEquals(2,requests.size());assertTrue(output.text().contains("Keep validated text."));assertTrue(output.text().contains("Repaired text."));assertFalse(output.text().contains("Wrong"));
    }
    @Test void malformedResponseIsRetriedAndRepeatedInvalidResponsesStopAtThreeAttempts()throws Exception {
        var plan=engine.inspect(sample(),30,5,15000);var count=new java.util.concurrent.atomic.AtomicInteger();
        var output=engine.translate(plan,"English","",(system,user,cap)->{
            if(count.incrementAndGet()==1)return new DeepSeekService.Result("not JSON",10,5);
            return new DeepSeekService.Result("{\"translations\":[{\"id\":0,\"text\":\"First.\"},{\"id\":1,\"text\":\"Second.\"}]}",10,5);
        },p->{});
        assertEquals(2,count.get());assertNotNull(output.pdf());
        count.set(0);assertThrows(ApiError.class,()->engine.translate(plan,"English","",(system,user,cap)->{count.incrementAndGet();return new DeepSeekService.Result("{\"translations\":[]}",10,5);},p->{}));
        assertEquals(3,count.get());
    }
    @Test void usesAvailableWhitespaceBeyondThreeExtraLinesWithoutAddingPages()throws Exception {
        byte[] input;
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            var page=new PDPage(PDRectangle.A4);doc.addPage(page);
            try(var stream=new PDPageContentStream(doc,page)){
                stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                stream.newLineAtOffset(40,740);stream.showText("Source paragraph.");stream.endText();
            }
            doc.save(out);input=out.toByteArray();
        }
        var plan=engine.inspect(input,30,5,15000);String translated="Long translated paragraph with complete details. ".repeat(30);
        var output=engine.render(plan,Map.of(0,translated));assertEquals("[Page 1]\n"+translated+"\n\n",output.text());
        try(var doc=Loader.loadPDF(output.pdf())){assertEquals(1,doc.getNumberOfPages());assertTrue(new PDFTextStripper().getText(doc).contains("complete details"));}
    }
    @Test void aCenteredCoverHeadingCanExpandWithoutMovingItsBaselineOrAddingAPage()throws Exception {
        byte[] input;try(var d=new PDDocument();var out=new ByteArrayOutputStream()) {
            var p=new PDPage(PDRectangle.A4);d.addPage(p);
            try(var s=new PDPageContentStream(d,p)){s.beginText();s.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),24);s.newLineAtOffset(240,570);s.showText("Charte");s.endText();}
            d.save(out);input=out.toByteArray();
        }
        var plan=engine.inspect(input,30,5,15000);var result=engine.render(plan,Map.of(0,"Responsible Purchasing Policy"));
        try(var d=Loader.loadPDF(result.pdf())){assertEquals(1,d.getNumberOfPages());assertTrue(new PDFTextStripper().getText(d).contains("Responsible Purchasing Policy"));}
    }
    @Test void shortAddressUsesBlankSpaceButStopsAtTheNextColumn()throws Exception {
        var input=sample();var address=new PdfLayoutTranslation.Line("Rue",72,490,30,15,0,"Helvetica",false);
        var neighbor=new PdfLayoutTranslation.Line("Other column",312,490,150,15,0,"Helvetica",false);
        var layout=new PdfLayoutTranslation.Page(PDRectangle.A4.getWidth(),PDRectangle.A4.getHeight(),false,List.of(address,neighbor));
        var plan=new PdfLayoutTranslation.Plan(input,List.of(layout),List.of(new PdfLayoutTranslation.Region(0,0,List.of(address),"Rue")),3,List.of());
        var result=engine.render(plan,Map.of(0,"1 Jean-Henri Fabre Street"));
        try(var d=Loader.loadPDF(result.pdf())){assertTrue(new PDFTextStripper().getText(d).contains("1 Jean-Henri Fabre Street"));}
        assertThrows(ApiError.class,()->engine.render(plan,Map.of(0,"A very long address ".repeat(150))));
    }
    @Test void longWordWrapRetainsEveryCharacterAcrossOriginalLines() {
        var line=new PdfLayoutTranslation.Line("x",0,20,5,10,0,"Helvetica",false);
        String text="https://example.com/longpath";
        var rows=PdfLayoutTranslation.wrap(text,Collections.nCopies(6,line),5,1,String::length);
        assertNotNull(rows);assertEquals(text,String.join("",rows));
    }
    @Test void multilineParagraphCanUseItsClearContainerWithoutChangingBaselines()throws Exception {
        var input=sample();var a=new PdfLayoutTranslation.Line("Tiny",40,200,30,12,0,"Helvetica",false);
        var b=new PdfLayoutTranslation.Line("text",40,218,25,12,0,"Helvetica",false);
        var page=new PdfLayoutTranslation.Page(PDRectangle.A4.getWidth(),PDRectangle.A4.getHeight(),false,List.of(a,b));
        var plan=new PdfLayoutTranslation.Plan(input,List.of(page),List.of(new PdfLayoutTranslation.Region(0,0,List.of(a,b),"Tiny text")),9,List.of());
        var output=engine.render(plan,Map.of(0,"This complete translated paragraph can use the empty space on its original lines without adding pages."));
        try(var doc=Loader.loadPDF(output.pdf())){assertEquals(1,doc.getNumberOfPages());assertTrue(new PDFTextStripper().getText(doc).contains("This complete translated paragraph"));}
    }
    @Test void stringRegionIdsAreAcceptedButAmbiguousOrOutOfRangeIdsAreNot()throws Exception {
        assertEquals(12,PdfLayoutTranslation.regionId(json.readTree("\"12\"")));
        for(String invalid:List.of("1.5","-1","2147483648","\"01\"","\"1.0\"","\"2147483648\"","null"))assertNull(PdfLayoutTranslation.regionId(json.readTree(invalid)));
        var plan=engine.inspect(sample(),30,5,15000);
        var output=engine.translate(plan,"English","",(system,user,cap)->{
            try{
                var source=json.readTree(user.substring(user.lastIndexOf("\n[")+1));var rows=new ArrayList<Map<String,String>>();
                source.forEach(row->rows.add(Map.of("id",row.path("id").asText(),"text","Translated sentence.")));
                return new DeepSeekService.Result(json.writeValueAsString(Map.of("translations",rows)),10,10);
            }catch(Exception e){throw new RuntimeException(e);}
        },p->{});
        assertNotNull(output.pdf());
    }
    @Test void adjacentFontFragmentsFitAsOneLineWithoutLosingText()throws Exception {
        var a=new PdfLayoutTranslation.Line("A",40,200,90,11,0xff0000,"Helvetica",true);
        var b=new PdfLayoutTranslation.Line("...",130,200,10,11,0xff0000,"Times",false);
        var c=new PdfLayoutTranslation.Line("B",140,200,40,11,0xff0000,"Helvetica",true);
        var lines=List.of(a,b,c);var regions=new ArrayList<PdfLayoutTranslation.Region>();
        for(int i=0;i<3;i++)regions.add(new PdfLayoutTranslation.Region(i,0,List.of(lines.get(i)),lines.get(i).text()));
        var plan=new PdfLayoutTranslation.Plan(sample(),List.of(new PdfLayoutTranslation.Page(595,842,false,lines)),regions,5,List.of());
        var result=engine.render(plan,Map.of(0,"Promote diversity and inclusion",1,"...",2,"Respect everyone"));
        try(var doc=Loader.loadPDF(result.pdf())){String text=new PDFTextStripper().getText(doc);assertTrue(text.contains("Promote diversity and inclusion"));assertTrue(text.contains("Respect everyone"));}
    }
    @Test void extraTranslationLinesStopBeforeTheFollowingHeading()throws Exception {
        var body=new PdfLayoutTranslation.Line("Source",40,180,510,11,0,"Helvetica",false);
        var heading=new PdfLayoutTranslation.Line("Next",40,208,200,11,0xff0000,"Helvetica",true);
        var plan=new PdfLayoutTranslation.Plan(sample(),List.of(new PdfLayoutTranslation.Page(595,842,false,List.of(body,heading))),List.of(new PdfLayoutTranslation.Region(0,0,List.of(body),"Source"),new PdfLayoutTranslation.Region(1,0,List.of(heading),"Next")),10,List.of());
        var translated="This chapter describes the integrity principles that suppliers should uphold in their operations, with the aim of maintaining the relationship of mutual trust established between suppliers and the parks.";
        var result=engine.render(plan,Map.of(0,translated,1,"Next"));
        try(var doc=Loader.loadPDF(result.pdf())){assertEquals(1,doc.getNumberOfPages());assertTrue(new PDFTextStripper().getText(doc).contains("Next"));}
        assertThrows(ApiError.class,()->engine.render(plan,Map.of(0,translated.repeat(8),1,"Next")));
    }
    @Test void translatesAllBatchesAndQuotesEachIdInJson()throws Exception {
        var plan=engine.inspect(sample(),30,5,15000);var calls=new ArrayList<Integer>();
        var output=engine.translate(plan,"English","Keep names",(system,user,cap)->{
            try {
                var source=json.readTree(user.substring(user.lastIndexOf("\n[")+1));var translations=new ArrayList<Map<String,Object>>();
                source.forEach(row->translations.add(Map.of("id",row.path("id").asInt(),"text","Translated sentence.")));
                calls.add(cap);return new DeepSeekService.Result(json.writeValueAsString(Map.of("translations",translations)),20,10);
            }catch(Exception e){throw new RuntimeException(e);}
        },p->{});
        assertFalse(calls.isEmpty());assertTrue(output.text().contains("[Page 1]"));assertTrue(output.pdf().length>100);
        assertTrue(engine.reserve(plan,"English","")>4000);
    }
    @Test void overflowFailsWithoutDroppingTextAndRotatedPagesKeepTheirRotation()throws Exception {
        var plan=engine.inspect(sample(),30,5,15000);var map=new HashMap<Integer,String>();plan.regions().forEach(r->map.put(r.id(),"Very long translated content ".repeat(200)));
        var error=assertThrows(ApiError.class,()->engine.render(plan,map));assertEquals(422,error.status);
        byte[] rotated;try(var doc=Loader.loadPDF(sample());var out=new ByteArrayOutputStream()){doc.getPage(0).setRotation(90);doc.save(out);rotated=out.toByteArray();}
        var rotatedPlan=engine.inspect(rotated,30,5,15000);var translated=new HashMap<Integer,String>();rotatedPlan.regions().forEach(r->translated.put(r.id(),"English sentence."));
        try(var doc=Loader.loadPDF(engine.render(rotatedPlan,translated).pdf())){assertEquals(90,doc.getPage(0).getRotation());}
    }
    @Test void layoutOverflowExportsCompletePaginatedPdfWithOriginalArtworkReference()throws Exception {
        var plan=engine.inspect(sample(),30,5,15000);
        var output=engine.translate(plan,"English","",(system,user,cap)->{
            try {
                var source=json.readTree(user.substring(user.lastIndexOf("\n[")+1));var rows=new ArrayList<Map<String,Object>>();
                source.forEach(row->rows.add(Map.of("id",row.path("id").asInt(),"text","Long translated passage ".repeat(100))));
                return new DeepSeekService.Result(json.writeValueAsString(Map.of("translations",rows)),100,1000);
            }catch(Exception e){throw new RuntimeException(e);}
        },p->{});
        assertNotNull(output.pdf());assertFalse(output.layoutPreserved());assertTrue(output.text().contains("Long translated passage"));
        try(var doc=Loader.loadPDF(output.pdf());var original=Loader.loadPDF(plan.input())){
            assertTrue(doc.getNumberOfPages()>original.getNumberOfPages());
            String text=new PDFTextStripper().getText(doc).replaceAll("\\s+"," ");assertEquals(200,text.split("Long translated passage",-1).length-1);
            var last=doc.getPage(doc.getNumberOfPages()-1);assertTrue(last.getResources().getXObjectNames().iterator().hasNext());
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/translation-preview"));
        java.nio.file.Files.write(java.nio.file.Path.of("target/translation-preview/reflow.pdf"),output.pdf());
    }
    @Test void japaneseReflowProducesVisiblePaginatedPdf()throws Exception {
        byte[] bytes=engine.flowingPdf("[Page 1]\n"+"日本語の翻訳文です。内容を省略せず、改行して保存します。 ".repeat(300));
        try(var doc=Loader.loadPDF(bytes)){
            assertTrue(doc.getNumberOfPages()>1);
            var bitmap=new PDFRenderer(doc).renderImage(0);int ink=0;
            for(int y=20;y<bitmap.getHeight()-20;y++)for(int x=20;x<bitmap.getWidth()-20;x++)if((bitmap.getRGB(x,y)&0xffffff)<0xdddddd)ink++;
            assertTrue(ink>1000);
        }
    }
    @Test void ocrCoordinatesAndBackgroundReconstructionAreLocal() {
        var image=new BufferedImage(400,200,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(new Color(240,248,255));g.fillRect(0,0,400,200);g.setColor(Color.RED);g.fillRect(20,30,80,20);g.dispose();
        var lines=PdfLayoutTranslation.parseTsv(List.of("5\t1\t1\t1\t1\t1\t20\t30\t80\t20\t95\tBonjour"),image,200,100);
        assertEquals(10,lines.getFirst().x());assertEquals(25,lines.getFirst().y());assertEquals(0xff0000,lines.getFirst().color());
        int original=image.getRGB(300,150);PdfLayoutTranslation.eraseScannedText(image,new PdfLayoutTranslation.Page(200,100,true,lines));assertEquals(original,image.getRGB(300,150));
        assertNotEquals(0xff0000,image.getRGB(40,40)&0xffffff);
    }
    @Test void scannedPageKeepsSizeAndArtworkOutsideTheTextArea()throws Exception {
        var image=new BufferedImage(600,400,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,600,400);g.setColor(Color.BLUE);g.fillRect(400,200,100,80);g.setColor(Color.BLACK);g.setFont(new java.awt.Font("SansSerif",0,24));g.drawString("BONJOUR",40,80);g.dispose();
        byte[] input;try(var d=new PDDocument();var bytes=new ByteArrayOutputStream()){var p=new PDPage(new PDRectangle(300,200));d.addPage(p);try(var s=new PDPageContentStream(d,p)){s.drawImage(LosslessFactory.createFromImage(d,image),0,0,300,200);}d.save(bytes);input=bytes.toByteArray();}
        var line=new PdfLayoutTranslation.Line("BONJOUR",20,40,120,14,0xff0000,"OCR",false);
        var page=new PdfLayoutTranslation.Page(300,200,true,List.of(line));
        var plan=new PdfLayoutTranslation.Plan(input,List.of(page),List.of(new PdfLayoutTranslation.Region(0,0,List.of(line),line.text())),7,List.of("OCR reconstruction"));
        var output=engine.render(plan,Map.of(0,"Hello"));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/translation-preview"));
        java.nio.file.Files.write(java.nio.file.Path.of("target/translation-preview/scanned.pdf"),output.pdf());
        try(var d=Loader.loadPDF(output.pdf())){assertEquals(1,d.getNumberOfPages());assertEquals(300,d.getPage(0).getMediaBox().getWidth());var result=new PDFRenderer(d).renderImageWithDPI(0,144);assertEquals(image.getRGB(440,240),result.getRGB(440,240));assertTrue(new PDFTextStripper().getText(d).contains("Hello"));}
    }
    @Test void formXObjectTextIsReplacedWithoutRemovingTheFormImage()throws Exception {
        byte[] input;try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
            var p=new PDPage();doc.addPage(p);var form=new PDFormXObject(doc);form.setBBox(new PDRectangle(200,100));form.setResources(new PDResources());
            try(var s=new PDFormContentStream(form)){s.beginText();s.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);s.newLineAtOffset(10,50);s.showText("Bonjour dans un formulaire.");s.endText();}
            try(var s=new PDPageContentStream(doc,p)){s.drawForm(form);}doc.save(out);input=out.toByteArray();
        }
        var plan=engine.inspect(input,30,5,15000);var text=new HashMap<Integer,String>();plan.regions().forEach(r->text.put(r.id(),"Hello in a form."));
        try(var doc=Loader.loadPDF(engine.render(plan,text).pdf())){String value=new PDFTextStripper().getText(doc);assertTrue(value.contains("Hello"));assertFalse(value.contains("Bonjour"));}
    }
}
