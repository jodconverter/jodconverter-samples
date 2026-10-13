package org.jodconverter.sample.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;

/**
 * Calls the running application as the form of its page would: the conversions go through a real
 * office process, which must be installed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConverterControllerTest {

  private static final String BOUNDARY = "----ConverterControllerTest";
  private static final byte[] TEXT = "Hello from the sample test".getBytes(StandardCharsets.UTF_8);

  // Three pages of text, as a flat OpenDocument text: each paragraph starts a new page.
  private static final byte[] THREE_PAGES =
      ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
              + "<office:document"
              + " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\""
              + " xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\""
              + " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\""
              + " xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\""
              + " office:version=\"1.3\""
              + " office:mimetype=\"application/vnd.oasis.opendocument.text\">"
              + "<office:automatic-styles><style:style style:name=\"NewPage\""
              + " style:family=\"paragraph\"><style:paragraph-properties"
              + " fo:break-before=\"page\"/></style:style></office:automatic-styles>"
              + "<office:body><office:text><text:p>Page one</text:p>"
              + "<text:p text:style-name=\"NewPage\">Page two</text:p>"
              + "<text:p text:style-name=\"NewPage\">Page three</text:p>"
              + "</office:text></office:body></office:document>")
          .getBytes(StandardCharsets.UTF_8);

  private final HttpClient client = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  @Autowired private OfficeManager officeManager;

  // Posts the form of the page with a document and the given fields.
  private HttpResponse<byte[]> post(
      final String filename, final byte[] document, final Map<String, String> fields)
      throws IOException, InterruptedException {

    final ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(
        ("--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"inputFile\"; filename=\""
                + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.write(document);
    body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    for (final Map.Entry<String, String> field : fields.entrySet()) {
      body.write(
          ("--"
                  + BOUNDARY
                  + "\r\nContent-Disposition: form-data; name=\""
                  + field.getKey()
                  + "\"\r\n\r\n"
                  + field.getValue()
                  + "\r\n")
              .getBytes(StandardCharsets.UTF_8));
    }
    body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/converter"))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  private HttpResponse<byte[]> post(
      final String filename, final byte[] document, final String... fields)
      throws IOException, InterruptedException {

    final Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i < fields.length; i += 2) {
      map.put(fields[i], fields[i + 1]);
    }
    return post(filename, document, map);
  }

  // The message of a refused or failed conversion, which the page shows.
  private static String message(final HttpResponse<byte[]> response, final int status) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(value -> assertThat(value).startsWith("text/plain"));
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  // A PDF document as text, one byte per character, to look for its markers.
  private static String pdf(final HttpResponse<byte[]> response) {
    assertThat(response.statusCode()).isEqualTo(200);
    final String pdf = new String(response.body(), StandardCharsets.ISO_8859_1);
    assertThat(pdf).startsWith("%PDF-");
    return pdf;
  }

  private static int pageCount(final String pdf) {
    final Matcher matcher = Pattern.compile("/Type\\s*/Page(?![a-zA-Z])").matcher(pdf);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private static boolean isLandscape(final String pdf) {
    final Matcher matcher =
        Pattern.compile("/MediaBox\\s*\\[\\s*0\\s+0\\s+([0-9.]+)\\s+([0-9.]+)").matcher(pdf);
    assertThat(matcher.find()).as("a MediaBox in the PDF").isTrue();
    return Double.parseDouble(matcher.group(1)) > Double.parseDouble(matcher.group(2));
  }

  @Test
  void shouldServeThePage() throws Exception {

    final HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains(
            "name=\"inputFile\"",
            "name=\"outputFormat\"",
            "name=\"password\"",
            "name=\"landscape\"",
            "name=\"removeHeaderFooter\"",
            "name=\"pdfPreset\"",
            "name=\"pageRange\"");
  }

  @Test
  void shouldConvertAndNameTheConvertedFile() throws Exception {

    final HttpResponse<byte[]> response = post("rapport é final.txt", TEXT, "outputFormat", "pdf");

    assertThat(pdf(response)).isNotEmpty();
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
    assertThat(response.headers().firstValue("Content-Disposition"))
        .hasValueSatisfying(
            value -> assertThat(value).contains("filename*=UTF-8''rapport%20%C3%A9%20final.pdf"));
  }

  @Test
  void shouldRefuseARequestWithoutAFormatOrWithAnUnknownOne() throws Exception {

    assertThat(message(post("hello.txt", TEXT), 400)).isEqualTo("Please select an output format.");
    assertThat(message(post("hello.txt", TEXT, "outputFormat", "nope"), 400))
        .isEqualTo("The output format is not supported.");
  }

  @Test
  void shouldRefuseARequestThatIsNotAnUpload() throws Exception {

    final HttpResponse<byte[]> response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/converter"))
                .POST(HttpRequest.BodyPublishers.ofString("not a form"))
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());

    assertThat(message(response, 400)).isEqualTo("Please select a file to upload.");
  }

  @Test
  void shouldRefuseADocumentThatIsTooLarge() throws Exception {

    // One byte more than spring.servlet.multipart.max-file-size.
    assertThat(message(post("large.txt", new byte[5 * 1024 * 1024 + 1], "outputFormat", "pdf"), 413))
        .isEqualTo("The document is too large.");
  }

  @Test
  void shouldTurnThePagesWithTheLandscapeOption() throws Exception {

    assertThat(isLandscape(pdf(post("hello.txt", TEXT, "outputFormat", "pdf")))).isFalse();
    assertThat(
            isLandscape(
                pdf(post("hello.txt", TEXT, "outputFormat", "pdf", "landscape", "true"))))
        .isTrue();
  }

  @Test
  void shouldConvertACsvFileWithoutItsPageHeaderAndFooter() throws Exception {

    final byte[] csv = "Name,Amount\nFirst,10\nSecond,20\n".getBytes(StandardCharsets.UTF_8);

    // What the pages show is checked by the tests of JODConverter; here, that the option of the
    // form reaches a conversion that succeeds.
    assertThat(
            pdf(post("amounts.csv", csv, "outputFormat", "pdf", "removeHeaderFooter", "true")))
        .isNotEmpty();
  }

  @Test
  void shouldExportTheAskedPagesOnly() throws Exception {

    assertThat(pageCount(pdf(post("pages.fodt", THREE_PAGES, "outputFormat", "pdf")))).isEqualTo(3);
    assertThat(
            pageCount(
                pdf(post("pages.fodt", THREE_PAGES, "outputFormat", "pdf", "pageRange", "2-3"))))
        .isEqualTo(2);
  }

  @Test
  void shouldMakeAnArchivePdf() throws Exception {

    // A PDF/A document says so in its metadata.
    assertThat(pdf(post("hello.txt", TEXT, "outputFormat", "pdf"))).doesNotContain("pdfaid:part");
    assertThat(pdf(post("hello.txt", TEXT, "outputFormat", "pdf", "pdfPreset", "archive")))
        .contains("pdfaid:part");
  }

  @Test
  void shouldIgnoreThePdfOptionsForAnotherFormat() throws Exception {

    final HttpResponse<byte[]> response =
        post("hello.txt", TEXT, "outputFormat", "odt", "pdfPreset", "archive", "pageRange", "1");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(new String(response.body(), StandardCharsets.ISO_8859_1)).startsWith("PK");
  }

  @Test
  void shouldAskForThePasswordOfAProtectedDocument() throws Exception {

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

    // With the password, the document is converted.
    assertThat(pdf(post("secret.odt", document, "outputFormat", "pdf", "password", "secret")))
        .isNotEmpty();

    // Without it, or with a wrong one, the answer is a message that says which.
    assertThat(message(post("secret.odt", document, "outputFormat", "pdf"), 400))
        .isEqualTo("The document is protected: give its password in the options.");
    final HttpResponse<byte[]> wrong =
        post("secret.odt", document, "outputFormat", "pdf", "password", "wrong");
    assertThat(new String(wrong.body(), StandardCharsets.UTF_8))
        .containsAnyOf(
            // What the answer says depends on what the office reports for a wrong password: that
            // it is wrong (400), or only that the document could not be opened (500).
            "The password is not the one of the document.",
            "If it is protected, check its password.");
    assertThat(wrong.statusCode()).isIn(400, 500);
  }
}
