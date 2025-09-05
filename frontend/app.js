// Authentication Gatekeeper - Check for JWT token immediately
(function() {
    const token = localStorage.getItem('jwtToken');
    if (!token) {
        window.location.href = 'login.html';
        return;
    }
})();

// Get references to DOM elements
const jobForm = document.getElementById('jobForm');
const jdUrlInput = document.getElementById('jdUrlInput');
const resumeFileInput = document.getElementById('resumeFileInput');
const modelProvider = document.getElementById('modelProvider');
const modelName = document.getElementById('modelName');
const logOutput = document.getElementById('logOutput');

// Function to log messages to the output
function logMessage(message) {
    const timestamp = new Date().toLocaleTimeString();
    logOutput.textContent += `[${timestamp}] ${message}\n`;
}

// Handle model provider selection to show/hide appropriate models
modelProvider.addEventListener('change', function() {
    const openaiModels = document.getElementById('openaiModels');
    const anthropicModels = document.getElementById('anthropicModels');
    
    if (this.value === 'openai') {
        openaiModels.style.display = '';
        anthropicModels.style.display = 'none';
        // Reset to default option
        modelName.value = '';
    } else if (this.value === 'anthropic') {
        openaiModels.style.display = 'none';
        anthropicModels.style.display = '';
        // Reset to default option
        modelName.value = '';
    }
});

// Add event listener for form submission
jobForm.addEventListener('submit', async (event) => {
    try {
        // 1. Prevent default form submission
        event.preventDefault();
        
        // A. Get Inputs
        const jdUrl = jdUrlInput.value.trim();
        const file = resumeFileInput.files[0];
        
        if (!jdUrl) {
            logMessage('Error: Job URL is required');
            return;
        }
        
        if (!file) {
            logMessage('Error: Resume file is required');
            return;
        }
        
        // Get JWT token for API authentication
        const token = localStorage.getItem('jwtToken');
        if (!token) {
            window.location.href = 'login.html';
            return;
        }
        
        // B. Get Pre-signed URL
        logMessage('Step 1: Requesting secure upload URL...');
        
        const presignedResponse = await fetch('/api/v1/uploads/presigned-url', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                fileName: file.name,
                contentType: file.type
            })
        });
        
        if (!presignedResponse.ok) {
            throw new Error(`Failed to get presigned URL: ${presignedResponse.status}`);
        }
        
        const presignedData = await presignedResponse.json();
        const presignedUrl = presignedData.url;
        
        // C. Upload Directly to S3
        logMessage('Step 2: Uploading file directly to cloud storage...');
        
        const uploadResponse = await fetch(presignedUrl, {
            method: 'PUT',
            headers: {
                'Content-Type': file.type
            },
            body: file
        });
        
        if (!uploadResponse.ok) {
            throw new Error(`Failed to upload file: ${uploadResponse.status}`);
        }
        
        // D. Submit the Job
        logMessage('Step 3: Submitting job with file reference...');
        
        // Construct the final resumeUri (presigned URL without query parameters)
        const resumeUri = presignedUrl.split('?')[0];
        
        const response = await fetch('/api/v1/applications', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ 
                jdUrl: jdUrl,
                resumeUri: resumeUri,
                modelProvider: modelProvider.value,
                modelName: modelName.value || null
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        // Parse JSON response to get jobId
        const data = await response.json();
        const jobId = data.jobId;
        
        // Log success message with jobId
        logMessage(`✅ Job submitted successfully! Job ID: ${jobId}`);
        
        // Create EventSource connection for real-time status updates with token authentication
        const sse = new EventSource(`/api/v1/applications/${jobId}/stream?token=${encodeURIComponent(token)}`);
        
        // Handle SSE connection opening
        sse.onopen = function(event) {
            logMessage('🔗 SSE Connection Established. Waiting for status updates...');
        };
        
        // Handle incoming status messages
        sse.onmessage = async function(event) {
            try {
                // Parse the event data as JSON
                const statusData = JSON.parse(event.data);
                const status = statusData.status;
                
                // Log the new status
                logMessage(`📊 Status update: ${status}`);
                
                // Check if pipeline is finished
                if (status === 'COMPLETED' || status === 'FAILED') {
                    // If completed, fetch the generated artifact
                    if (status === 'COMPLETED') {
                        logMessage('Pipeline finished. Fetching final artifact...');
                        
                        try {
                            const artifactResponse = await fetch(`/api/v1/applications/${jobId}/artifact`, {
                                headers: {
                                    'Authorization': `Bearer ${token}`
                                }
                            });
                            
                            if (artifactResponse.ok) {
                                const artifactData = await artifactResponse.json();
                                const artifactOutput = document.getElementById('artifactOutput');
                                artifactOutput.value = artifactData.generatedText;
                            } else {
                                logMessage(`❌ Error fetching artifact: ${artifactResponse.status}`);
                            }
                        } catch (error) {
                            logMessage(`❌ Error fetching artifact: ${error.message}`);
                        }
                    }
                    
                    // Close connection and log completion
                    sse.close();
                    logMessage('🏁 Connection closed.');
                }
            } catch (error) {
                logMessage(`❌ Error parsing status update: ${error.message}`);
            }
        };
        
        // Handle SSE connection errors
        sse.onerror = function(event) {
            logMessage('❌ SSE Connection Error.');
            sse.close();
        };
        
        // Clear the input fields
        jdUrlInput.value = '';
        resumeFileInput.value = '';
        
    } catch (error) {
        logMessage(`❌ Error submitting job: ${error.message}`);
    }
});