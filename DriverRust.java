package co5.demo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

public class Rust {

    String url = "https://deployment.co5.be";
    String clientId = "id";
    String auth = "auth";

    private static TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
            }
        }
    };

    @Test
    public void runDeploy() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        long l = System.currentTimeMillis();

        ArrayList<ForkJoinTask<?>> results = new ArrayList<>();
        int threads = 1;
        for (int i = 0; i < threads; i++) {
            results.add(ForkJoinPool.commonPool().submit(() -> {
                try {
                    System.out.println("Executing on thread: " + Thread.currentThread().getName());
                    HttpRequest request;
                    HttpResponse<String> response;
                    HttpResponse<Path> response1;
                    JsonObject responseBody;
                    SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
                    sslContext.init(null, trustAllCerts, new SecureRandom());

                    HttpClient client = HttpClient.newBuilder()
                            .sslContext(sslContext)
                            .build();

                    //Domain knowledge in a jar
                    FileInputStream fis = new FileInputStream("libclient_code.so"); 
                    byte[] ba = fis.readAllBytes();
                    fis.close();
                    byte[] resultByte = DigestUtils.md5(ba);
                    String streamMD5 = URLEncoder.encode(new String(java.util.Base64.getEncoder().encode(resultByte)), "utf-8");
                    //Request upload url, this creates a private bucket where the domain knowledge
                    //is stored. It returns a presigned url valid for 15 minutes
                    request = HttpRequest.newBuilder()
                            .GET()
                            .header("Authorization", auth)
                            .header("Client", clientId)
                            .uri(new URI(url + "/?md5=" + streamMD5))
                            .build();
                    response = client.send(request, BodyHandlers.ofString());
                    System.out.println("Response: " + response.body());
                    System.out.println("Response status: " + response.statusCode());
                    try {
                        responseBody = (JsonObject) JsonParser.parseString(response.body());
                        String id = responseBody.get("id").getAsString().trim();
                        if (response.statusCode() == 200) {
                            //Upload jar 
                            request = HttpRequest.newBuilder()
                                    .PUT(BodyPublishers.ofByteArray(ba))
                                    .timeout(Duration.ofMinutes(60))
                                    .uri(new URI(responseBody.get("url").getAsString().trim()))
                                    .header("content-type", "application/octet-stream")
                                    .build();
                            response = client.send(request, BodyHandlers.ofString());
                            System.out.println("Upload response status: " + response.statusCode());
                            request = HttpRequest.newBuilder()
                                    .POST(BodyPublishers.ofString("{ \"deployIp\": \"IP\", \"workUnitFactory\": \"work_unit_factory\", \"authorizer\": \"authorize\", \"buildMethod\": \"rest_workflow\", \"language\": \"rust\", \"passThrough_DUMMY\": \"TTTTTT\" }"))
                                    .timeout(Duration.ofMinutes(60))
                                    .header("Authorization", auth)
                                    .header("Client", clientId)
                                    .uri(new URI(url + "/" + id))
                                    .build();
                            response = client.send(request, BodyHandlers.ofString());
                        }
                        System.out.println("Thread: " + Thread.currentThread().getName() + "ID: " + id + " Response: " + response.body() + " Status: " + response.statusCode());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }));
        }
        for (int i = 0; i < threads;) {
            results.get(i).join();
            i++;
        }
    }
    
    @Test
    public void runJobPayload() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        long l = System.currentTimeMillis();
        byte[] bais = Files.readAllBytes(Path.of("8grade.csv"));
        ArrayList<ForkJoinTask<?>> results = new ArrayList<>();
        int threads = 10, docs = 100;
        
        url = "https://426d3e0e-bbb0-480c-9d66-358bc4b636a4.co5.be";
            
        for (int i = 0; i < threads; i++) {
            results.add(ForkJoinPool.commonPool().submit(() -> {
                try {
                    System.out.println("Executing on thread: " + Thread.currentThread().getName());
                    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                    sslContext.init(null, trustAllCerts, new SecureRandom());
                    
                    HttpClient client1 = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .sslContext(sslContext)
                        .build();
                    HttpResponse<String> response1;
                    HttpRequest request1;
                    
                    for (int ii = 0; ii < docs; ii++) {
                        String id1 = UUID.randomUUID().toString();
                        try {
                            Thread.sleep(5);
                            request1 = HttpRequest
                                    .newBuilder(URI.create(String.format("%s/%s", url, id1)))
                                    .header("authorization", "valid").POST(HttpRequest.BodyPublishers.ofByteArray(bais))
                                    .timeout(Duration.ofMinutes(5)).build();
                            response1 = client1.send(request1,HttpResponse.BodyHandlers.ofString());
                            System.out.println(String.format("%s received: %d body: %s", Thread.currentThread().getName(), response1.statusCode(), response1.body()));
                        } catch (Exception x) {
                                Logger.getLogger(Java.class.getName()).log(Level.SEVERE, null, x);
                        }
                    }
                    System.out.println("Thread: " + Thread.currentThread().getName() + " completed requests");
                } catch (KeyManagementException ex) {
                    Logger.getLogger(Java.class.getName()).log(Level.SEVERE, null, ex);
                } catch (NoSuchAlgorithmException ex) {
                    Logger.getLogger(Java.class.getName()).log(Level.SEVERE, null, ex);
                }
            }));
        }
        for (int i = 0; i < threads;) {
            results.get(i).join();
            i++;
        }
        System.out.println("Done in " + ((System.currentTimeMillis() - l) / (threads * docs)) + " ms per doc" );        
    }
}
