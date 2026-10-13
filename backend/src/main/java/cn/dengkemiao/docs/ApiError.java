package cn.dengkemiao.docs;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

public class ApiError extends RuntimeException {
    final int status;
    public ApiError(int status, String message) { super(message); this.status = status; }
}

@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(ApiError.class)
    ResponseEntity<?> api(ApiError e) { return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage())); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> size(Exception e) { return ResponseEntity.status(413).body(Map.of("message", "单个文件不能超过20MB，总上传不能超过100MB")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> other(Exception e) {
        LoggerFactory.getLogger(ErrorHandler.class).error("Request failed", e);
        return ResponseEntity.internalServerError().body(Map.of("message", "处理失败，请确认文件有效后重试"));
    }
}
