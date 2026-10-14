package org.jodconverter.sample.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * Calls the preview endpoints of the running application as a client would: the images are made
 * by a real office process, which must be installed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PreviewControllerTest {

  private static final String BOUNDARY = "----PreviewControllerTest";

  private static final byte[] TEXT = "Hello from the preview test".getBytes(StandardCharsets.UTF_8);

  // A presentation of three slides, as a flat OpenDocument file.
  private static final byte[] THREE_SLIDES =
      ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
              + "<office:document"
              + " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\""
              + " xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\""
              + " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\""
              + " xmlns:svg=\"urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0\""
              + " office:version=\"1.3\""
              + " office:mimetype=\"application/vnd.oasis.opendocument.presentation\">"
              + "<office:body><office:presentation>"
              + slide("One")
              + slide("Two")
              + slide("Three")
              + "</office:presentation></office:body></office:document>")
          .getBytes(StandardCharsets.UTF_8);

  private final HttpClient client = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  private static String slide(final String name) {
    return "<draw:page draw:name=\""
        + name
        + "\"><draw:frame svg:x=\"2cm\" svg:y=\"2cm\" svg:width=\"20cm\" svg:height=\"3cm\">"
        + "<draw:text-box><text:p>Slide "
        + name
        + "</text:p></draw:text-box></draw:frame></draw:page>";
  }

  // Posts a document, named as given, and the fields of the form, to the given path.
  private HttpResponse<byte[]> post(
      final String path, final String filename, final byte[] document, final String... fields)
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
    body.write("\r\n".getBytes(StandardCharsets.UTF_8));
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
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  private static String text(final HttpResponse<byte[]> response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  private static BufferedImage image(final HttpResponse<byte[]> response) throws IOException {
    assertThat(response.statusCode()).isEqualTo(200);
    final BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
    assertThat(image).as("a readable image").isNotNull();
    return image;
  }

  // The names of the files of a ZIP answer, in their order.
  private static List<String> entries(final HttpResponse<byte[]> response) throws IOException {
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/zip");
    final List<String> names = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        assertThat(zip.readAllBytes()).as(entry.getName()).isNotEmpty();
        names.add(entry.getName());
      }
    }
    return names;
  }

  @Test
  void shouldPreviewTheFirstPageOfATextAtTheAskedWidth() throws Exception {

    final HttpResponse<byte[]> response = post("/preview", "hello.txt", TEXT, "width", "200");

    assertThat(response.headers().firstValue("Content-Type")).hasValue("image/png");
    final BufferedImage image = image(response);
    assertThat(image.getWidth()).isEqualTo(200);
    // A portrait page keeps its proportions.
    assertThat(image.getHeight()).isGreaterThan(200);
  }

  @Test
  void shouldPreviewTheFirstSlideOfAPresentation() throws Exception {

    final BufferedImage image = image(post("/preview", "deck.fodp", THREE_SLIDES));

    // The default width; a slide is wider than high.
    assertThat(image.getWidth()).isEqualTo(320);
    assertThat(image.getHeight()).isLessThan(320);
  }

  @Test
  void shouldExportEachSlideInAZipFile() throws Exception {

    assertThat(entries(post("/pages", "deck.fodp", THREE_SLIDES)))
        .containsExactly("page-1.png", "page-2.png", "page-3.png");
  }

  @Test
  void shouldExportTheAskedSlidesInTheAskedFormat() throws Exception {

    assertThat(entries(post("/pages", "deck.fodp", THREE_SLIDES, "pages", "2-3", "format", "jpg")))
        .containsExactly("page-2.jpg", "page-3.jpg");
  }

  @Test
  void shouldRefuseASlideBeyondTheLastOne() throws Exception {

    final HttpResponse<byte[]> response = post("/pages", "deck.fodp", THREE_SLIDES, "pages", "4");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(text(response)).contains("the document has 3 pages");
  }

  @Test
  void shouldRefuseToExportThePagesOfAText() throws Exception {

    final HttpResponse<byte[]> response = post("/pages", "hello.txt", TEXT);

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(text(response)).startsWith("Only the slides of a presentation");
  }

  @Test
  void shouldRefuseAWidthOutOfRangeOrNotANumber() throws Exception {

    assertThat(post("/preview", "hello.txt", TEXT, "width", "5000").statusCode()).isEqualTo(400);
    final HttpResponse<byte[]> response = post("/preview", "hello.txt", TEXT, "width", "wide");
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(text(response)).isEqualTo("The width must be a number.");
  }
}
