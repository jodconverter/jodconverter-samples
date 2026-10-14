package org.jodconverter.sample.attached;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.job.ConversionJobWithOptionalSourceFormatUnspecified;
import org.jodconverter.core.job.ConversionJobWithSourceSpecified;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.util.FileUtils;
import org.jodconverter.core.util.StringUtils;

/** Controller providing the conversion endpoint. */
@RestController
public class ConverterController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConverterController.class);

  private static final MediaType TEXT = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

  private final DocumentConverter converter;

  /**
   * Creates a new controller.
   *
   * @param converter The converter created by the JODConverter starter. Only the attached manager
   *     is enabled in this application, so it is the one that converts with the office process of
   *     the container; the code that uses it is the same as with an office that the application
   *     starts itself.
   */
  public ConverterController(final DocumentConverter converter) {
    this.converter = converter;
  }

  /**
   * Converts a document to a target format.
   *
   * @param file The document to convert.
   * @param format The extension of the target format, such as pdf.
   * @return The converted document, or an error status with a message as plain text.
   */
  @PostMapping(path = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  /* default */ ResponseEntity<?> convert(
      @RequestParam("file") final MultipartFile file,
      @RequestParam("format") final String format) {

    if (file.isEmpty()) {
      return error(HttpStatus.BAD_REQUEST, "The document is missing or empty.");
    }

    final DocumentFormat targetFormat = converter.getFormatRegistry().getFormatByExtension(format);
    if (targetFormat == null) {
      return error(HttpStatus.BAD_REQUEST, "The format " + format + " is not supported.");
    }

    try (ByteArrayOutputStream converted = new ByteArrayOutputStream()) {

      source(file).to(converted).as(targetFormat).execute();

      final HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType(targetFormat.getMediaType()));
      // The name comes from the request: ContentDisposition quotes and encodes it.
      headers.setContentDisposition(
          ContentDisposition.attachment()
              .filename(
                  FileUtils.getBaseName(String.valueOf(file.getOriginalFilename()))
                      + "."
                      + targetFormat.getExtension(),
                  StandardCharsets.UTF_8)
              .build());
      return new ResponseEntity<>(converted.toByteArray(), headers, HttpStatus.OK);

    } catch (OfficeException | IOException e) {
      // The office refused the document, failed, or could not be reached: the application
      // worked, the office it depends on did not. The details are for the log.
      LOGGER.error("Could not convert {}", file.getOriginalFilename(), e);
      return error(HttpStatus.BAD_GATEWAY, "The office could not convert the document.");
    }
  }

  // The uploaded document, with the format its name tells. A stream has no name: without the
  // format the office guesses from the content. When the extension is unknown, the office is left
  // to guess.
  private ConversionJobWithSourceSpecified source(final MultipartFile file) throws IOException {

    final ConversionJobWithOptionalSourceFormatUnspecified job =
        converter.convert(file.getInputStream());
    final String extension = FileUtils.getExtension(String.valueOf(file.getOriginalFilename()));
    final DocumentFormat sourceFormat =
        StringUtils.isBlank(extension)
            ? null
            : converter.getFormatRegistry().getFormatByExtension(extension);
    return sourceFormat == null ? job : job.as(sourceFormat);
  }

  // The upload is refused before the conversion is called: these answer as the conversion does.
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  /* default */ ResponseEntity<String> tooLarge() {
    return error(HttpStatus.CONTENT_TOO_LARGE, "The document is too large.");
  }

  @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
  /* default */ ResponseEntity<String> notAnUpload() {
    return error(HttpStatus.BAD_REQUEST, "The document is missing or empty.");
  }

  private static ResponseEntity<String> error(final HttpStatus status, final String message) {
    return ResponseEntity.status(status).contentType(TEXT).body(message);
  }
}
