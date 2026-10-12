package org.jodconverter.sample.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * Calls the running application as a client would: the conversions go through a real office
 * process, which must be installed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConverterControllerTest {

  private static final String BOUNDARY = "----ConverterControllerTest";

  private final HttpClient client = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  // Posts a small text document, named as given, to the given path of the application.
  private HttpResponse<byte[]> post(final String path, final String filename)
      throws IOException, InterruptedException {

    final String body =
        "--"
            + BOUNDARY
            + "\r\nContent-Disposition: form-data; name=\"data\"; filename=\""
            + filename
            + "\"\r\nContent-Type: text/plain\r\n\r\nHello from the sample test\r\n--"
            + BOUNDARY
            + "--\r\n";
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  private HttpResponse<String> get(final String path) throws IOException, InterruptedException {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String text(final HttpResponse<byte[]> response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  @Test
  void shouldConvertWithTheFormatInThePath() throws Exception {

    final HttpResponse<byte[]> response = post("/lool/convert-to/pdf", "hello.txt");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
    assertThat(text(response)).startsWith("%PDF-");
  }

  @Test
  void shouldConvertWithTheFormatAsParameter() throws Exception {

    final HttpResponse<byte[]> response = post("/lool/convert-to?format=odt", "hello.txt");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValue("application/vnd.oasis.opendocument.text");
    // An OpenDocument file is a ZIP archive.
    assertThat(text(response)).startsWith("PK");
  }

  @Test
  void shouldEncodeTheNameOfTheConvertedFile() throws Exception {

    final HttpResponse<byte[]> response = post("/lool/convert-to/pdf", "rapport é final.txt");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Disposition"))
        .hasValueSatisfying(
            value ->
                assertThat(value)
                    .startsWith("attachment;")
                    .contains("filename*=UTF-8''rapport%20%C3%A9%20final.pdf"));
  }

  @Test
  void shouldRefuseAnUnknownFormat() throws Exception {

    final HttpResponse<byte[]> response = post("/lool/convert-to/nope", "hello.txt");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(text(response)).isEqualTo("The output format is not supported: nope");
  }

  @Test
  void shouldRefuseTheLoadPropertiesThatTrustTheDocument() throws Exception {

    for (final String parameter : new String[] {"lUpdateDocMode=1", "lmacroexecutionmode=4"}) {
      final HttpResponse<byte[]> response =
          post("/lool/convert-to/pdf?" + parameter, "hello.txt");

      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(text(response)).contains("cannot be set by a request");
    }
  }

  @Test
  void shouldAcceptOtherProperties() throws Exception {

    // What a jodconverter-remote client sends for a text source and a PDF target.
    final HttpResponse<byte[]> response =
        post(
            "/lool/convert-to/pdf?lFilterName=Text%20(encoded)&lFilterOptions=utf8"
                + "&sFilterName=writer_pdf_Export&sfdPageRange=1",
            "hello.txt");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(text(response)).startsWith("%PDF-");
  }

  @Test
  void shouldReportTheOfficeProcessesThroughTheActuator() throws Exception {

    // The health is down, with the answer 503, as long as no office process is ready: the
    // application answers before its office processes are started.
    HttpResponse<String> health = get("/actuator/health");
    for (int i = 0; i < 120 && health.statusCode() != 200; i++) {
      Thread.sleep(500L);
      health = get("/actuator/health");
    }
    assertThat(health.statusCode()).isEqualTo(200);
    assertThat(health.body()).contains("\"jodconverter\"", "\"queueSize\"", "\"workers\"");

    final HttpResponse<String> workers = get("/actuator/metrics/jodconverter.workers");
    assertThat(workers.statusCode()).isEqualTo(200);
    assertThat(workers.body()).contains("\"jodconverter.workers\"", "\"state\"");
  }
}
