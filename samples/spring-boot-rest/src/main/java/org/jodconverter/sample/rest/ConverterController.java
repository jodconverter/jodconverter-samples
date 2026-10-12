package org.jodconverter.sample.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.document.DocumentFormatRegistry;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.util.FileUtils;
import org.jodconverter.core.util.StringUtils;
import org.jodconverter.local.LocalConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    // The load properties that decide what the office may do on its own while it loads a
    // document: update its links, run its macros.
    private static final Set<String> RESTRICTED_LOAD_PROPERTIES =
            Set.of("UpdateDocMode", "MacroExecutionMode");

    private final OfficeManager officeManager;
    private final DocumentFormatRegistry formatRegistry;

    @Autowired
    private ParameterDecoder parameterDecoder;

    /**
     * Creates a new controller.
     *
     * @param officeManager The manager used to execute conversions.
     * @param formatRegistry The registry of the document formats, with the options of the
     *     jodconverter.document-formats properties.
     */
    public ConverterController(
            final OfficeManager officeManager, final DocumentFormatRegistry formatRegistry) {
        super();

        this.officeManager = officeManager;
        this.formatRegistry = formatRegistry;
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
                            description =
                                    "The input document or the output format is missing, the format"
                                            + " is not supported, or a parameter is not allowed."),
                    @ApiResponse(responseCode = "500", description = "The conversion failed.")
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
                            description =
                                    "The input document or the output format is missing, the format"
                                            + " is not supported, or a parameter is not allowed."),
                    @ApiResponse(responseCode = "500", description = "The conversion failed.")
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
            return badRequest("The input document is missing or empty.");
        }

        if (StringUtils.isBlank(outputFormat)) {
            return badRequest("The output format is missing.");
        }

        final DocumentFormat targetFormat = formatRegistry.getFormatByExtension(outputFormat);
        if (targetFormat == null) {
            return badRequest("The output format is not supported: " + outputFormat);
        }

        // Decode the parameters to load and store properties.
        final Map<String, Object> customLoadProperties = new HashMap<>();
        final Map<String, Object> storeProperties = new HashMap<>();
        parameterDecoder.decodeParameters(parameters, customLoadProperties, storeProperties);

        // A caller chooses how its document is read and written, not whether the links and the
        // macros of the document are trusted: these load properties stay what the converter
        // sets by default.
        for (final String name : customLoadProperties.keySet()) {
            if (RESTRICTED_LOAD_PROPERTIES.stream().anyMatch(name::equalsIgnoreCase)) {
                return badRequest("The load property " + name + " cannot be set by a request.");
            }
        }
        final Map<String, Object> loadProperties =
                new HashMap<>(LocalConverter.DEFAULT_LOAD_PROPERTIES);
        loadProperties.putAll(customLoadProperties);

        // Here, we could have a dedicated service that would convert document
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Create a converter with the properties.
            final DocumentConverter converter =
                    LocalConverter.builder()
                            .officeManager(officeManager)
                            .formatRegistry(formatRegistry)
                            .loadProperties(loadProperties)
                            .storeProperties(storeProperties)
                            .build();

            // Convert...
            converter.convert(inputFile.getInputStream()).to(baos).as(targetFormat).execute();

            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(targetFormat.getMediaType()));
            // The name comes from the request: ContentDisposition quotes and encodes it, so that
            // a quote or a line break in the name cannot change the header.
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(
                                    FileUtils.getBaseName(
                                                    String.valueOf(inputFile.getOriginalFilename()))
                                            + "."
                                            + targetFormat.getExtension(),
                                    StandardCharsets.UTF_8)
                            .build());
            return ResponseEntity.ok().headers(headers).body(baos.toByteArray());

        } catch (OfficeException | IOException ex) {
            // The details go to the log, not to the caller: an exception may tell paths and
            // other internals of the server.
            LOGGER.error("Could not convert {}", inputFile.getOriginalFilename(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("The document could not be converted.");
        }
    }

    private static ResponseEntity<Object> badRequest(final String message) {
        return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(message);
    }
}
