package cn.dengkemiao.docs;

import java.util.*;
import java.util.stream.Collectors;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OcrService {
    @Value("${app.cloud-secret-id}") private String id;
    @Value("${app.cloud-secret-key}") private String key;
    @Value("${app.cloud-region}") private String region;
    public String recognize(byte[] image) throws Exception {
        if (id.isBlank() || key.isBlank()) throw new ApiError(503,"OCR尚未配置，请设置腾讯云凭据");
        var request = new GeneralBasicOCRRequest(); request.setImageBase64(Base64.getEncoder().encodeToString(image));
        try {
            var response = new OcrClient(new Credential(id,key),region).GeneralBasicOCR(request);
            return Arrays.stream(response.getTextDetections()).map(TextDetection::getDetectedText).collect(Collectors.joining("\n"));
        } catch (com.tencentcloudapi.common.exception.TencentCloudSDKException e) { throw new ApiError(502,"云端OCR识别失败，请稍后重试"); }
    }
}
