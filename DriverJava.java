package co5.demo;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DriverJava {

    String url = "https://deployment.co5.be";
    String clientId = "id";
    String auth = "auth";

    @Test
    public void runDeploy() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        long l = System.currentTimeMillis();

        ArrayList<ForkJoinTask<?>> results = new ArrayList<>();
        int threads = 1;
        for (int i = 0; i < threads; i++) {
            results.add(ForkJoinPool.commonPool().submit(() -> {
                try {
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
                    byte[] ba = new FileInputStream("build/libs/demo.jar").readAllBytes();
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
                                    .POST(BodyPublishers.ofString("{ \"deployIp\": \"AN_IP_ADDRESS\", \"builderClass\": \"co5.demo.DemoBuilder\", \"passThrough_DUMMY\": \"TTTTTT\" }"))
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
    public void getUrl() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        SSLContext ssl = SSLContext.getInstance("TLSv1.3");
        String jobId = "b7743898-7c22-4fb8-9659-abbdc22a3847";
        ssl.init(null, trustAllCerts, new SecureRandom());
        HttpClient client = HttpClient.newBuilder()
                .sslContext(ssl)
                .build();

        HttpRequest request = HttpRequest
                .newBuilder(URI.create(
                        String.format("%s/deployment/%s", url, jobId)))
                .header("Authorization", auth)
                .header("Client", clientId)
                .timeout(Duration.ofMinutes(5)).build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
         if (response.statusCode() != 200) {
            System.out.println("Received error:" + response.statusCode());
        }
        else{
            System.out.println("Received:" + response.body());
        }
    }

    @Test
    public void delete() throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException, IOException, InterruptedException {
        HttpRequest request;
        HttpResponse<String> response;
        String id = "d09f73f1-ae81-4a95-a2a9-6d401a5d5b6e";
        request = HttpRequest.newBuilder()
                .DELETE()
                .timeout(Duration.ofMinutes(3))
                .header("Authorization", auth)
                .header("Client", clientId)
                .uri(new URI(String.format("%s/%s", url, id)))
                .build();
        SSLContext ssl = SSLContext.getInstance("TLSv1.3");

        ssl.init(null, trustAllCerts, new SecureRandom());
        HttpClient client = HttpClient.newBuilder()
                .sslContext(ssl)
                .build();
        response = client.send(request, BodyHandlers.ofString());
        System.out.println("Response: " + response.body());
        System.out.println("Delete Response status: " + response.statusCode());
    }

    @Test
    public void runJob() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        long l = System.currentTimeMillis();
        byte[] bais = Files.readAllBytes(Path.of("8grade.158k.xlsx"));
        byte[] bais1 = Files.readAllBytes(Path.of("Brochure.docx"));

        ArrayList<ForkJoinTask<?>> results = new ArrayList<>();
        int threads = 3, docs = 667;
        String id = "b7743898-7c22-4fb8-9659-abbdc22a3847";
        url = String.format("https://%s.co5.be", id);
        for (int i = 0; i < threads; i++) {
            results.add(ForkJoinPool.commonPool().submit(() -> {
                try {
                    System.out.println("Executing on thread: " + Thread.currentThread().getName());
                    SSLContext ssl = SSLContext.getInstance("TLSv1.2");
                    ssl.init(null, trustAllCerts, new SecureRandom());

                    HttpClient client1 = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                            .sslContext(ssl).build();
                    HttpResponse<Path> response1;
                    HttpRequest request1;

                    Stack<String> retries = new Stack<String>();
                    int ii;
                    for (ii = 0; ii < docs; ii++) {
                        String id1 = UUID.randomUUID().toString();
                        try {
                            Thread.sleep(10);
                            request1 = HttpRequest
                                    .newBuilder(URI.create(
                                            String.format("%s/%s?stages=%s&message=%d", url, id1, (ii % 2 == 0 ? "LOAD_EXCEL,RENAME_SHEETS,SET_PAYLOAD" : "LOAD_DOCX,ADD_TEXT,SET_PAYLOAD"), System.currentTimeMillis())))
                                    .header("authorization", "valid").POST(HttpRequest.BodyPublishers.ofByteArray(ii % 2 == 0 ? bais : bais1))
                                    .timeout(Duration.ofMinutes(5)).build();
                            response1 = client1.send(request1,
                                    HttpResponse.BodyHandlers.ofFile(Path.of("output", String.format((ii % 2 == 0 ? "doc-%s.xlsx" : "doc-%s.docx"), id1))));
                            if (response1.statusCode() == 200) {
                                System.out.println(String.format((ii % 2 == 0 ? "doc-%s.xlsx" : "doc-%s.docx"), id1));
                            } else {
                                System.out.println("error, printing logs, retrying: " + id1);
                                retries.push(id1);
                            }
                        } catch (Exception x) {
                            x.printStackTrace();
                        }
                    }
                    for (String id1 : retries) {
                        try {
                            request1 = HttpRequest
                                    .newBuilder(URI.create(
                                            String.format("%s/%s", url, id1)))
                                    .header("authorization", "valid").POST(HttpRequest.BodyPublishers.ofByteArray(bais))
                                    .timeout(Duration.ofMinutes(10)).build();
                            response1 = client1.send(request1,
                                    HttpResponse.BodyHandlers.ofFile(Path.of("output", String.format("doc-%s.xlsx", id1))));
                            if (response1.statusCode() == 200) {
                                System.out.println(String.format("received: doc-%s.xlsx", id1));
                            } else {
                                System.out.println("Retrying recived error:" + response1.statusCode());
                                retries.push(id1);
                            }

                        } catch (Exception x) {
                            System.out.println("retry failed: " + id1);
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
        System.out.println("Done in " + ((System.currentTimeMillis() - l) / (threads * docs)) + " ms per doc");
    }

    @Test
    public void downloadLogs() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        SSLContext ssl = SSLContext.getInstance("TLSv1.3");
        String jobId = "008ca39b-e955-4654-a2d0-f44f34624af1";
        ssl.init(null, trustAllCerts, new SecureRandom());
        HttpClient client = HttpClient.newBuilder()
                .sslContext(ssl)
                .build();

        HttpRequest request = HttpRequest
                .newBuilder(URI.create(
                        String.format("%s/logs/%s", url, jobId)))
                .header("Authorization", auth)
                .header("Client", clientId)
                .timeout(Duration.ofMinutes(5)).build();
        HttpResponse<Path> response = client.send(request,
                HttpResponse.BodyHandlers.ofFile(Path.of("output", String.format("logs-%s.zip", jobId))));
         if (response.statusCode() != 200) {
            System.out.println("Received error:" + response.statusCode());
        }
    }

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
}
