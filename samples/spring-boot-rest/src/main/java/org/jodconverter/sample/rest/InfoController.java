package org.jodconverter.sample.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.office.OfficeInfo;
import org.jodconverter.local.task.OfficeInfoTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells which office the application converts with: its name, its version, and the filters it has
 * to load and to store documents.
 */
@RestController
public class InfoController {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfoController.class);

    private final OfficeManager officeManager;

    // The office is asked once: the answer does not change while the application runs, and
    // asking costs a few hundred calls to the office.
    private volatile OfficeInfo info;

    /**
     * Creates a new controller.
     *
     * @param officeManager The manager of the office to describe.
     */
    public InfoController(final OfficeManager officeManager) {
        this.officeManager = officeManager;
    }

    @Operation(
            summary =
                    "Returns the name and the version of the office that converts the documents,"
                            + " and its import and export filters.")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "The description of the office."),
                    @ApiResponse(responseCode = "503", description = "The office is not available.")
            })
    @GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    /* default */ ResponseEntity<Object> info() {

        OfficeInfo result = info;
        if (result == null) {
            try {
                result = OfficeInfoTask.query(officeManager);
                info = result;
            } catch (OfficeException ex) {
                LOGGER.error("Could not get the description of the office", ex);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("The office is not available.");
            }
        }
        return ResponseEntity.ok(result);
    }
}
