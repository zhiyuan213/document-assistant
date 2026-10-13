package cn.dengkemiao.docs;

import java.io.*;
import java.nio.file.*;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Storage {
    @Value("${app.storage-dir}") private String directory;
    @Value("${app.storage}") private String mode;
    @Value("${app.cloud-secret-id}") private String secretId;
    @Value("${app.cloud-secret-key}") private String secretKey;
    @Value("${app.cloud-region}") private String region;
    @Value("${app.cos-bucket}") private String bucket;
    private Path path(String key) {
        if (!key.matches("[a-f0-9-]+\\.(pdf|txt|doc|docx|xls|xlsx|ppt|pptx|zip)")) throw new ApiError(400, "无效文件标识");
        return Path.of(directory).toAbsolutePath().resolve(key);
    }
    private COSClient client() { return new COSClient(new BasicCOSCredentials(secretId, secretKey), new ClientConfig(new Region(region))); }
    public void put(String key, byte[] bytes, String contentType) throws IOException {
        if (mode.equals("cos")) {
            COSClient client = client();
            try {
                ObjectMetadata meta = new ObjectMetadata(); meta.setContentLength(bytes.length); meta.setContentType(contentType);
                client.putObject(bucket, key, new ByteArrayInputStream(bytes), meta);
            } finally { client.shutdown(); }
        } else { Files.createDirectories(path(key).getParent()); Files.write(path(key), bytes); }
    }
    public byte[] get(String key) throws IOException {
        try(InputStream input=open(key)){return input.readAllBytes();}
    }
    public InputStream open(String key) throws IOException {
        if (mode.equals("cos")) {
            COSClient client = client();
            try {
                COSObject object=client.getObject(bucket,key);
                return new FilterInputStream(object.getObjectContent()) {
                    @Override public void close() throws IOException { try{super.close();}finally{try{object.close();}finally{client.shutdown();}} }
                };
            } catch(Exception e){client.shutdown();throw e;}
        }
        return Files.newInputStream(path(key));
    }
    public void delete(String key) throws IOException {
        if (mode.equals("cos")) { COSClient client = client(); try { client.deleteObject(bucket, key); } finally { client.shutdown(); } }
        else Files.deleteIfExists(path(key));
    }
}
