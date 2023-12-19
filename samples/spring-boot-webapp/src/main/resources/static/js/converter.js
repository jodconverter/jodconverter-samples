function dismissError() {
    const errorDiv = document.getElementById("errorMessage")
    errorDiv.replaceChildren();
}

function showError(errorMessage) {

    if (errorMessage) {
        const alert =
            '<div class="alert alert-danger alert-dismissible fade show" role="alert">' +
            `<strong>Error!</strong> ${errorMessage}` +
            '<button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>' +
            '</div>'
        // Replace current HTML of the error message div
        const errorDiv = document.getElementById("errorMessage")
        errorDiv.innerHTML = alert;
    } else {
        // Remove older message from the error message div
        dismissError();
    }
}

function setElementDisabled(eltId, disabled) {
    document.getElementById(eltId).disabled = disabled;
}

function onInputFileChange() {

    // Remove any previous error message
    dismissError();

    // Extract the filename from the formFile input.
    const inputValue = document.getElementById("inputFile").value;
    const filename = inputValue ? inputValue.split('\\').pop() : "";

    // Search for an extension in the filename
    // See https://stackoverflow.com/a/680982/4336562
    const re = /(?:\.([^.]+))?$/;
    const ext = re.exec(filename)[1];
    if (ext === undefined) {
        setElementDisabled("outputFormat", true);
        setElementDisabled("formGo", true);
        showError("No extension found in the source file name.");
        return false;
    }

    // Retrieve the input family for this extension
    const family = importFormatTable[ext.toLowerCase()];

    // Input format not supported ? Inform the user.
    if (family === undefined) {
        setElementDisabled("outputFormat", true);
        setElementDisabled("formGo", true);
        showError("Conversion from extension <b>" + ext + "</b> is not supported.");
        return false;
    }

    // Get the supported output formats for the input format family
    const formats = exportFormatTable[family];

    // Populate the drop down list of supported output formats
    const outputFormat = document.getElementById("outputFormat");
    // Clear the combo, keeping the first element (Choose type).
    while (outputFormat.options.length !== 1) {
        outputFormat.remove(1);
    }
    formats.forEach((format) => {
        if (format.value !== ext) {
            outputFormat.add(format);
        }
    });

    setElementDisabled("outputFormat", false);
    setElementDisabled("formGo", false);
}