package org.jodconverter.sample.rest;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import org.jodconverter.core.document.DocumentFamily;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.document.DocumentFormatRegistry;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.util.FileUtils;
import org.jodconverter.local.ImageFormat;
import org.jodconverter.local.LocalConverter;

/**
 * Makes images of documents: a preview of the first page of any document, and an image of each
 * slide of a presentation or page of a drawing.
 */
@RestController
public class PreviewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreviewController.class);

    private static final MediaType TEXT = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    // The sizes of the images that can be asked for, in pixels.
    private static final int MIN_WIDTH = 16;
    private static final int MAX_WIDTH = 2000;

    private final LocalConverter converter;
    private final DocumentFormatRegistry formatRegistry;

    /**
     * Creates a new controller.
     *
     * @param officeManager The manager of the office processes, which make the images.
     * @param formatRegistry The registry of the document formats.
     */
    public PreviewController(
            final OfficeManager officeManager, final DocumentFormatRegistry formatRegistry) {
        // The export of the pages is a method of the LocalConverter, not of the DocumentConverter
        // interface that the starter gives: a converter of this controller, on the same office
        // processes.
        this.converter =
                LocalConverter.builder()
                        .officeManager(officeManager)
                        .formatRegistry(formatRegistry)
                        .build();
        this.formatRegistry = formatRegistry;
    }

    @Operation(
            summary =
                    "Returns a PNG image of the first page of the document, of the given width: a"
                            + " text, a spreadsheet, a presentation or a drawing.")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "The image of the first page."),
                    @ApiResponse(
                            responseCode = "400",
                            description = "The document is missing or of an unknown type, or the width is out of range."),
                    @ApiResponse(responseCode = "500", description = "The image could not be made.")
            })
    @PostMapping(
            value = "/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = {MediaType.IMAGE_PNG_VALUE, MediaType.TEXT_PLAIN_VALUE})
    /* default */ ResponseEntity<?> preview(
            @Parameter(description = "The document.", required = true)
            @RequestParam("data") final MultipartFile document,
            @Parameter(description = "The width of the image, in pixels (16 to 2000).")
            @RequestParam(name = "width", defaultValue = "320") final int width) {

        final ResponseEntity<String> refusal = check(document, width);
        if (refusal != null) {
            return refusal;
        }
        final DocumentFormat source = sourceFormat(document);
        if (source == null) {
            return error(HttpStatus.BAD_REQUEST, "The type of the document is not supported.");
        }

        // The export of a document as a PNG image is the image of its first page, at the size of
        // the page. Its width is set here, in Java, rather than by the office: the export filters
        // need the width and the height together, and the height that keeps the proportions of
        // the page is only known once the page is drawn.
        try (ByteArrayOutputStream page = new ByteArrayOutputStream()) {
            converter
                    .convert(document.getInputStream())
                    .as(source)
                    .to(page)
                    .as(formatRegistry.getFormatByExtension("png"))
                    .execute();

            final byte[] image = scale(ImageIO.read(new ByteArrayInputStream(page.toByteArray())), width);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);

        } catch (OfficeException | IOException e) {
            LOGGER.error("Could not make the preview of {}", document.getOriginalFilename(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "The preview could not be made.");
        }
    }

    @Operation(
            summary =
                    "Returns a ZIP file with an image of each slide of a presentation, or of each"
                            + " page of a drawing.")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "The images, in a ZIP file."),
                    @ApiResponse(
                            responseCode = "400",
                            description =
                                    "The document is missing or is not a presentation or a drawing,"
                                            + " or an option is out of range."),
                    @ApiResponse(responseCode = "500", description = "The images could not be made.")
            })
    @PostMapping(
            value = "/pages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = {"application/zip", MediaType.TEXT_PLAIN_VALUE})
    /* default */ ResponseEntity<?> pages(
            @Parameter(description = "The presentation or the drawing.", required = true)
            @RequestParam("data") final MultipartFile document,
            @Parameter(description = "The width of the images, in pixels (16 to 2000).")
            @RequestParam(name = "width", defaultValue = "320") final int width,
            @Parameter(description = "The format of the images: png (default), jpg, svg or webp.")
            @RequestParam(name = "format", defaultValue = "png") final String format,
            @Parameter(description = "The pages to export, such as 1-3,7; all of them by default.")
            @RequestParam(name = "pages", required = false) final String pages) {

        final ResponseEntity<String> refusal = check(document, width);
        if (refusal != null) {
            return refusal;
        }
        final DocumentFormat source = sourceFormat(document);
        if (source == null
                || source.getInputFamily() != DocumentFamily.PRESENTATION
                        && source.getInputFamily() != DocumentFamily.DRAWING) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "Only the slides of a presentation and the pages of a drawing can be exported"
                            + " as images.");
        }
        final ImageFormat imageFormat;
        try {
            imageFormat = ImageFormat.of(format);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "The image format " + format + " is not supported.");
        }

        // The office writes the images in a directory: a temporary one, deleted once the images
        // are in the ZIP file. The document is written there too, under the name of its format,
        // which tells the office what it is.
        File directory = null;
        try {
            directory = Files.createTempDirectory("pages").toFile();
            final File input = new File(directory, "document." + source.getExtension());
            document.transferTo(input);

            final List<File> images =
                    converter
                            .exportPages(input)
                            .to(new File(directory, "images"))
                            .as(imageFormat)
                            .width(width)
                            .pages(pages)
                            .baseName("page")
                            .execute();

            final String name = FileUtils.getBaseName(String.valueOf(document.getOriginalFilename()));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(
                            "Content-Disposition",
                            ContentDisposition.attachment()
                                    .filename(name + "-pages.zip", StandardCharsets.UTF_8)
                                    .build()
                                    .toString())
                    .body(zip(images));

        } catch (IllegalArgumentException e) {
            // A range of pages that is not one.
            return error(HttpStatus.BAD_REQUEST, "These options cannot be used: " + e.getMessage());
        } catch (OfficeException e) {
            // A page beyond the last one is only known once the office has loaded the document,
            // and it is the request that is wrong: its message says how many pages there are.
            if (String.valueOf(e.getMessage()).contains("does not exist: the document has")) {
                return error(HttpStatus.BAD_REQUEST, e.getMessage() + ".");
            }
            LOGGER.error("Could not export the pages of {}", document.getOriginalFilename(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "The images could not be made.");
        } catch (IOException e) {
            LOGGER.error("Could not export the pages of {}", document.getOriginalFilename(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "The images could not be made.");
        } finally {
            if (directory != null) {
                FileUtils.deleteQuietly(directory);
            }
        }
    }

    // A request without the document, or with a width that is not a number, is refused before the
    // endpoint is called: it is answered here, with a message as the other refusals are.
    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    /* default */ ResponseEntity<String> notAnUpload() {
        return error(HttpStatus.BAD_REQUEST, "The document is missing or empty.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    /* default */ ResponseEntity<String> notANumber(final MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "The " + e.getName() + " must be a number.");
    }

    // A refusal for what both endpoints need, or null.
    private static ResponseEntity<String> check(final MultipartFile document, final int width) {

        if (document.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "The document is missing or empty.");
        }
        if (width < MIN_WIDTH || width > MAX_WIDTH) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "The width must be from " + MIN_WIDTH + " to " + MAX_WIDTH + " pixels.");
        }
        return null;
    }

    // The format of the document, from the extension of its name, or null when it is unknown.
    private DocumentFormat sourceFormat(final MultipartFile document) {
        final String extension = FileUtils.getExtension(String.valueOf(document.getOriginalFilename()));
        return extension == null ? null : formatRegistry.getFormatByExtension(extension);
    }

    // The image at the given width, with the proportions of the original, as a PNG image. The
    // background is white: the image of a page may have transparent parts.
    private static byte[] scale(final BufferedImage page, final int width) throws IOException {

        if (page == null) {
            throw new IOException("The office did not make a readable image");
        }
        final int height = Math.max(1, Math.round(page.getHeight() * (float) width / page.getWidth()));
        final BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(page, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        final ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(scaled, "png", png);
        return png.toByteArray();
    }

    private static byte[] zip(final List<File> images) throws IOException {

        final ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream entries = new ZipOutputStream(zip)) {
            for (final File image : images) {
                entries.putNextEntry(new ZipEntry(image.getName()));
                Files.copy(image.toPath(), entries);
                entries.closeEntry();
            }
        }
        return zip.toByteArray();
    }

    private static ResponseEntity<String> error(final HttpStatus status, final String message) {
        return ResponseEntity.status(status).contentType(TEXT).body(message);
    }
}
