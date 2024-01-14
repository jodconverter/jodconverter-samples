package org.jodconverter.sample.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.util.FileUtils;
import org.jodconverter.core.util.StringUtils;
import org.jodconverter.local.LocalConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller that will process conversion requests. The mapping is the same as LibreOffice Online
 * (/lool/convert-to) so we can use the jodconverter-remote module to send request to this
 * controller. This controller does the same as LibreOffice Online, and also support custom
 * conversions through filters and custom load/store properties.
 */
@Controller
@RequestMapping("/lool/convert-to")
public class ConverterController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConverterController.class);

    private final OfficeManager officeManager;

    @Autowired
    private ParameterDecoder parameterDecoder;

    /**
     * Creates a new controller.
     *
     * @param officeManager The manager used to execute conversions.
     */
    public ConverterController(final OfficeManager officeManager) {
        super();

        this.officeManager = officeManager;
    }

    @Operation(
            summary =
                    "Converts the incoming document to the specified format (provided as request param)"
                            + " and returns the converted document.")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Document converted successfully."),
                    @ApiResponse(
                            responseCode = "400",
                            description = "The input document or output format is missing."),
                    @ApiResponse(responseCode = "500", description = "An unexpected error occurred.")
            })
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        /* default */ Object convertToUsingParam(
            @Parameter(description = "The input document to convert.", required = true)
            @RequestParam("data") final MultipartFile inputFile,
            @Parameter(
                    description = "The document format to convert the input document to.",
                    required = true)
            @RequestParam(name = "format") final String convertToFormat,
            @Parameter(description = "The custom options to apply to the conversion.")
            @RequestParam(required = false) final Map<String, String> parameters) {

        LOGGER.debug("convertUsingRequestParam > Converting file to {}", convertToFormat);
        return convert(inputFile, convertToFormat, parameters);
    }

    @Operation(
            summary =
                    "Converts the incoming document to the specified format (provided as path param)"
                            + " and returns the converted document.")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Document converted successfully."),
                    @ApiResponse(
                            responseCode = "400",
                            description = "The input document or output format is missing."),
                    @ApiResponse(responseCode = "500", description = "An unexpected error occurred.")
            })
    @PostMapping(
            value = "/{format}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        /* default */ Object convertToUsingPath(
            @Parameter(description = "The input document to convert.", required = true)
            @RequestParam("data") final MultipartFile inputFile,
            @Parameter(
                    description = "The document format to convert the input document to.",
                    required = true)
            @PathVariable(name = "format") final String convertToFormat,
            @Parameter(description = "The custom options to apply to the conversion.")
            @RequestParam(required = false) final Map<String, String> parameters) {

        LOGGER.debug("convertUsingPathVariable > Converting file to {}", convertToFormat);
        return convert(inputFile, convertToFormat, parameters);
    }

    private ResponseEntity<Object> convert(
            final MultipartFile inputFile,
            final String outputFormat,
            final Map<String, String> parameters) {

        if (inputFile.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (StringUtils.isBlank(outputFormat)) {
            return ResponseEntity.badRequest().build();
        }

        // Here, we could have a dedicated service that would convert document
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            final DocumentFormat targetFormat =
                    DefaultDocumentFormatRegistry.getFormatByExtension(outputFormat);
            Assert.notNull(targetFormat, "targetFormat must not be null");

            // Decode the parameters to load and store properties.
            final Map<String, Object> loadProperties =
                    new HashMap<>(LocalConverter.DEFAULT_LOAD_PROPERTIES);
            final Map<String, Object> storeProperties = new HashMap<>();
            parameterDecoder.decodeParameters(parameters, loadProperties, storeProperties);

            // Create a converter with the properties.
            final DocumentConverter converter =
                    LocalConverter.builder()
                            .officeManager(officeManager)
                            .loadProperties(loadProperties)
                            .storeProperties(storeProperties)
                            .build();

            // Convert...
            converter.convert(inputFile.getInputStream()).to(baos).as(targetFormat).execute();

            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(targetFormat.getMediaType()));
            headers.add(
                    "Content-Disposition",
                    "attachment; filename="
                            + FileUtils.getBaseName(inputFile.getOriginalFilename())
                            + "."
                            + targetFormat.getExtension());
            return ResponseEntity.ok().headers(headers).body(baos.toByteArray());

        } catch (OfficeException | IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex);
        }
    }
}
