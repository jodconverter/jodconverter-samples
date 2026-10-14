package org.jodconverter.sample.attached;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.jodconverter.core.util.FileUtils;
import org.jodconverter.core.util.OSUtils;
import org.jodconverter.local.office.LocalOfficeUtils;

/**
 * Calls the application over HTTP, with an office process that the test starts itself, as the
 * container of compose.yaml would: the application only connects to it. The office of the machine
 * is used (it must be installed), on a free port, with a profile of its own.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConverterControllerTest {

  private static final String BOUNDARY = "----ConverterControllerTest";

  private static int officePort;
  private static File profile;
  private static Process office;

  private final HttpClient client = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  @BeforeAll
  static void startTheOffice() throws IOException {

    try (ServerSocket socket = new ServerSocket(0)) {
      officePort = socket.getLocalPort();
    }
    profile = Files.createTempDirectory("attached-sample-profile").toFile();

    // The launcher of the office, as the container starts it: on Linux, program/soffice restarts
    // the office when its first start with a new profile ends with the exit code 81, which the
    // office binary that JODConverter starts itself (program/soffice.bin) does not. On Windows and
    // macOS, the executable that JODConverter uses is that launcher.
    final File home = LocalOfficeUtils.getDefaultOfficeHome();
    final File launcher = new File(home, "program/soffice");
    final File executable =
        !OSUtils.IS_OS_WINDOWS && launcher.isFile()
            ? launcher
            : LocalOfficeUtils.getOfficeExecutable(home);
    office =
        new ProcessBuilder(
                executable.getAbsolutePath(),
                "--headless",
                "--invisible",
                "--nocrashreport",
                "--nodefault",
                "--nofirststartwizard",
                "--nologo",
                "--norestore",
                "--nolockcheck",
                "--accept=socket,host=127.0.0.1,port="
                    + officePort
                    + ",tcpNoDelay=1;urp;StarOffice.ServiceManager",
                "-env:UserInstallation=" + LocalOfficeUtils.toUrl(profile))
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    // The application starts after this method: the attached manager retries its connection
    // until the office accepts it.
  }

  @DynamicPropertySource
  static void office(final DynamicPropertyRegistry registry) {
    registry.add("jodconverter.attached.host-name", () -> "127.0.0.1");
    registry.add("jodconverter.attached.port-numbers", () -> officePort);
  }

  @AfterAll
  static void stopTheOffice() {
    // The launcher of the office may have started the office process as a child: both are
    // stopped.
    office.descendants().forEach(ProcessHandle::destroyForcibly);
    office.destroyForcibly();
    FileUtils.deleteQuietly(profile);
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
    return new String(response.body(), StandardCharsets.ISO_8859_1);
  }

  @Test
  void shouldConvertWithTheOfficeItIsAttachedTo() throws Exception {

    final HttpResponse<byte[]> response =
        post("rapport é final.txt", "Hello from the sample test", "format", "pdf");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(text(response)).startsWith("%PDF-");
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
    assertThat(response.headers().firstValue("Content-Disposition"))
        .hasValueSatisfying(
            value -> assertThat(value).contains("filename*=UTF-8''rapport%20%C3%A9%20final.pdf"));
  }

  @Test
  void shouldConvertASpreadsheet() throws Exception {

    final HttpResponse<byte[]> response = post("data.csv", "a,b\n1,2\n", "format", "xlsx");

    // An Office Open XML file is a ZIP file.
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(text(response)).startsWith("PK");
  }

  @Test
  void shouldRefuseAnUnknownFormat() throws Exception {

    final HttpResponse<byte[]> response = post("hello.txt", "Hello", "format", "nope");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(text(response)).isEqualTo("The format nope is not supported.");
  }

  @Test
  void shouldRefuseARequestWithoutADocument() throws Exception {

    final HttpResponse<byte[]> response = post(null, null, "format", "pdf");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(text(response)).isEqualTo("The document is missing or empty.");
  }
}
