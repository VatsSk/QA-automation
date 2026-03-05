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
                    <input type="file" class="input-csv" accept=".csv" required>
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
                    <input type="file" class="input-csv" accept=".csv" required>
                </div>
            `;
}

// 4. Function to collect data and generate the JSON payload
// function runTests() {
//     const blocks = document.querySelectorAll('.test-block');
//     const payload = { tests: [] };
//
//     blocks.forEach(block => {
//         const type = block.querySelector('.type-selector').value;
//         const targetValue = block.querySelector('.input-target').value;
//         const fileInput = block.querySelector('.input-csv');
//         console.log("csv file "+fileInput)
//
//         // Extract file name if a file is uploaded, otherwise set to a placeholder/null
//         const fileName = fileInput.files.length > 0 ? fileInput.files[0].name : "no_file_uploaded.csv";
//
//         if (type === 'url') {
//             payload.tests.push({
//                 type: "URL",
//                 url: targetValue,
//                 file: fileName
//             });
//         } else if (type === 'model') {
//             payload.tests.push({
//                 type: "MODAL",
//                 id: targetValue,
//                 file: fileName
//             });
//         }
//     });
//
//     // Log output to console to verify
//     console.log("Collected JSON Payload:");
//     console.log(JSON.stringify(payload, null, 2));
//     alert("Tests initiated! Check the browser console to see the JSON payload.");
//
//     // 5. Send to Spring Boot backend API (Simulated)
//     /*
//     fetch('/run-tests', {
//         method: 'POST',
//         headers: {
//             'Content-Type': 'application/json'
//         },
//         body: JSON.stringify(payload)
//     })
//     .then(response => response.json())
//     .then(data => console.log('Success:', data))
//     .catch(error => console.error('Error:', error));
//     */
// }

async function runTests() {
    const blocks = document.querySelectorAll('.test-block');
    const payload = { tests: [] };

    // 1. Create a new FormData object to hold both JSON and Files
    const formData = new FormData();

    // Changed from .forEach() to a standard 'for' loop so 'await' works correctly
    for (let index = 0; index < blocks.length; index++) {
        const block = blocks[index];
        const type = block.querySelector('.type-selector').value;
        const targetValue = block.querySelector('.input-target').value;
        const fileInput = block.querySelector('.input-csv');

        let fileKey = null;
        let fileName = null;

        // 2. Access the actual file
        if (fileInput.files.length > 0) {
            const actualFile = fileInput.files[0];
            fileName = actualFile.name;

            // === NEW: READ AND LOG CSV CONTENT ===
            try {
                // Read the file content as text
                const csvText = await actualFile.text();

                console.log(`\n📄 --- Content of ${fileName} (Block ${index + 1}) ---`);
                console.log(csvText);
                console.log(`-----------------------------------------`);
            } catch (err) {
                console.error(`Error reading ${fileName}:`, err);
            }
            // =====================================

            // Create a unique key for this file (e.g., "file_0", "file_1")
            fileKey = `file_${index}`;

            // Append the physical file to the FormData object
            formData.append(fileKey, actualFile);
        }

        console.log("form data : "+formData)

        // 3. Add to our JSON structure
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
                id: targetValue,
                fileName: fileName,
                fileKey: fileKey
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
        alert("Tests submitted successfully! Check console for CSV contents.");
    } catch (error) {
        console.error('Error submitting tests:', error);
        alert("Failed to submit tests. Check console.");
    }
}