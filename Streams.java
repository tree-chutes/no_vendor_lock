package co5.driver;

import co5.backflow.client.FluxMessage;
import io.netty.handler.ssl.*;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Streams {
    Sinks.Many<FluxMessage> sink;
    byte[] bais = null;
    byte[] bais1 = null;
    int threads = 1, docs = 100000;
    AtomicInteger counter = new AtomicInteger(threads * docs);
    String cert = "-----BEGIN CERTIFICATE-----MIII3TCCBMWgAwIBAgIGAZxszbOpMA0GCSqGSIb3DQEBCwUAMBQxEjAQBgNVBAMMCWxvY2FsaG9zdDAgFw0yNjAyMTcxODEyMjZaGA8yOTM4MTAwODAyMTIyNlowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIEIjANBgkqhkiG9w0BAQEFAAOCBA8AMIIECgKCBAEA36lCUj9/2YF1tBsKCZuPO5wTPcAyfM+oOWkgKNXZcFeSLW0hbh60/lPux0ykOSiIJqx3gOOGUNDCCOGyRK8mH+Bn/lfH+hTInFWTCKehpzzzqOZSIkp8okHFeWAeLj7fh6suj3Q9bI9KGt828zTNibkzASN4Ywnfm7jex9wp7B05+jCQ5TM4DaI/Vhm8BgrRERsjVwjeUheOJW7FDdSJ+p1gziEL5UxVZ6ivtsTBrhlsx3U5+QdNlXGb/yWrwOTi0b5nUzuD9XwJNnpxUlOd81d0gMW+vlRa1Gb4H7YtVZJolZ8HEx9Fv2VZ9XIlfQH+3VhHawWaNrfWBIcCyUZKnRPHfcp2pC+p4P/cRu9sjkfoWoBMos4EBW7OYh4H0hzwBP/LxOOYngPeOa1tiS2sKC3FCV7NqPW8LmOEFHQQmB2SYhPspWfKJzYSpx9fetgq773+ixXb1qRv/XJ1E/a5aHv+8RV/pwDaSjJWK8m2Ls04EgC567IlyYPObqJg7c+Q3r9O/McZrgQ5rYlD3CY9CWFONvyQ5AbdJb2wKn1fIqXKLRxW+QVUJPlf384sSgDxCoGC1Js3eblDt/H3OLw0b0gQnGd4+Oe1vB6VveFe5U8trW9S0+GTd3wWII6UJ9eJApAa9yMh0yvkNPtuF0i+xuD6FQo8I0gioUTh7n1qX+2Y/zh2phuv6JJNGFKFjBpMsjKp8CeBePckt/S3rLfR9wFtWEVImwkT4a54OkutlU3QLkpnAa8KAQmyTuzbEuo4fTzhEQ2Xqa93XjPh4jDdBtM2eXMa5PpXJtwfR5ocKY9mhOJmIVNDXJkTEweTefjERlsiwVCLiGOyRk2vTSGi5dzJ/BifvKFBvcS6IA5LnH1dissiFhPJV3i0j1alfSBxpXpSLJLxpatXi3wqozg/xZyFc5K67e1covkWYvxfNRLHaENLgptMCDngIZ87ndzTN0kI8zwuvLpO5OdB0typPci/j3QxLELgRUaToLxy9VeCkmjq44RhF5soLp/LKqAO3ov68XwnDdxDSvcJJSb45kiSm4XqfFQlCM1m6Tn8WsCgPqUbnHy3gl4EnjhRjQC73tbnCTokae52+osKaTqX9C7eC0YXLeT9ngvMAAKLK7DJwgOKQxNod0sp/m7Ss9/K651nwUU1Z/6NRr40mSjSrxwWz1YILO8AaDMmmAuVlxIGeSqtxPhvDiCrHMN9NztD/CcbrJNuZBw7zABdt2VJ2HSF2OkJra2fvw+kNz0fFesSdAy6HDQraM/LcLGOZntHqOqwZAN3bamku6YAd821bOg50fy5KVtXNdz6iRSjR34t7n6083MdFvqRYFqncv7oE8ZQoNVx6qbptjXIZOxrwQIDAQABozMwMTAXBgNVHREBAf8EDTALgglsb2NhbGhvc3QwFgYDVR0lAQH/BAwwCgYIKwYBBQUHAwEwDQYJKoZIhvcNAQELBQADggQBAN3+bwHGzu3yCkTUwP22KaVbRsHzJUKsBrCpR5ynR4hf3c9a0zPzt5KjfbA/JWKW+RevAoHvCANKdBB67GP7X3+0UDtup6+YMkTAbr+GFJ0MH42HomiTG31gbXx4UEosTZCtpAeAqrKal8Z64EWTJaHA0jnG4hlTpOyrDSwYkUc6jPIRIkvD1Nx+6bJKukQI5r3nXA7yEZDXAJT7THbtnX10hWcBIF7rD9ozXZssS71VNlFQfx4O3h/7TeTKLaP7LkWw7v1tICAiu6R+V43LM0I4p+Pkpogvdo/gCL5YjyxhZ7HZNyoyarTMMbzPtnK/ltZNWtId41wfFbVIN2Mowut3xOJknn5PA/+cpbxsCi1MWQPfe0apKTdQAhuQeBfYbSHYqqKxwiKbvi5WiR94BRtYyfZp9W2/nnQndvBngHxjAE9OJijA7ZW1K0Q5KkQZaV1xkDie5zi8BrOERxt0zYOhEGUOciqPVuE9DUM4CJi0GriuVKH4h8yGQsHYCpCsx4/1amtyrAlRMiHxOsJgbXzoNxjnjc7DV7zWAHrZtNhEtxm1FNbit6V78oq8GufVy3lQWwFQWc1HutBP94gNzs0evuMLlIJ+EWgmOVgl3aO4OPkXRtWVASpmAsxzo/nhg8X/1e8N3D2376KDNbWnqkiQMSvcbNFZygRU/2+TmRyvj6v7SE8KJ8NYKbxYmRRIAngnjwq5t9f4wFxmQG+DX9xv5WR9N2lJv63UNsj9mUIqJYEL5YAG8SRwhg6Xk83ytND65OOgB8j2zoF8v1/yitwUfpIKt998juX+nzPtarQ7ommPtuy8Zxtvo5qSdEe2bci6elgchx5pZbolIUGZyu0pD8zxrh+HvRFo3DoDygozapARIpwIQb+xaW8oTWtJzh2grKMPENcTqCoHm+dKYW51EDfHaqz3mEJzGjWA4hAWSvR8Fr+i1OcNKd4YU35hwOumAXIJ6+UQ1yIAZNUFq7TwkFIIUqcB7rnYRVr6DmZWyt2z9/f1IWP+7xTrs+9B/MuoUuUQi0GDqzJk2AEKjIkuVGnsEud01kIlRqv0EaVbVw1dfONLsTOpnyoM8J0h0XtqZAjs/PXaV9m8sT7woXlffwEzkVGoDLETn48pKUxJfOCktfOnnguW5iPRnVa4WiXpVsg3NzI74/bOFrJke9tHySNuvVVPey7ngUiR8+u7yYvWnDwxn6uVNtnTKUFKQEx0xY2TaGLUm0kgY6mEsGg9mX3us9Bt9yNSua0jek4xVMTgNeBtyzN0D01ZJpn/5mnC5F1vlIY4iRmJ4ZQD0l5c9IWcR+MeS7NLDCEqEjAeCeVjeP7vAHMfV/KrTl+6fAndagffvDxcqG5E3Ka4UL4=-----END CERTIFICATE-----";
    CO5Subscriber subs;
    Flux<FluxMessage> f;
    String baseUrl;

    public static void main(String[] args) {

        Streams s = new Streams();

        try {
            byte[] bais = Files.readAllBytes(Path.of("aca.pdf"));
            s.baseUrl = "https://localhost:8443"; //+ args[1];
            UUID fluxId = UUID.randomUUID();

            WebClient wc = s.fluxTls(s.baseUrl);
            s.sink = Sinks.many().unicast().onBackpressureBuffer();
            wc.post().uri("/stream")
                    .body(s.sink.asFlux().publishOn(Schedulers.newBoundedElastic(1, s.docs, "publisher")), FluxMessage.class)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().bodyToFlux(FluxMessage.class).subscribeOn(Schedulers.newBoundedElastic(1, s.docs, "subscriber")).subscribeWith(new CO5Subscriber(s.counter));
            Sinks.EmitResult r;
            boolean l = false;
            long start = System.currentTimeMillis();
            Map<String, String> pdf;
            Scannable scannable = Scannable.from(s.sink);
            for (int ii = 0; ii < s.docs; ii++) {
                pdf = new HashMap<>();
                synchronized (s.sink) {
                    if (ii % 2 == 0) {
                        pdf.put("stages", "LOAD_PDF,ADD_PAGE,SAVE_PDF");
                        pdf.put("message", "streams");
                        s.sink.tryEmitNext(new FluxMessage(UUID.randomUUID().toString(), bais, pdf, fluxId));
                    }
                    else{
                        pdf.put("stages", "LOAD_PDF,REMOVE_PAGE,SAVE_PDF");
                        s.sink.tryEmitNext(new FluxMessage(UUID.randomUUID().toString(), bais, pdf, fluxId));
                    }
                }
            }
            while (s.counter.get() > 0){
                Thread.sleep(30);
            }
            System.out.println("Millis per doc" + (System.currentTimeMillis() - start) / s.docs );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private WebClient fluxTls(String baseUrl) throws Exception {
        SslContext sslContext = buildClientSslContext(loadCertificate_1(cert));

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
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024 * 1024 * 100)).build())
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

    public X509Certificate loadCertificate_1(String pemContent) throws Exception {
        String cleanPem = pemContent
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", ""); // Remove all whitespace/newlines

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(cleanPem)));
    }

    public TrustManagerFactory loadCertificate(String pemContent) throws Exception {
        String cleanPem = pemContent
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", ""); // Remove all whitespace/newlines

        byte[] decoded = Base64.getDecoder().decode(cleanPem);

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        trustStore.setCertificateEntry("alias", (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decoded)));

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }


    private TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    try {
                        return new X509Certificate[]{loadCertificate_1(cert)};
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    System.out.println(certs[0].getNotBefore());
                }
            }
    };
}

class CO5Subscriber implements Subscriber<FluxMessage> {
    AtomicInteger counter;
    AtomicBoolean f = new AtomicBoolean();
    Subscription _Subscription;

    CO5Subscriber(AtomicInteger c){
        counter = c;
    }

    @Override
    public void onSubscribe(Subscription s) {
        _Subscription = s;
        _Subscription.request(1);
        System.out.println("Streams Subscribed on " + Thread.currentThread().getName());
    }

    @Override
    public void onNext(FluxMessage m) {
        if (counter.decrementAndGet() == 0) {
            _Subscription.cancel();
        } else {
            _Subscription.request(1);
        }
        if (counter.get() % 100 == 0) {
            System.out.println(String.format("%s count %d", Thread.currentThread().getName(), counter.get()));
        }
    }

    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
        f.set(true);
    }

    @Override
    public void onComplete() {
        f.set(true);
    }
}
