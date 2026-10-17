package cn.dengkemiao.docs;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import java.util.HexFormat;

/** Adds diagnostics to the platform trust manager without changing trust decisions. */
final class WechatTls extends X509ExtendedTrustManager {
    private final X509ExtendedTrustManager delegate;
    WechatTls(X509ExtendedTrustManager delegate) { this.delegate = delegate; }
    static SSLContext context() {
        try {
            var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (var manager : factory.getTrustManagers()) {
                if (manager instanceof X509ExtendedTrustManager trust) {
                    var context = SSLContext.getInstance("TLS");
                    context.init(null, new TrustManager[]{new WechatTls(trust)}, null);
                    org.slf4j.LoggerFactory.getLogger(WechatTls.class).info(
                            "WeChat TLS trusted root count={}", trust.getAcceptedIssuers().length);
                    return context;
                }
            }
            throw new IllegalStateException("No extended platform trust manager");
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Cannot initialize WeChat TLS", e); }
    }
    @FunctionalInterface private interface Check { void run() throws CertificateException; }
    private void checked(X509Certificate[] chain, Check check) throws CertificateException {
        try { check.run(); }
        catch (CertificateException failure) {
            var log = org.slf4j.LoggerFactory.getLogger(WechatTls.class);
            if (chain != null) for (int i=0; i<Math.min(chain.length, 8); i++) {
                var cert = chain[i];
                String fingerprint;
                try { fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())); }
                catch (GeneralSecurityException ignored) { fingerprint = "unavailable"; }
                log.warn("WeChat TLS rejected certificate index={} subject={} issuer={} validFrom={} validTo={} sha256={}",
                        i, cert.getSubjectX500Principal(), cert.getIssuerX500Principal(),
                        cert.getNotBefore().toInstant(), cert.getNotAfter().toInstant(), fingerprint);
            }
            throw failure;
        }
    }
    @Override public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
    @Override public void checkServerTrusted(X509Certificate[] c,String a) throws CertificateException { checked(c,()->delegate.checkServerTrusted(c,a)); }
    @Override public void checkServerTrusted(X509Certificate[] c,String a,Socket s) throws CertificateException { checked(c,()->delegate.checkServerTrusted(c,a,s)); }
    @Override public void checkServerTrusted(X509Certificate[] c,String a,SSLEngine e) throws CertificateException { checked(c,()->delegate.checkServerTrusted(c,a,e)); }
    @Override public void checkClientTrusted(X509Certificate[] c,String a) throws CertificateException { delegate.checkClientTrusted(c,a); }
    @Override public void checkClientTrusted(X509Certificate[] c,String a,Socket s) throws CertificateException { delegate.checkClientTrusted(c,a,s); }
    @Override public void checkClientTrusted(X509Certificate[] c,String a,SSLEngine e) throws CertificateException { delegate.checkClientTrusted(c,a,e); }
}
