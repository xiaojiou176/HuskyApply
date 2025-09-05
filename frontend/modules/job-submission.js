/**
 * Job Submission Module - Handle job creation and processing
 * 
 * This module handles the job submission form, file uploads, and real-time
 * job processing status updates. Loaded on-demand when user accesses job submission.
 */

class JobSubmissionModule {
    constructor() {
        this.core = window.HuskyApply;
        this.initialized = false;
        this.currentJob = null;
        this.sseConnection = null;
        this.progressSteps = ['PENDING', 'PROCESSING', 'COMPLETED'];
    }

    async init() {
        if (this.initialized) return;
        
        console.log('Initializing Job Submission module...');
        
        this.setupEventListeners();
        this.setupFormValidation();
        this.initialized = true;
        
        console.log('Job Submission module initialized');
    }

    setupEventListeners() {
        // Form submission
        const jobForm = document.getElementById('jobForm');
        if (jobForm) {
            jobForm.addEventListener('submit', this.handleJobSubmission.bind(this));
        }

        // File input handling
        const fileInput = document.getElementById('resumeFileInput');
        if (fileInput) {
            fileInput.addEventListener('change', this.handleFileSelection.bind(this));
            this.setupDragAndDrop(fileInput);
        }

        // Model provider change
        const modelProvider = document.getElementById('modelProvider');
        if (modelProvider) {
            modelProvider.addEventListener('change', this.handleModelProviderChange.bind(this));
        }

        // Global events
        this.core.on('job-cancel', this.cancelCurrentJob.bind(this));
        this.core.on('job-retry', this.retryJob.bind(this));
    }

