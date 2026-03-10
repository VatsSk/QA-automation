/* --- JavaScript Logic --- */
let blockCounter = 0;

// Add the first block on page load
document.addEventListener('DOMContentLoaded', () => {
    addBlock();
});

// 1. Function to dynamically add a block
function addBlock() {
    blockCounter++;
    const blockId = `block-${blockCounter}`;

    const container = document.getElementById('blocks-container');
    const block = document.createElement('div');
    block.className = 'test-block';
    block.id = blockId;

    block.innerHTML = `
        <div class="block-header">
            <h3>Test Block ${blockCounter}</h3>
            <button class="btn-remove" onclick="removeBlock('${blockId}')" title="Remove Block">&times;</button>
        </div>

        <div class="form-group">
            <label>Select Type</label>
            <select class="type-selector" onchange="toggleFields('${blockId}')">
                <option value="url">URL</option>
                <option value="model">Model</option>
                <option value="statement">Test Result Statement</option>
                <option value="nav_url">Navigation URL</option>
                <option value="nav_modal">Navigation Modal</option>
            </select>
        </div>

        <div id="fields-${blockId}" class="dynamic-fields">
            ${getUrlFieldsHTML()}
        </div>
    `;
    container.appendChild(block);
}

// 2. Function to remove a block
function removeBlock(blockId) {
    const block = document.getElementById(blockId);
    if (block) {
        block.remove();
    }
}

// 3. Function to switch inputs based on dropdown selection
function toggleFields(blockId) {
    const block = document.getElementById(blockId);
    const type = block.querySelector('.type-selector').value;
    const fieldsContainer = document.getElementById(`fields-${blockId}`);

    if (type === 'url') {
        fieldsContainer.innerHTML = getUrlFieldsHTML();
    } else if (type === 'model') {
        fieldsContainer.innerHTML = getModelFieldsHTML();
    } else if (type === 'statement') {
        fieldsContainer.innerHTML = getStatementFieldsHTML();
    } else if (type === 'nav_url') {
        fieldsContainer.innerHTML = getNavUrlFieldsHTML();
    } else if (type === 'nav_modal') {
        fieldsContainer.innerHTML = getNavModalFieldsHTML();
    }
}

// HTML templates for dynamic inputs
function getUrlFieldsHTML() {
    return `
        <div class="form-group">
            <label>Enter URL</label>
            <input type="url" class="input-target" placeholder="http://example.com" required>
        </div>
        <div class="form-group">
            <label>Upload Test Cases (CSV)</label>
            <input type="file" class="input-csv" accept=".csv">
        </div>
    `;
}

function getModelFieldsHTML() {
    return `
        <div class="form-group">
            <label>Enter Model ID</label>
            <input type="text" class="input-target" placeholder="MODEL_123" required>
        </div>
        <div class="form-group">
            <label>Upload Test Cases (CSV)</label>
            <input type="file" class="input-csv" accept=".csv">
        </div>
    `;
}

// NEW HTML TEMPLATE FOR STATEMENT
function getStatementFieldsHTML() {
    return `
        <div class="form-group">
            <label>Enter Test Result Statement</label>
            <input type="text" class="input-statement" placeholder="e.g., Verify successful login dashboard load" required>
        </div>
    `;
}

// NEW HTML TEMPLATE FOR NAVIGATION URL (url only)
function getNavUrlFieldsHTML() {
    return `
        <div class="form-group">
            <label>Navigation URL</label>
            <input type="url" class="input-target" placeholder="http://example.com" required>
        </div>
    `;
}

// NEW HTML TEMPLATE FOR NAVIGATION MODAL (openerCss only)
function getNavModalFieldsHTML() {
    return `
        <div class="form-group">
            <label>Navigation Opener CSS (openerCss)</label>
            <input type="text" class="input-target" placeholder=".nav-item-class or #menu > li:nth-child(2)" required>
            <small class="muted">Provide the CSS selector that will be clicked to open the navigation/modal</small>
        </div>
    `;
}

// 4. Function to collect data and generate the payload
async function runTests() {
    const blocks = document.querySelectorAll('.test-block');
    const payload = { tests: [] };

    // 1. Create a new FormData object to hold JSON, Files, AND Statements
    const formData = new FormData();

    for (let index = 0; index < blocks.length; index++) {
        const block = blocks[index];
        const type = block.querySelector('.type-selector').value;

        // === HANDLE "STATEMENT" TYPE SEPARATELY ===
        if (type === 'statement') {
            const statementText = block.querySelector('.input-statement').value;
            // Append statements with unique key if you may have multiple statements
            // formData.append(`testResultStatement_${index}`, statementText);
            formData.append("testResultStatement", statementText);
            // console.log(`\n📝 --- Captured Statement (Block ${index + 1}) ---`);
            console.log(statementText);

            continue; // Skip the file and JSON logic below for this block
        }
        // =================================================

        // For other types we rely on .input-target for the main value (url, model id, or CSS)
        const targetElem = block.querySelector('.input-target');
        const targetValue = targetElem ? targetElem.value : null;

        const fileInput = block.querySelector('.input-csv');

        let fileKey = null;
        let fileName = null;

        // 2. Access the actual file (if any)
        if (fileInput && fileInput.files.length > 0) {
            const actualFile = fileInput.files[0];
            fileName = actualFile.name;

            // READ AND LOG CSV CONTENT (optional for debug)
            try {
                const csvText = await actualFile.text();
                console.log(`\n📄 --- Content of ${fileName} (Block ${index + 1}) ---`);
                console.log(csvText);
                console.log(`-----------------------------------------`);
            } catch (err) {
                console.error(`Error reading ${fileName}:`, err);
            }

            // Create a unique key for this file (e.g., "file_0")
            fileKey = `file_${index}`;

            // Append the physical file to the FormData object
            formData.append(fileKey, actualFile);
        }

        // 3. Add to our JSON structure (frontend naming -> backend mapping)
        if (type === 'url') {
            payload.tests.push({
                type: "URL",
                url: targetValue,
                fileName: fileName,
                fileKey: fileKey
            });
        } else if (type === 'model') {
            payload.tests.push({
                type: "MODAL",
                openerCss: targetValue,
                fileName: fileName,
                fileKey: fileKey
            });
        } else if (type === 'nav_url') {
            payload.tests.push({
                type: "NAV_URL",
                url: targetValue
            });
        } else if (type === 'nav_modal') {
            payload.tests.push({
                type: "NAV_MODAL",
                openerCss: targetValue
            });
        }
    }

    console.log("\n📦 Collected JSON Payload (attached to FormData):", JSON.stringify(payload, null, 2));

    // 4. Append the JSON payload to the FormData as a Blob
    formData.append('testConfiguration', new Blob([JSON.stringify(payload)], {
        type: "application/json"
    }));

    // 5. Send to Spring Boot backend
    try {
        const response = await fetch('/runner/run-auth', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();
        console.log('Success:', data);
        alert("Tests submitted successfully! Check console for contents.");
    } catch (error) {
        console.error('Error submitting tests:', error);
        alert("Failed to submit tests. Check console.");
    }
}