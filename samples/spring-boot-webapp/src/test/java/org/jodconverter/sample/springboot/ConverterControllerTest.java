package org.jodconverter.sample.springboot;

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
 * Calls the running application as the form of its page would: the conversion goes through a real
 * office process, which must be installed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConverterControllerTest {

  private static final String BOUNDARY = "----ConverterControllerTest";

  // The redirections are not followed, to see them.
  private final HttpClient client = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  // Posts the form of the page, with a small text document and the given output format, if any.
  private HttpResponse<byte[]> post(final String filename, final String outputFormat)
      throws IOException, InterruptedException {

    final StringBuilder body = new StringBuilder(256);
    body.append("--")
        .append(BOUNDARY)
        .append("\r\nContent-Disposition: form-data; name=\"inputFile\"; filename=\"")
        .append(filename)
        .append("\"\r\nContent-Type: text/plain\r\n\r\nHello from the sample test\r\n");
    if (outputFormat != null) {
      body.append("--")
          .append(BOUNDARY)
          .append("\r\nContent-Disposition: form-data; name=\"outputFormat\"\r\n\r\n")
          .append(outputFormat)
          .append("\r\n");
    }
    body.append("--").append(BOUNDARY).append("--\r\n");

    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/converter"))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  @Test
  void shouldServeThePage() throws Exception {

    final HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("name=\"inputFile\"", "name=\"outputFormat\"");
  }

  @Test
  void shouldConvertAndNameTheConvertedFile() throws Exception {

    final HttpResponse<byte[]> response = post("rapport é final.txt", "pdf");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
    assertThat(new String(response.body(), StandardCharsets.UTF_8)).startsWith("%PDF-");
    assertThat(response.headers().firstValue("Content-Disposition"))
        .hasValueSatisfying(
            value -> assertThat(value).contains("filename*=UTF-8''rapport%20%C3%A9%20final.pdf"));
  }

  @Test
  void shouldGoBackToThePageWithoutAFormatOrWithAnUnknownOne() throws Exception {

    assertThat(post("hello.txt", null).statusCode()).isEqualTo(302);
    assertThat(post("hello.txt", "nope").statusCode()).isEqualTo(302);
  }
}
