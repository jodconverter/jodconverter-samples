package org.jodconverter.sample.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
  private static final byte[] TEXT = "Hello from the sample test".getBytes(StandardCharsets.UTF_8);

  private final HttpClient client = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  @Autowired private OfficeManager officeManager;

  // Posts a small text document, named as given, to the given path of the application.
  private HttpResponse<byte[]> post(final String path, final String filename)
      throws IOException, InterruptedException {
    return post(path, filename, TEXT);
  }

  // Posts a document, named as given, to the given path of the application.
  private HttpResponse<byte[]> post(final String path, final String filename, final byte[] document)
      throws IOException, InterruptedException {

    final ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(
        ("--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"data\"; filename=\""
                + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.write(document);
    body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
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
  void shouldOpenAProtectedDocumentWithItsPassword() throws Exception {

    // A protected document, made with a converter that stores with a password.
    final ByteArrayOutputStream protectedDocument = new ByteArrayOutputStream();
    LocalConverter.builder()
        .officeManager(officeManager)
        .storeProperty("Password", "secret")
        .build()
        .convert(new ByteArrayInputStream(TEXT))
        .as(DefaultDocumentFormatRegistry.TXT)
        .to(protectedDocument)
        .as(DefaultDocumentFormatRegistry.ODT)
        .execute();
    final byte[] document = protectedDocument.toByteArray();

    final HttpResponse<byte[]> converted =
        post("/lool/convert-to/pdf?password=secret", "secret.odt", document);
    assertThat(converted.statusCode()).isEqualTo(200);
    assertThat(text(converted)).startsWith("%PDF-");

    // Without the password, the answer says what is missing.
    final HttpResponse<byte[]> refused = post("/lool/convert-to/pdf", "secret.odt", document);
    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(text(refused)).isEqualTo("The document is protected: give its password.");

    // With a wrong one, the conversion fails, and the answer mentions the password. Whether it
    // is a 400 or a 500 depends on what the office reports: that the password is wrong, or only
    // that the document could not be opened.
    final HttpResponse<byte[]> wrong =
        post("/lool/convert-to/pdf?password=wrong", "secret.odt", document);
    assertThat(wrong.statusCode()).isIn(400, 500);
    assertThat(text(wrong)).contains("password");
  }

  @Test
  void shouldDescribeTheOffice() throws Exception {

    final HttpResponse<String> response = get("/info");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
    assertThat(response.body())
        .contains("\"productName\"", "\"version\"", "\"filters\"", "\"writer_pdf_Export\"");
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
