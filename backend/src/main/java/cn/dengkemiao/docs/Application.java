package cn.dengkemiao.docs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class Application {
    public static void main(String[] args) {
        // Log only certificate settings, never environment variables or credentials.
        org.slf4j.LoggerFactory.getLogger(Application.class).info(
                "TLS configuration: systemJavaTrustStore={}, aiaIssuers={}",
                "/etc/ssl/certs/java/cacerts".equals(System.getProperty("javax.net.ssl.trustStore")),
                Boolean.getBoolean("com.sun.security.enableAIAcaIssuers"));
        SpringApplication.run(Application.class, args);
    }
}