    setupDragAndDrop(fileInput) {
        const dropZone = fileInput.closest('.file-input');
        if (!dropZone) return;

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.classList.add('drag-over');
        });

        dropZone.addEventListener('dragleave', (e) => {
            e.preventDefault();
            dropZone.classList.remove('drag-over');
        });

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('drag-over');
            
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                fileInput.files = files;
                this.handleFileSelection({ target: fileInput });
            }
        });
    }

    setupFormValidation() {
        const form = document.getElementById('jobForm');
        if (!form) return;

        // Real-time validation
        const inputs = form.querySelectorAll('input, select, textarea');
        inputs.forEach(input => {
            input.addEventListener('blur', () => this.validateField(input));
            input.addEventListener('input', this.core.debounce(() => this.validateField(input), 300));
        });
    }

    validateField(field) {
        const fieldName = field.name || field.id;
        const value = field.value.trim();
        
        let isValid = true;
        let errorMessage = '';

        switch (fieldName) {
            case 'jdUrlInput':
                if (!value) {
                    isValid = false;
                    errorMessage = 'Job URL is required';
                } else if (!this.isValidUrl(value)) {
                    isValid = false;
                    errorMessage = 'Please enter a valid URL';
                }
                break;

            case 'resumeFileInput':
                if (!field.files || field.files.length === 0) {
                    isValid = false;
                    errorMessage = 'Resume file is required';
                } else {
                    const file = field.files[0];
                    const maxSize = 10 * 1024 * 1024; // 10MB
                    const allowedTypes = ['.pdf', '.doc', '.docx'];
                    const fileExtension = '.' + file.name.split('.').pop().toLowerCase();

                    if (file.size > maxSize) {
                        isValid = false;
                        errorMessage = 'File size must be less than 10MB';
                    } else if (!allowedTypes.includes(fileExtension)) {
                        isValid = false;
                        errorMessage = 'Only PDF, DOC, and DOCX files are allowed';
                    }
                }
                break;
        }

        this.showFieldValidation(field, isValid, errorMessage);
        return isValid;
    }

    showFieldValidation(field, isValid, errorMessage) {
        const container = field.closest('.form-group');
        if (!container) return;

        // Remove existing validation
        const existingError = container.querySelector('.field-error');
        if (existingError) {
            existingError.remove();
        }

        field.classList.remove('field-valid', 'field-invalid');

        if (!isValid) {
            field.classList.add('field-invalid');
            const errorDiv = document.createElement('div');
            errorDiv.className = 'field-error';
            errorDiv.textContent = errorMessage;
            container.appendChild(errorDiv);
        } else if (field.value.trim()) {
            field.classList.add('field-valid');
        }
    }

    isValidUrl(string) {
        try {
            const url = new URL(string);
            return url.protocol === 'http:' || url.protocol === 'https:';
        } catch {
            return false;
        }
    }

    handleFileSelection(event) {
        const file = event.target.files[0];
        if (!file) return;

        const fileInfo = document.querySelector('.file-input-label span:last-child');
        if (fileInfo) {
            fileInfo.textContent = `${file.name} (${this.core.formatBytes(file.size)})`;
        }

        this.validateField(event.target);
    }

    handleModelProviderChange(event) {
        const provider = event.target.value;
        const openaiModels = document.getElementById('openaiModels');
        const anthropicModels = document.getElementById('anthropicModels');

        if (provider === 'openai') {
            if (openaiModels) openaiModels.style.display = '';
            if (anthropicModels) anthropicModels.style.display = 'none';
        } else if (provider === 'anthropic') {
            if (openaiModels) openaiModels.style.display = 'none';
            if (anthropicModels) anthropicModels.style.display = '';
        }

        // Reset model selection
        const modelName = document.getElementById('modelName');
        if (modelName) {
            modelName.value = '';
        }
    }

    async handleJobSubmission(event) {
        event.preventDefault();
        
        const startTime = performance.now();
        
        try {
            // Validate form
            if (!this.validateForm()) {
                return;
            }

            const formData = this.getFormData();
            this.showProcessingUI();
            
            // Step 1: Get presigned URL for file upload
            this.updateProgress('Requesting secure upload URL...', 10);
            const uploadUrl = await this.getPresignedUploadUrl(formData.file);
            
            // Step 2: Upload file to S3
            this.updateProgress('Uploading resume to secure storage...', 30);
            const fileUrl = await this.uploadFileToS3(uploadUrl, formData.file);
            
            // Step 3: Submit job
            this.updateProgress('Submitting job for AI processing...', 50);
            const jobResponse = await this.submitJob({
                jdUrl: formData.jdUrl,
                resumeUri: fileUrl,
                modelProvider: formData.modelProvider,
                modelName: formData.modelName
            });

            this.currentJob = jobResponse;
            this.updateProgress('Job submitted successfully! Starting AI processing...', 70);
            
            // Step 4: Start SSE connection for real-time updates
            this.startJobStatusTracking(jobResponse.jobId);
            
            const submitTime = performance.now() - startTime;
            this.core.logMetric('job_submission_time', submitTime);
            
        } catch (error) {
            console.error('Job submission failed:', error);
            this.core.logError('job_submission_error', error.message);
            this.showErrorState(error.message);
        }
    }

    validateForm() {
        const form = document.getElementById('jobForm');
        const inputs = form.querySelectorAll('input[required], select[required]');
        let allValid = true;

        inputs.forEach(input => {
            if (!this.validateField(input)) {
                allValid = false;
            }
        });

        return allValid;
    }

    getFormData() {
        return {
            jdUrl: document.getElementById('jdUrlInput').value.trim(),
            file: document.getElementById('resumeFileInput').files[0],
            modelProvider: document.getElementById('modelProvider').value,
            modelName: document.getElementById('modelName').value
        };
    }

    showProcessingUI() {
        // Hide form and show progress
        const formContainer = document.querySelector('.form-container');
        const progressSection = document.getElementById('progressSection');
        
        if (formContainer) formContainer.style.display = 'none';
        if (progressSection) progressSection.classList.remove('hidden');
        
        // Reset progress
        this.updateProgress('Initializing...', 0);
    }

    updateProgress(message, percentage) {
        const statusText = document.getElementById('statusText');
        const progressBar = document.getElementById('progressBar');
        const progressPercent = document.getElementById('progressPercent');
        const logOutput = document.getElementById('logOutput');

        if (statusText) statusText.textContent = message;
        if (progressBar) progressBar.style.width = `${percentage}%`;
        if (progressPercent) progressPercent.textContent = `${percentage}%`;
        if (logOutput) {
            const timestamp = new Date().toLocaleTimeString();
            logOutput.textContent += `[${timestamp}] ${message}\n`;
            logOutput.scrollTop = logOutput.scrollHeight;
        }
    }

    async getPresignedUploadUrl(file) {
        const response = await this.core.apiCall('/uploads/presigned-url', {
            method: 'POST',
            body: JSON.stringify({
                fileName: file.name,
                contentType: file.type
            })
        });

        if (!response || !response.url) {
            throw new Error('Failed to get upload URL');
        }

        return response.url;
    }

    async uploadFileToS3(presignedUrl, file) {
        const response = await fetch(presignedUrl, {
            method: 'PUT',
            headers: {
                'Content-Type': file.type
            },
            body: file
        });

        if (!response.ok) {
            throw new Error(`File upload failed: ${response.status}`);
        }

        // Return the file URL without query parameters
        return presignedUrl.split('?')[0];
    }

    async submitJob(jobData) {
        const response = await this.core.apiCall('/applications', {
            method: 'POST',
            body: JSON.stringify(jobData)
        });

        if (!response || !response.jobId) {
            throw new Error('Job submission failed');
        }

        return response;
    }

    startJobStatusTracking(jobId) {
        const sseUrl = `${this.core.apiBase}/applications/${jobId}/stream?token=${encodeURIComponent(this.core.token)}`;
        
        this.sseConnection = new EventSource(sseUrl);
        
        this.sseConnection.onopen = () => {
            console.log('SSE connection established');
            this.updateProgress('Connected to real-time status updates...', 75);
        };

        this.sseConnection.onmessage = (event) => {
            try {
                const statusData = JSON.parse(event.data);
                this.handleJobStatusUpdate(statusData);
            } catch (error) {
                console.error('Failed to parse SSE message:', error);
            }
        };

        this.sseConnection.onerror = (error) => {
            console.error('SSE connection error:', error);
            this.core.logError('sse_connection_error', 'SSE connection failed');
        };

        // Auto-cleanup after 10 minutes
        setTimeout(() => {
            this.closeSseConnection();
        }, 10 * 60 * 1000);
    }

    handleJobStatusUpdate(statusData) {
        const status = statusData.status;
        
        switch (status) {
            case 'PROCESSING':
                this.updateProgress('AI is analyzing job description and generating cover letter...', 85);
                this.updateStatusBadge('processing');
                break;
                
            case 'COMPLETED':
                this.updateProgress('Job completed successfully!', 100);
                this.updateStatusBadge('completed');
                this.showJobResults(statusData);
                this.closeSseConnection();
                break;
                
            case 'FAILED':
                this.updateProgress('Job processing failed', 100);
                this.updateStatusBadge('failed');
                this.showErrorState('Job processing failed. Please try again.');
                this.closeSseConnection();
                break;
        }

        this.core.emit('job-status-updated', { jobId: this.currentJob?.jobId, status, data: statusData });
    }

    updateStatusBadge(status) {
        const statusBadge = document.getElementById('statusBadge');
        if (!statusBadge) return;

        statusBadge.className = `status-badge status-${status}`;
        
        const statusDot = statusBadge.querySelector('.status-dot');
        const statusText = document.getElementById('statusText');
        
        if (statusDot && statusText) {
            const statusLabels = {
                processing: 'Processing',
                completed: 'Completed',
                failed: 'Failed'
            };
            statusText.textContent = statusLabels[status] || status;
        }
    }

    async showJobResults(statusData) {
        try {
            // Load job results from API
            const artifactResponse = await this.core.apiCall(`/applications/${this.currentJob.jobId}/artifact`);
            
            const resultsSection = document.getElementById('resultsSection');
            const artifactOutput = document.getElementById('artifactOutput');
            
            if (resultsSection && artifactOutput) {
                artifactOutput.value = artifactResponse.generatedText;
                resultsSection.classList.remove('hidden');
                
                // Scroll to results
                resultsSection.scrollIntoView({ behavior: 'smooth' });
                
                // Setup download and copy buttons
                this.setupResultsActions(artifactResponse);
            }
            
        } catch (error) {
            console.error('Failed to load job results:', error);
            this.showErrorState('Job completed but failed to load results');
        }
    }

    setupResultsActions(artifactData) {
        const copyBtn = document.getElementById('copyBtn');
        const downloadBtn = document.getElementById('downloadBtn');

        if (copyBtn) {
            copyBtn.addEventListener('click', () => {
                navigator.clipboard.writeText(artifactData.generatedText).then(() => {
                    copyBtn.textContent = '✅ Copied!';
                    setTimeout(() => {
                        copyBtn.textContent = '📋 Copy to Clipboard';
                    }, 2000);
                });
            });
        }

        if (downloadBtn) {
            downloadBtn.addEventListener('click', () => {
                const blob = new Blob([artifactData.generatedText], { type: 'text/plain' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `cover-letter-${this.currentJob.jobId.substring(0, 8)}.txt`;
                a.click();
                URL.revokeObjectURL(url);
            });
        }
    }

    showErrorState(message) {
        const statusBadge = document.getElementById('statusBadge');
        if (statusBadge) {
            statusBadge.className = 'status-badge status-failed';
        }

        this.updateProgress(message, 100);
        
        // Show retry button
        this.showRetryOption();
    }

    showRetryOption() {
        const progressSection = document.getElementById('progressSection');
        if (!progressSection) return;

        const retryBtn = document.createElement('button');
        retryBtn.className = 'btn btn-primary';
        retryBtn.textContent = '🔄 Try Again';
        retryBtn.onclick = () => this.resetForm();
        
        progressSection.appendChild(retryBtn);
    }

    resetForm() {
        // Show form, hide progress and results
        const formContainer = document.querySelector('.form-container');
        const progressSection = document.getElementById('progressSection');
        const resultsSection = document.getElementById('resultsSection');
        
        if (formContainer) formContainer.style.display = '';
        if (progressSection) progressSection.classList.add('hidden');
        if (resultsSection) resultsSection.classList.add('hidden');

        // Reset form fields
        const form = document.getElementById('jobForm');
        if (form) {
            form.reset();
            
            // Reset file input label
            const fileLabel = document.querySelector('.file-input-label span:last-child');
            if (fileLabel) {
                fileLabel.textContent = 'Drop your resume here or click to browse';
            }
        }

        // Clear validation states
        const fields = form?.querySelectorAll('.field-valid, .field-invalid');
        fields?.forEach(field => {
            field.classList.remove('field-valid', 'field-invalid');
        });

        // Remove error messages
        const errors = document.querySelectorAll('.field-error');
        errors.forEach(error => error.remove());

        this.currentJob = null;
        this.closeSseConnection();
    }

    closeSseConnection() {
        if (this.sseConnection) {
            this.sseConnection.close();
            this.sseConnection = null;
        }
    }

    async cancelCurrentJob() {
        if (!this.currentJob) return;

        try {
            // Call cancel API if available
            await this.core.apiCall(`/applications/${this.currentJob.jobId}/cancel`, {
                method: 'POST'
            });
            
            this.showErrorState('Job cancelled by user');
            this.closeSseConnection();
            
        } catch (error) {
            console.error('Failed to cancel job:', error);
        }
    }

    async retryJob() {
        if (!this.currentJob) return;
        
        // Reset and resubmit with same data
        this.resetForm();
        
        // Could restore previous form data if needed
        // this.restoreFormData(this.currentJob.originalData);
    }

    destroy() {
        this.closeSseConnection();
        this.currentJob = null;
        this.initialized = false;
    }
}

// Export for dynamic loading
export { JobSubmissionModule };
export const init = async () => {
    const jobSubmission = new JobSubmissionModule();
    await jobSubmission.init();
    return jobSubmission;
};