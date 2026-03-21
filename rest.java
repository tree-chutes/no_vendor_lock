package co5.demo;

import io.netty.handler.ssl.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

public class Rest {
    byte[] bais = null;
    byte[] bais1 = null;
    int docs = 100000;
    String baseUrl;

    public static void main(String[] args) {
        int threads = 4;
        ArrayList<ForkJoinTask<Boolean>> tasks = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            tasks.add(ForkJoinPool.commonPool().submit(() -> {
                Rest r = new Rest();
                try {
                    byte[] bais = Files.readAllBytes(Path.of("aca.pdf"));
                    r.baseUrl = "https://" + args[0];

                    WebClient wc = r.fluxTls(r.baseUrl);
                    long start = System.currentTimeMillis();
                    for (int d = 0; d < r.docs; d++){
                        UUID id = UUID.randomUUID();
                        wc.post().uri(String.format("/single/%s", id.toString()))
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(BodyInserters.fromValue(bais))
                            .retrieve()
                            .onStatus(sp ->{
                                if (!sp.is2xxSuccessful()){
                                    System.out.println(String.format("%s -> %d", id.toString(), sp.value()));
                                }
                                return false;
                            }, resp -> Mono.empty()).bodyToMono(byte[].class).block();
                    }
                    System.out.println("Millis per doc" + (System.currentTimeMillis() - start) / r.docs);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return true;
            }));
        }
        for (ForkJoinTask<Boolean> t : tasks){
            t.join();
        }
    }

    private WebClient fluxTls(String baseUrl) throws Exception {
        SslContext sslContext = buildClientSslContext(loadCertificate(System.getenv("TLS_CERT")));

        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec.sslContext(sslContext)
                        .handlerConfigurator(h -> {
                            SSLEngine engine = h.engine();
                            SSLParameters sslParameters = engine.getSSLParameters();
                            sslParameters.setServerNames(Collections.singletonList(new SNIHostName("localhost")));
                            engine.setSSLParameters(sslParameters);
                        }))
                .protocol(HttpProtocol.H2, HttpProtocol.HTTP11)
                .keepAlive(true);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "valid")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024 * 1024 * 10)).build())
                .build();
    }

    public SslContext buildClientSslContext(X509Certificate cert) throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("alias", cert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return SslContextBuilder.forClient()
                .trustManager(tmf)
                .sslProvider(SslProvider.OPENSSL)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();
    }

    public X509Certificate loadCertificate(String pemContent) throws Exception {
        String cleanPem = pemContent
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", ""); // Remove all whitespace/newlines

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(cleanPem)));
    }
}
