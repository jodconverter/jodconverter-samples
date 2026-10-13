package org.jodconverter.sample.webapp;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.util.FileUtils;

/**
 * Converts the document uploaded by the form of the page to the format named by the extension of
 * the request URL, and sends the converted document back.
 *
 * <p>The upload is read with the multipart support of the servlet container, which web.xml
 * configures for this servlet (maximum size of a file and of a request).
 */
public class ConverterServlet extends HttpServlet {
  private static final long serialVersionUID = -591469426224201748L;

  private static final Logger LOGGER = LoggerFactory.getLogger(ConverterServlet.class);

  private static final String PART_NAME = "inputDocument";

  @Override
  public void init() {
    LOGGER.info("Servlet {} has started", this.getServletName());
  }

  @Override
  public void destroy() {
    LOGGER.info("Servlet {} has stopped", this.getServletName());
  }

  @Override
  protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {

    final String contentType = request.getContentType();
    if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
      refuse(response, HttpServletResponse.SC_BAD_REQUEST, "Only multipart requests are allowed");
      return;
    }

    final Part uploadedFile;
    try {
      uploadedFile = request.getPart(PART_NAME);
    } catch (IllegalStateException tooLarge) {
      // The container refuses a file or a request larger than what web.xml allows.
      refuse(
          response,
          HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
          "The uploaded document is too large");
      return;
    }
    if (uploadedFile == null || uploadedFile.getSize() == 0) {
      refuse(response, HttpServletResponse.SC_BAD_REQUEST, "No document was uploaded");
      return;
    }

    // The name of the uploaded file, without any path that a browser may have sent with it.
    final String uploadedName =
        new File(String.valueOf(uploadedFile.getSubmittedFileName())).getName();
    final String inputExtension = FileUtils.getExtension(uploadedName);
    final String baseName = String.valueOf(FileUtils.getBaseName(uploadedName));
    final String outputExtension = FileUtils.getExtension(request.getRequestURI());

    final DocumentConverter converter =
        WebappContext.get(getServletContext()).getDocumentConverter();
    final DocumentFormat outputFormat =
        outputExtension == null
            ? null
            : converter.getFormatRegistry().getFormatByExtension(outputExtension);
    if (outputFormat == null) {
      refuse(
          response,
          HttpServletResponse.SC_BAD_REQUEST,
          "The output format is missing or not supported");
      return;
    }

    // The office converts from a file to a file: the upload is written to a temporary file, whose
    // extension tells the office what the document is.
    final File inputFile =
        File.createTempFile("upload", inputExtension == null ? "" : "." + inputExtension);
    final File outputFile = File.createTempFile("converted", "." + outputFormat.getExtension());
    try {
      uploadedFile.write(inputFile.getAbsolutePath());

      final long startTime = System.currentTimeMillis();
      converter.convert(inputFile).to(outputFile).execute();
      LOGGER.info(
          "Successful conversion: {} [{}b] to {} in {}ms",
          inputExtension,
          inputFile.length(),
          outputFormat.getExtension(),
          System.currentTimeMillis() - startTime);

      response.setContentType(outputFormat.getMediaType());
      response.setHeader(
          "Content-Disposition",
          contentDisposition(baseName + "." + outputFormat.getExtension()));
      response.setContentLengthLong(outputFile.length());
      Files.copy(outputFile.toPath(), response.getOutputStream());

    } catch (Exception exception) {
      // The details are for the log: the caller is only told that the conversion failed.
      LOGGER.error(
          "Failed conversion: {} [{}b] to {}",
          inputExtension,
          inputFile.length(),
          outputFormat.getExtension(),
          exception);
      // Drops the headers of the converted file, when the failure came after they were set.
      if (!response.isCommitted()) {
        response.reset();
      }
      refuse(
          response,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "The document could not be converted");
    } finally {
      FileUtils.deleteQuietly(outputFile);
      FileUtils.deleteQuietly(inputFile);
      uploadedFile.delete();
    }
  }

  // Answers an error status with a short message as plain text, which the page shows. The
  // messages are constants: nothing of the request or of the exception goes into them.
  private static void refuse(
      final HttpServletResponse response, final int status, final String message)
      throws IOException {

    response.setStatus(status);
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(message);
  }

  // The name of the converted file comes from the request (the name of the uploaded file and the
  // extension of the URL), so it is not written as it is in the header, where a quote or a line
  // break would change the header: a plain name for old browsers, with anything unusual replaced,
  // and the real name encoded as RFC 5987 says.
  private static String contentDisposition(final String filename) {

    final String fallback = filename.replaceAll("[^A-Za-z0-9._-]", "_");
    final String encoded =
        URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
  }
}
