package org.jodconverter.sample.springboot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.job.ConversionJob;
import org.jodconverter.core.job.ConversionJobWithOptionalSourceFormatUnspecified;
import org.jodconverter.core.job.ConversionJobWithSourceSpecified;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.pdf.PdfOptions;
import org.jodconverter.core.util.FileUtils;
import org.jodconverter.core.util.StringUtils;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.filter.Filter;
import org.jodconverter.local.filter.HeaderFooterRemoverFilter;
import org.jodconverter.local.filter.PageOrientationFilter;
import org.jodconverter.local.office.PasswordProtectedException;

/** Controller providing conversion endpoints. */
@Controller
public class ConverterController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConverterController.class);

  private static final MediaType TEXT = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

  private final DocumentConverter converter;
  private final OfficeManager officeManager;

  /**
   * Creates a new controller.
   *
   * @param converter The converter created by the JODConverter starter, with the options of the
   *     application properties.
   * @param officeManager The office manager created by the starter, for the conversions that need
   *     a converter of their own.
   */
  public ConverterController(final DocumentConverter converter, final OfficeManager officeManager) {
    this.converter = converter;
    this.officeManager = officeManager;
  }

  @SuppressWarnings("SameReturnValue")
  @GetMapping("/")
  /* default */ String index() {
    return "converter";
  }

  /**
   * Converts a source file to a target format.
   *
   * @param inputFile Source file to convert.
   * @param outputFormat Output format of the conversion.
   * @param password Password of the source file, when it is protected.
   * @param landscape Whether the pages are set to landscape before the conversion.
   * @param removeHeaderFooter Whether the page header and footer are removed before the
   *     conversion.
   * @param pdfPreset The PDF options to start from, when the output is a PDF document: archive,
   *     accessible or compact.
   * @param pageRange The pages to export, such as 1-3, when the output is a PDF document.
   * @return The converted file, or an error status with a message as plain text, which the page
   *     shows.
   */
  @PostMapping("/converter")
  /* default */ ResponseEntity<?> convert(
      @RequestParam("inputFile") final MultipartFile inputFile,
      @RequestParam(name = "outputFormat", required = false) final String outputFormat,
      @RequestParam(name = "password", required = false) final String password,
      @RequestParam(name = "landscape", defaultValue = "false") final boolean landscape,
      @RequestParam(name = "removeHeaderFooter", defaultValue = "false")
          final boolean removeHeaderFooter,
      @RequestParam(name = "pdfPreset", required = false) final String pdfPreset,
      @RequestParam(name = "pageRange", required = false) final String pageRange) {

    if (inputFile.isEmpty()) {
      return error(HttpStatus.BAD_REQUEST, "Please select a file to upload.");
    }

    if (StringUtils.isBlank(outputFormat)) {
      return error(HttpStatus.BAD_REQUEST, "Please select an output format.");
    }

    final DocumentFormat targetFormat =
        converter.getFormatRegistry().getFormatByExtension(outputFormat);
    if (targetFormat == null) {
      return error(HttpStatus.BAD_REQUEST, "The output format is not supported.");
    }

    // Here, we could have a dedicated service that would convert document
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      final DocumentConverter jobConverter = converterFor(landscape, removeHeaderFooter);
      ConversionJob job =
          source(jobConverter, inputFile).to(baos).as(targetFormat);

      // The PDF options only apply to a PDF output: the job refuses them with another format.
      final PdfOptions pdfOptions = pdfOptions(pdfPreset, pageRange);
      if (pdfOptions != null && isPdf(targetFormat)) {
        job = job.with(pdfOptions);
      }

      // Without the password, the conversion of a protected document fails with a
      // PasswordProtectedException.
      if (StringUtils.isNotBlank(password)) {
        job = job.password(password);
      }

      job.execute();

      final HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType(targetFormat.getMediaType()));
      // The name comes from the request: ContentDisposition quotes and encodes it, so that a
      // quote or a line break in the name cannot change the header.
      headers.setContentDisposition(
          ContentDisposition.attachment()
              .filename(
                  FileUtils.getBaseName(String.valueOf(inputFile.getOriginalFilename()))
                      + "."
                      + targetFormat.getExtension(),
                  StandardCharsets.UTF_8)
              .build());
      return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);

    } catch (IllegalArgumentException e) {
      // Options that cannot be used together, or a page range that is not one.
      return error(HttpStatus.BAD_REQUEST, "These options cannot be used: " + e.getMessage());
    } catch (OfficeException | IOException e) {
      return conversionError(inputFile, StringUtils.isNotBlank(password), e);
    }
  }

  // The upload is refused before the conversion is called: these answer as the conversion does,
  // with a message that the page shows.
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  /* default */ ResponseEntity<String> tooLarge() {
    return error(HttpStatus.CONTENT_TOO_LARGE, "The document is too large.");
  }

  @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
  /* default */ ResponseEntity<String> notAnUpload() {
    return error(HttpStatus.BAD_REQUEST, "Please select a file to upload.");
  }

  // An error status with its message, as plain text: the page writes it as text, never as HTML,
  // since it may hold the name of the uploaded file.
  private static ResponseEntity<String> error(final HttpStatus status, final String message) {
    return ResponseEntity.status(status).contentType(TEXT).body(message);
  }

  // The converter of the starter does the plain conversions. Filters belong to a converter, not
  // to a conversion, so a conversion that needs some gets a converter of its own, on the same
  // office manager and with the same formats. Building one is cheap: the office processes belong
  // to the manager. Note that such a converter does not have the jodconverter.pdf.* options of
  // the application properties, which the starter only gives to the converters it creates.
  private DocumentConverter converterFor(final boolean landscape, final boolean removeHeaderFooter) {

    final List<Filter> filters = new ArrayList<>();
    if (removeHeaderFooter) {
      // The name of the sheet at the top of each page and "Page 1" at the bottom, which the
      // office adds to a CSV file or to a spreadsheet without page setup.
      filters.add(new HeaderFooterRemoverFilter());
    }
    if (landscape) {
      filters.add(new PageOrientationFilter());
    }
    if (filters.isEmpty()) {
      return converter;
    }

    return LocalConverter.builder()
        .officeManager(officeManager)
        .formatRegistry(converter.getFormatRegistry())
        .filterChain(filters.toArray(new Filter[0]))
        .build();
  }

  // The uploaded document, with the format its name tells. A stream has no name: without the
  // format the office guesses from the content, and takes a CSV file for a text, not for a
  // spreadsheet. When the extension is unknown, the office is left to guess.
  private static ConversionJobWithSourceSpecified source(
      final DocumentConverter converter, final MultipartFile inputFile) throws IOException {

    final ConversionJobWithOptionalSourceFormatUnspecified job =
        converter.convert(inputFile.getInputStream());
    final String extension =
        FileUtils.getExtension(String.valueOf(inputFile.getOriginalFilename()));
    final DocumentFormat sourceFormat =
        StringUtils.isBlank(extension)
            ? null
            : converter.getFormatRegistry().getFormatByExtension(extension);
    return sourceFormat == null ? job : job.as(sourceFormat);
  }

  // Builds the PDF options of the form, or returns null when none is asked for.
  private static PdfOptions pdfOptions(final String preset, final String pageRange) {

    final PdfOptions.Builder builder;
    if (StringUtils.isBlank(preset)) {
      if (StringUtils.isBlank(pageRange)) {
        return null;
      }
      // As soon as one option is set, the office no longer uses the PDF settings of its own
      // configuration: tagged is the one worth keeping.
      builder = PdfOptions.builder().tagged(true);
    } else {
      builder =
          switch (preset) {
            case "archive" -> PdfOptions.archive().toBuilder();
            case "accessible" -> PdfOptions.accessible().toBuilder();
            case "compact" -> PdfOptions.compact().toBuilder();
            default -> throw new IllegalArgumentException("unknown PDF preset " + preset);
          };
    }

    if (StringUtils.isNotBlank(pageRange)) {
      builder.pages(pages -> pages.range(pageRange.trim()));
    }
    return builder.build();
  }

  private static boolean isPdf(final DocumentFormat format) {
    return DefaultDocumentFormatRegistry.PDF.getMediaType().equals(format.getMediaType());
  }

  // What the user is told: the reason when it is about the password, which the user can fix, and
  // otherwise that the conversion failed. The details are for the log.
  private static ResponseEntity<String> conversionError(
      final MultipartFile inputFile, final boolean withPassword, final Exception exception) {

    LOGGER.error("Could not convert {}", inputFile.getOriginalFilename(), exception);

    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof PasswordProtectedException) {
        // The message of the exception says whether the password was missing or wrong.
        return error(
            HttpStatus.BAD_REQUEST,
            String.valueOf(cause.getMessage()).startsWith("Wrong password")
                ? "The password is not the one of the document."
                : "The document is protected: give its password in the options.");
      }
    }
    // The office does not always say that a password is wrong: with some documents it only
    // fails to open them.
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Could not convert the file "
            + inputFile.getOriginalFilename()
            + (withPassword ? ". If it is protected, check its password." : "."));
  }
}
