package cn.dengkemiao.docs;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.cert.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WechatTlsTest {
    @Test void preservesPlatformRejectionForEveryServerOverload() throws Exception {
        var platform = mock(X509ExtendedTrustManager.class);
        var failure = new CertificateException("untrusted");
        var chain = new X509Certificate[0];
        doThrow(failure).when(platform).checkServerTrusted(chain,"RSA");
        doThrow(failure).when(platform).checkServerTrusted(chain,"RSA",(Socket)null);
        doThrow(failure).when(platform).checkServerTrusted(chain,"RSA",(SSLEngine)null);
        var diagnostic = new WechatTls(platform);
        assertSame(failure,assertThrows(CertificateException.class,()->diagnostic.checkServerTrusted(chain,"RSA")));
        assertSame(failure,assertThrows(CertificateException.class,()->diagnostic.checkServerTrusted(chain,"RSA",(Socket)null)));
        assertSame(failure,assertThrows(CertificateException.class,()->diagnostic.checkServerTrusted(chain,"RSA",(SSLEngine)null)));
    }
    @Test void initializesWithSystemRoots() { assertNotNull(WechatTls.context()); }
}
