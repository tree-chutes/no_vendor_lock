package co5.driver;

import co5.backflow.client.FluxMessageOuterClass;
import co5.backflow.client.FluxMessageOuterClass.FluxMessage;
import com.google.protobuf.UnsafeByteOperations;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.*;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Sinks;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Streams {
    String url;
    static int docs = Integer.valueOf(System.getenv("DOCS"));
    static int maxBatch = Integer.valueOf(System.getenv("MAX_BATCH"));;
    static ConcurrentSkipListSet<String> flying = new ConcurrentSkipListSet();
    static AtomicInteger counter = new AtomicInteger(docs);
    static AtomicInteger batch = new AtomicInteger(0);
    static AtomicBoolean subscribed = new AtomicBoolean(false);
    public Streams() throws Exception{
        url = String.format("https://%s", System.getenv("ADDRESS"));
    }

    public void run(){
        int threads = Integer.parseInt(System.getenv("THREADS"));
        int rounds = Integer.parseInt(System.getenv("ROUNDS"));
        ArrayList<ForkJoinTask<Boolean>> tasks = new ArrayList<>();
        for (int r = 0; r < rounds; rounds++) {
            for (int i = 0; i < threads; i++) {
                tasks.add(ForkJoinPool.commonPool().submit(() -> {
                    Sinks.Many<FluxMessage> sink = Sinks.many().unicast().onBackpressureBuffer();
                    CO5Subscriber subs;

                    try {
                        byte[] bais = Files.readAllBytes(Path.of("aca.pdf"));
                        UUID fluxId = UUID.randomUUID();
                        CO5Subscriber subscriber = new CO5Subscriber();
                        WebClient wc = getWebClient(url);
                        wc.post().uri("/stream")
                                .contentType(MediaType.APPLICATION_PROTOBUF)
                                .body(sink.asFlux()
                                        /*.publishOn(Schedulers.newBoundedElastic(3, docs, "publisher"))*/, FluxMessageOuterClass.FluxMessage.class)
                                .accept(MediaType.APPLICATION_PROTOBUF)
                                .retrieve().bodyToFlux(FluxMessageOuterClass.FluxMessage.class).subscribeWith(subscriber);
                        long start = System.currentTimeMillis();
                        subscriber.start = start;
                        subscriber.total = docs;
                        while (!subscribed.getAcquire()){
                            Thread.sleep(5);
                        }
                        ForkJoinTask<Boolean> t = ForkJoinPool.commonPool().submit(() -> {
                            try {
                                while (counter.getAcquire() > 0) {
                                    int gap = Streams.maxBatch - Streams.batch.getAcquire();
                                    if (gap > 0){
                                        for (int c = 0; c < gap; c++) {
                                            Map<String, String> pdf = new HashMap<>();
                                            if (counter.get() % 2 == 0) {
                                                pdf.put("stages", "LOAD_PDF,ADD_PAGE,SAVE_PDF");
                                                pdf.put("message", "streams");
                                            } else {
                                                pdf.put("stages", "LOAD_PDF,REMOVE_PAGE,SAVE_PDF");
                                            }
                                            FluxMessage fm = FluxMessageOuterClass.FluxMessage.newBuilder().setFlux(fluxId.toString())
                                                    .setId(UUID.randomUUID().toString()).putAllParams(pdf).setSent(-1)
                                                    .setPayload(UnsafeByteOperations.unsafeWrap(bais)).build();
                                            Streams.flying.add(fm.getId());
                                            sink.emitNext(fm, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(3)));
                                            Streams.batch.incrementAndGet();
                                        }
                                        //System.out.println(String.format("%d sent", gap));
                                    }
                                    Thread.sleep(5);
                                }
                            } catch (Exception x) {
                                x.printStackTrace();
                            }
                            return true;
                        });
                        t.join();
                        System.out.println("Millis per doc" + (System.currentTimeMillis() - start) / docs);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                }));
            }
            for (ForkJoinTask<Boolean> t : tasks) {
                t.join();
            }
        }
    }
    public static void main(String[] args) {
        try{
            Streams s = new Streams();
            s.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private WebClient getWebClient(String baseUrl) throws Exception {
        SslContext sslContext = buildClientSslContext(loadCertificate(System.getenv("TLS_CERT")));

        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec.sslContext(sslContext)
                        .handlerConfigurator(h -> {
                            SSLEngine engine = h.engine();
                            SSLParameters sslParameters = engine.getSSLParameters();
                            sslParameters.setServerNames(Collections.singletonList(new SNIHostName(System.getenv("SNI"))));
                            engine.setSSLParameters(sslParameters);
                        }))
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .protocol(HttpProtocol.HTTP11)
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

class CO5Subscriber implements Subscriber<FluxMessageOuterClass.FluxMessage> {
    AtomicBoolean f = new AtomicBoolean();
    Subscription _Subscription;
    long start;
    int total;
    @Override
    public void onSubscribe(Subscription s) {
        _Subscription = s;
        _Subscription.request(Long.MAX_VALUE);
        System.out.println("Streams Subscribed on " + Thread.currentThread().getName());
        Streams.subscribed.setRelease(true);
    }

    @Override
    public void onNext(FluxMessageOuterClass.FluxMessage m) {
        if (Streams.flying.contains(m.getId()) && m.getSent() > 0) {
            Streams.flying.remove(m.getId());
            if (Streams.counter.decrementAndGet() == 0) {
                _Subscription.cancel();
            }
            Streams.batch.decrementAndGet();
            if (Streams.counter.getAcquire() % Streams.maxBatch == 0) {
                System.out.println(String.format("%s count %d, %s", Thread.currentThread().getName(), Streams.counter.getAcquire(), m.getId()));
                if (total != 0 && start != 0) {
                    System.out.println(String.format("avg: %d, flying %d, batch %d"
                            , (System.currentTimeMillis() - start) / (total - Streams.counter.getAcquire()), Streams.flying.size(), Streams.batch.getAcquire()));

                }
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
        f.set(true);
    }

    @Override
    public void onComplete() {
        System.out.println("COMPLETE");
        f.set(true);
    }
}
