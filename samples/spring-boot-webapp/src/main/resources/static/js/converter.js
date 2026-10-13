function dismissError() {
    const errorDiv = document.getElementById("errorMessage")
    errorDiv.replaceChildren();
}

// Shows a message as text: it may hold the name of the chosen file, which must not be read as
// HTML.
function showError(errorMessage) {

    if (errorMessage) {
        const alert = document.createElement("div");
        alert.className = "alert alert-danger alert-dismissible fade show";
        alert.setAttribute("role", "alert");

        const title = document.createElement("strong");
        title.textContent = "Error!";
        const close = document.createElement("button");
        close.type = "button";
        close.className = "btn-close";
        close.setAttribute("data-bs-dismiss", "alert");
        close.setAttribute("aria-label", "Close");
        alert.append(title, " " + errorMessage, close);

        // Replace the current content of the error message div
        document.getElementById("errorMessage").replaceChildren(alert);
    } else {
        // Remove older message from the error message div
        dismissError();
    }
}

function setElementDisabled(eltId, disabled) {
    document.getElementById(eltId).disabled = disabled;
}

// The PDF options are sent only when the output is a PDF document: the fields of a disabled
// fieldset are not part of the form.
function onOutputFormatChange() {
    setElementDisabled("pdfOptions", document.getElementById("outputFormat").value !== "pdf");
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
        setElementDisabled("pdfOptions", true);
        showError("No extension found in the source file name.");
        return false;
    }

    // Retrieve the input family for this extension
    const family = importFormatTable[ext.toLowerCase()];

    // Input format not supported ? Inform the user.
    if (family === undefined) {
        setElementDisabled("outputFormat", true);
        setElementDisabled("formGo", true);
        setElementDisabled("pdfOptions", true);
        showError("Conversion from extension " + ext + " is not supported.");
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
    onOutputFormatChange();
}

// Saves a file that the page holds, as a download of the browser would.
function saveFile(blob, filename) {
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    document.body.append(link);
    link.click();
    link.remove();
    // The browser has started to save the file: its content can be released.
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

// Sends the form from the page, instead of letting the browser leave the page for the answer.
// The page stays where it is: it shows the message of the server when the conversion is refused
// or fails, and saves the converted document otherwise.
async function onFormSubmit(event) {

    event.preventDefault();
    dismissError();

    const form = event.target;
    const button = document.getElementById("formGo");
    const buttonText = button.textContent;

    // The converted document is named after the chosen one.
    const filename = document.getElementById("inputFile").files[0].name;
    const dot = filename.lastIndexOf(".");
    const convertedName = (dot > 0 ? filename.substring(0, dot) : filename)
        + "." + document.getElementById("outputFormat").value;

    // The fields are read before the button is disabled; the fields of a disabled fieldset
    // (the PDF options, when the output is not a PDF document) are not among them.
    const body = new FormData(form);
    button.disabled = true;
    button.textContent = "Converting...";
    try {
        const response = await fetch(form.action, {method: "POST", body: body});
        if (response.ok) {
            saveFile(await response.blob(), convertedName);
        } else {
            // The server answers an error with a message as plain text.
            const message = response.headers.get("Content-Type")?.startsWith("text/plain")
                ? await response.text()
                : "";
            showError(message || "The conversion failed (HTTP " + response.status + ").");
        }
    } catch (error) {
        // No answer at all: the server is stopped, or it closed the connection on an upload
        // that is too large.
        showError("The server did not answer. Is it running, and is the document under the size limit?");
    } finally {
        button.textContent = buttonText;
        button.disabled = false;
    }
}

document.getElementById("converterForm").addEventListener("submit", onFormSubmit);
