package org.jodconverter.sample.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Calls the application over HTTP, with a stand-in for the conversion server: a small HTTP server
 * that answers the convert-to service as Collabora Online does. The tests need neither an office
 * nor a container; they check what the application sends to the server and what it answers.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConverterControllerTest {

  private static final String BOUNDARY = "----ConverterControllerTest";
  private static final String CONVERTED = "%PDF-converted by the stand-in server";

  // The requests the stand-in server received: "METHOD path", then the body.
  private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
  private static final HttpServer SERVER = startServer();

  private final HttpClient client = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  private static HttpServer startServer() {
    try {
      final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/cool/convert-to/",
          exchange -> {
            final String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            REQUESTS.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            REQUESTS.add(body);
            // A document that the server cannot convert, for the test of a failure.
            final boolean refused = body.contains("not a document");
            final byte[] answer =
                (refused ? "the server failed" : CONVERTED).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(refused ? 500 : 200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  // The application is given the address of the stand-in server instead of the one of
  // application.yml.
  @DynamicPropertySource
  static void conversionServer(final DynamicPropertyRegistry registry) {
    registry.add(
        "jodconverter.remote.url",
        () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/cool/");
  }

  @AfterAll
  static void stopServer() {
    SERVER.stop(0);
  }

  @BeforeEach
  void forgetRequests() {
    REQUESTS.clear();
  }

  // Posts a document, named as given, and the fields of the form.
  private HttpResponse<byte[]> post(
      final String filename, final String content, final String... fields)
      throws IOException, InterruptedException {

    final ByteArrayOutputStream body = new ByteArrayOutputStream();
    if (filename != null) {
      body.write(
          ("--"
                  + BOUNDARY
                  + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                  + filename
                  + "\"\r\nContent-Type: application/octet-stream\r\n\r\n"
                  + content
                  + "\r\n")
              .getBytes(StandardCharsets.UTF_8));
    }
    for (int i = 0; i < fields.length; i += 2) {
      body.write(
          ("--"
                  + BOUNDARY
                  + "\r\nContent-Disposition: form-data; name=\""
                  + fields[i]
                  + "\"\r\n\r\n"
                  + fields[i + 1]
                  + "\r\n")
              .getBytes(StandardCharsets.UTF_8));
    }
    body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/convert"))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  private static String text(final HttpResponse<byte[]> response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  @Test
  void shouldSendTheDocumentToTheConversionServerAndAnswerWhatItConverted() throws Exception {

    final HttpResponse<byte[]> response =
        post("rapport é final.txt", "Hello from the sample test", "format", "pdf");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(text(response)).isEqualTo(CONVERTED);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
    assertThat(response.headers().firstValue("Content-Disposition"))
        .hasValueSatisfying(
            value -> assertThat(value).contains("filename*=UTF-8''rapport%20%C3%A9%20final.pdf"));

    // The server was asked for the target format in the URL, and was sent the document.
    assertThat(REQUESTS).hasSize(2);
    assertThat(REQUESTS.get(0)).isEqualTo("POST /cool/convert-to/pdf");
    assertThat(REQUESTS.get(1)).contains("Hello from the sample test");
  }

  @Test
  void shouldRefuseAnUnknownFormatWithoutCallingTheConversionServer() throws Exception {

    final HttpResponse<byte[]> response = post("hello.txt", "Hello", "format", "nope");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(text(response)).isEqualTo("The format nope is not supported.");
    assertThat(REQUESTS).isEmpty();
  }

  @Test
  void shouldRefuseARequestWithoutADocument() throws Exception {

    final HttpResponse<byte[]> response = post(null, null, "format", "pdf");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(text(response)).isEqualTo("The document is missing or empty.");
    assertThat(REQUESTS).isEmpty();
  }

  @Test
  void shouldAnswerBadGatewayWhenTheConversionServerFails() throws Exception {

    final HttpResponse<byte[]> response = post("broken.docx", "not a document", "format", "pdf");

    // The message does not repeat what the server said.
    assertThat(response.statusCode()).isEqualTo(502);
    assertThat(text(response)).isEqualTo("The conversion server could not convert the document.");
  }
}
