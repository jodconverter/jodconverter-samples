package org.jodconverter.sample.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.jodconverter.sample.webapp.launcher.Launcher;

/**
 * Runs the war file of the web application in an embedded Tomcat, as the run task does, and calls
 * it as a browser would: the conversions go through a real office process, which must be
 * installed.
 */
class BasicWebappTest {

  private static final String BOUNDARY = "----BasicWebappTest";

  private static Tomcat tomcat;
  private static String baseUrl;

  private final HttpClient client = HttpClient.newHttpClient();

  @BeforeAll
  static void startTheApplication() throws Exception {
    // The build gives the war file it has just made; any free port.
    tomcat = Launcher.start(new File(System.getProperty("sample.war")), 0);
    baseUrl =
        "http://localhost:" + tomcat.getConnector().getLocalPort() + Launcher.CONTEXT_PATH + "/";
  }

  @AfterAll
  static void stopTheApplication() throws Exception {
    // Stops the web application, and with it its office manager.
    tomcat.stop();
    tomcat.destroy();
  }

  // Posts a document, named as given, as the form of the page does.
  private HttpResponse<byte[]> post(final String path, final String filename, final byte[] document)
      throws IOException, InterruptedException {

    final ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(
        ("--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"inputDocument\"; filename=\""
                + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.write(document);
    body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return client.send(
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  private static byte[] text() {
    return "Hello from the sample test".getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void shouldServeThePage() throws Exception {

    final HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(baseUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("name=\"inputDocument\""));
  }

  @Test
  void shouldConvertToTheFormatOfTheUrl() throws Exception {

    final HttpResponse<byte[]> response = post("converted/document.pdf", "hello.txt", text());

    assertEquals(200, response.statusCode());
    assertEquals("application/pdf", response.headers().firstValue("Content-Type").orElse(null));
    assertTrue(new String(response.body(), StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
  }

  @Test
  void shouldNameTheConvertedFileSafely() throws Exception {

    final HttpResponse<byte[]> response =
        post("converted/document.odt", "rapport é final.txt", text());

    assertEquals(200, response.statusCode());
    final String disposition = response.headers().firstValue("Content-Disposition").orElse("");
    assertTrue(disposition.startsWith("attachment; filename=\"rapport___final.odt\""), disposition);
    assertTrue(disposition.endsWith("filename*=UTF-8''rapport%20%C3%A9%20final.odt"), disposition);
  }

  @Test
  void shouldRefuseAnUnknownFormat() throws Exception {

    final HttpResponse<byte[]> response = post("converted/document.nope", "hello.txt", text());

    assertEquals(400, response.statusCode());
  }

  @Test
  void shouldRefuseARequestThatIsNotAnUpload() throws Exception {

    final HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "converted/document.pdf"))
                .POST(HttpRequest.BodyPublishers.ofString("not a form"))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(400, response.statusCode());
  }

  @Test
  void shouldNotShowTheDetailsOfAFailedConversion() throws Exception {

    // Bytes that are no document, under the name of one.
    final HttpResponse<byte[]> response =
        post("converted/document.pdf", "broken.docx", new byte[] {0, 1, 2, 3, 4, 5, 6, 7});

    // Either the office refuses the file, or it reads it as text and converts it. When it fails,
    // the answer does not tell how.
    if (response.statusCode() != 200) {
      assertEquals(500, response.statusCode());
      assertFalse(new String(response.body(), StandardCharsets.UTF_8).contains("Exception"));
    }
  }
}
