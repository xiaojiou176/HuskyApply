/**
 * Form Management Module
 * Handles job application form, validation, and file upload
 */

import { AppCore, debounce } from '../app-core.js';

export default class FormModule {
    constructor() {
        this.initialized = false;
        this.currentStep = 1;
        this.totalSteps = 4;
        this.formData = {
            jdUrl: '',
            resumeFile: null,
            modelProvider: 'openai',
            modelName: '',
            includeKeywords: true,
            personalizedTone: true,
            industrySpecific: false,
            additionalNotes: ''
        };
    }
    
    async init() {
        if (this.initialized) return;
        
        console.log('📝 Initializing Form Module...');
        
        // Setup form components
        this.setupMultiStepForm();
        this.setupValidation();
        this.setupFileUpload();
        this.setupModelSelection();
        this.restoreFormData();
        
        this.initialized = true;
        console.log('✅ Form Module Ready');
    }
    
    setupMultiStepForm() {
        const nextBtn = document.getElementById('nextBtn');
        const prevBtn = document.getElementById('prevBtn');
        const submitBtn = document.getElementById('submitBtn');
        
        if (nextBtn) {
            nextBtn.addEventListener('click', () => this.nextStep());
        }
        
        if (prevBtn) {
            prevBtn.addEventListener('click', () => this.prevStep());
        }
        
        if (submitBtn) {
            submitBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.submitForm();
            });
        }
        
        this.updateStepDisplay();
    }
    
    nextStep() {
        if (this.validateCurrentStep()) {
            if (this.currentStep < this.totalSteps) {
                this.currentStep++;
                this.updateStepDisplay();
                this.saveFormData();
                this.trackStepProgression();
            }
        }
    }
    
    prevStep() {
        if (this.currentStep > 1) {
            this.currentStep--;
            this.updateStepDisplay();
        }
    }
    
    updateStepDisplay() {
        // Update progress indicators
        document.querySelectorAll('.progress-step').forEach((step, index) => {
            const stepNumber = index + 1;
            step.classList.toggle('active', stepNumber === this.currentStep);
            step.classList.toggle('completed', stepNumber < this.currentStep);
        });
        
        // Update form steps
        document.querySelectorAll('.form-step').forEach((step, index) => {
            const stepNumber = index + 1;
            step.classList.toggle('active', stepNumber === this.currentStep);
        });
        
        // Update navigation buttons
        const nextBtn = document.getElementById('nextBtn');
        const prevBtn = document.getElementById('prevBtn');
        const submitBtn = document.getElementById('submitBtn');
        
        if (nextBtn) {
            nextBtn.style.display = this.currentStep < this.totalSteps ? 'inline-flex' : 'none';
        }
        
        if (prevBtn) {
            prevBtn.style.display = this.currentStep > 1 ? 'inline-flex' : 'none';
        }
        
        if (submitBtn) {
            submitBtn.style.display = this.currentStep === this.totalSteps ? 'inline-flex' : 'none';
        }
        
        // Animate step transition
        this.animateStepTransition();
    }
    
    animateStepTransition() {
        const activeStep = document.querySelector('.form-step.active');
        if (activeStep) {
            activeStep.classList.add('fade-in');
            setTimeout(() => activeStep.classList.remove('fade-in'), 300);
        }
    }
    
    validateCurrentStep() {
        const currentStepElement = document.querySelector(`.form-step[data-step="${this.currentStep}"]`);
        if (!currentStepElement) return true;
        
        const inputs = currentStepElement.querySelectorAll('input[required], select[required], textarea[required]');
        let isValid = true;
        
        inputs.forEach(input => {
            if (!this.validateField(input)) {
                isValid = false;
            }
        });
        
        return isValid;
    }
    
    validateField(field) {
        const value = field.value.trim();
        const type = field.type || field.tagName.toLowerCase();
        let isValid = true;
        let errorMessage = '';
        
        // Clear previous error
        this.clearFieldError(field);
        
        // Required field validation
        if (field.hasAttribute('required') && !value) {
            isValid = false;
            errorMessage = 'This field is required';
        }
        // URL validation
        else if (field.dataset.validation === 'url' && value) {
            const urlPattern = /^https?:\/\/.+\..+/i;
            if (!urlPattern.test(value)) {
                isValid = false;
                errorMessage = 'Please enter a valid URL starting with http:// or https://';
            }
        }
        // File validation
        else if (type === 'file' && field.files.length > 0) {
            const file = field.files[0];
            const fileExtension = '.' + file.name.split('.').pop().toLowerCase();
            
            if (!AppCore.config.SUPPORTED_FORMATS.includes(fileExtension)) {
                isValid = false;
                errorMessage = `Unsupported file format. Please use: ${AppCore.config.SUPPORTED_FORMATS.join(', ')}`;
            } else if (file.size > AppCore.config.MAX_FILE_SIZE) {
                isValid = false;
                errorMessage = `File too large. Maximum size is ${AppCore.config.MAX_FILE_SIZE / 1024 / 1024}MB`;
            }
        }
        
        // Update field state
        field.classList.toggle('valid', isValid && value);
        field.classList.toggle('invalid', !isValid);
        
        if (!isValid && errorMessage) {
            this.showFieldError(field, errorMessage);
        }
        
        return isValid;
    }
    
    setupValidation() {
        // Real-time validation
        document.querySelectorAll('input, select, textarea').forEach(field => {
            ['blur', 'change'].forEach(event => {
                field.addEventListener(event, () => this.validateField(field));
            });
            
            // URL field special handling
            if (field.dataset.validation === 'url') {
                field.addEventListener('input', debounce(() => {
                    if (field.value.trim()) {
                        this.validateField(field);
                    }
                }, 500));
            }
        });
    }
    
    setupFileUpload() {
        const fileInput = document.getElementById('resumeFileInput');
        const dropZone = document.getElementById('fileDropZone');
        const filePreview = document.getElementById('filePreview');
        
        if (!fileInput || !dropZone) return;
        
        // Drag and drop
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
                this.handleFileSelection(files[0]);
            }
        });
        
        // File selection
        fileInput.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                this.handleFileSelection(e.target.files[0]);
            }
        });
        
        // Remove file
        const removeBtn = filePreview?.querySelector('.file-preview-remove');
        if (removeBtn) {
            removeBtn.addEventListener('click', () => this.removeFile());
        }
    }
    
    handleFileSelection(file) {
        const filePreview = document.getElementById('filePreview');
        const fileInputLabel = document.getElementById('fileInputLabel');
        
        if (this.validateField(document.getElementById('resumeFileInput'))) {
            this.formData.resumeFile = file;
            
            // Update UI
            if (filePreview) {
                filePreview.querySelector('.file-preview-name').textContent = file.name;
                filePreview.querySelector('.file-preview-size').textContent = this.formatFileSize(file.size);
                filePreview.style.display = 'flex';
            }
            
            if (fileInputLabel) {
                fileInputLabel.style.display = 'none';
            }
            
            // Add success animation
            if (filePreview) {
                filePreview.classList.add('bounce-in');
                setTimeout(() => filePreview.classList.remove('bounce-in'), 500);
            }
        }
    }
    
    removeFile() {
        const fileInput = document.getElementById('resumeFileInput');
        const filePreview = document.getElementById('filePreview');
        const fileInputLabel = document.getElementById('fileInputLabel');
        
        if (fileInput) fileInput.value = '';
        if (filePreview) filePreview.style.display = 'none';
        if (fileInputLabel) fileInputLabel.style.display = 'flex';
        
        this.formData.resumeFile = null;
    }
    
    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
    
    setupModelSelection() {
        const providerSelect = document.getElementById('modelProvider');
        const modelSelect = document.getElementById('modelName');
        
        if (providerSelect && modelSelect) {
            providerSelect.addEventListener('change', () => {
                const openaiModels = document.getElementById('openaiModels');
                const anthropicModels = document.getElementById('anthropicModels');
                
                if (providerSelect.value === 'openai') {
                    if (openaiModels) openaiModels.style.display = '';
                    if (anthropicModels) anthropicModels.style.display = 'none';
                } else if (providerSelect.value === 'anthropic') {
                    if (openaiModels) openaiModels.style.display = 'none';
                    if (anthropicModels) anthropicModels.style.display = '';
                }
                
                modelSelect.value = '';
                this.formData.modelProvider = providerSelect.value;
            });
        }
    }
    
    saveFormData() {
        // Save form data to state and localStorage
        const formElement = document.getElementById('jobForm');
        if (formElement) {
            const formData = new FormData(formElement);
            Object.keys(this.formData).forEach(key => {
                if (key !== 'resumeFile') {
                    this.formData[key] = formData.get(key) || this.formData[key];
                }
            });
        }
        
        localStorage.setItem('huskyapply_form_data', JSON.stringify({
            ...this.formData,
            resumeFile: null // Don't store file in localStorage
        }));
    }
    
    restoreFormData() {
        const savedData = localStorage.getItem('huskyapply_form_data');
        if (savedData) {
            try {
                const data = JSON.parse(savedData);
                Object.assign(this.formData, data);
                
                // Restore form fields
                Object.keys(data).forEach(key => {
                    const field = document.getElementById(key);
                    if (field && data[key]) {
                        field.value = data[key];
                        if (field.type === 'checkbox') {
                            field.checked = data[key];
                        }
                    }
                });
            } catch (error) {
                console.warn('Failed to restore form data:', error);
            }
        }
    }
    
    trackStepProgression() {
        // Analytics tracking for step progression
        if (typeof gtag !== 'undefined') {
            gtag('event', 'form_step', {
                step_number: this.currentStep,
                step_name: `step_${this.currentStep}`
            });
        }
    }
    
    async submitForm() {
        if (!this.validateCurrentStep()) {
            this.showNotification('Please complete all required fields', 'error');
            return;
        }
        
        // Check authentication
        const token = localStorage.getItem('jwtToken');
        if (!token) {
            this.showNotification('Please log in to continue', 'warning');
            setTimeout(() => {
                window.location.href = 'login.html';
            }, 2000);
            return;
        }
        
        this.setSubmitButtonLoading(true);
        
        try {
            // Step 1: Upload file to get presigned URL
            const presignedUrl = await this.getPresignedUrl();
            
            // Step 2: Upload file to S3
            await this.uploadFileToS3(presignedUrl);
            
            // Step 3: Submit job
            const jobData = await this.submitJobRequest(presignedUrl.split('?')[0]);
            
            // Store job ID and start processing
            AppCore.state.jobId = jobData.jobId;
            this.showProcessingSection();
            
            // Load processing module dynamically
            const { ModuleLoader } = window.HuskyApplyCore;
            const processingModule = await ModuleLoader.loadModule('processing');
            processingModule.startStatusUpdates(jobData.jobId);
            
        } catch (error) {
            console.error('Submission error:', error);
            this.showNotification(`Submission failed: ${error.message}`, 'error');
        } finally {
            this.setSubmitButtonLoading(false);
        }
    }
    
    async getPresignedUrl() {
        const response = await fetch(`${AppCore.config.API_BASE}/uploads/presigned-url`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${localStorage.getItem('jwtToken')}`
            },
            body: JSON.stringify({
                fileName: this.formData.resumeFile.name,
                contentType: this.formData.resumeFile.type
            })
        });
        
        if (!response.ok) {
            throw new Error(`Failed to get upload URL: ${response.status}`);
        }
        
        const data = await response.json();
        return data.url;
    }
    
    async uploadFileToS3(presignedUrl) {
        const response = await fetch(presignedUrl, {
            method: 'PUT',
            headers: {
                'Content-Type': this.formData.resumeFile.type
            },
            body: this.formData.resumeFile
        });
        
        if (!response.ok) {
            throw new Error(`File upload failed: ${response.status}`);
        }
    }
    
    async submitJobRequest(resumeUri) {
        const response = await fetch(`${AppCore.config.API_BASE}/applications`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${localStorage.getItem('jwtToken')}`
            },
            body: JSON.stringify({
                jdUrl: document.getElementById('jdUrlInput')?.value.trim(),
                resumeUri: resumeUri,
                modelProvider: document.getElementById('modelProvider')?.value,
                modelName: document.getElementById('modelName')?.value || null,
                options: {
                    includeKeywords: document.getElementById('includeKeywords')?.checked,
                    personalizedTone: document.getElementById('personalizedTone')?.checked,
                    industrySpecific: document.getElementById('industrySpecific')?.checked,
                    additionalNotes: document.getElementById('additionalNotes')?.value.trim()
                }
            })
        });
        
        if (!response.ok) {
            const errorData = await response.text();
            throw new Error(errorData || `HTTP ${response.status}`);
        }
        
        return await response.json();
    }
    
    showProcessingSection() {
        document.getElementById('processingSection')?.classList.remove('hidden');
        document.querySelector('.form-section')?.classList.add('hidden');
        
        // Scroll to processing section
        document.getElementById('processingSection')?.scrollIntoView({
            behavior: 'smooth'
        });
    }
    
    setSubmitButtonLoading(isLoading) {
        const submitBtn = document.getElementById('submitBtn');
        const btnText = document.getElementById('submitBtnText');
        const btnIcon = document.getElementById('submitBtnIcon');
        const btnSpinner = document.getElementById('submitBtnSpinner');
        
        if (submitBtn) {
            submitBtn.disabled = isLoading;
            submitBtn.classList.toggle('loading', isLoading);
        }
        
        if (btnText) btnText.textContent = isLoading ? 'Processing...' : 'Generate Cover Letter';
        if (btnIcon) btnIcon.style.display = isLoading ? 'none' : 'inline';
        if (btnSpinner) btnSpinner.style.display = isLoading ? 'inline-block' : 'none';
    }
    
    showFieldError(field, message) {
        const errorId = field.getAttribute('aria-describedby')?.split(' ')?.find(id => id.includes('error'));
        const errorElement = errorId ? document.getElementById(errorId) : null;
        
        if (errorElement) {
            errorElement.textContent = message;
            errorElement.style.display = 'block';
        }
    }
    
    clearFieldError(field) {
        const errorId = field.getAttribute('aria-describedby')?.split(' ')?.find(id => id.includes('error'));
        const errorElement = errorId ? document.getElementById(errorId) : null;
        
        if (errorElement) {
            errorElement.textContent = '';
            errorElement.style.display = 'none';
        }
    }
    
    showNotification(message, type = 'info') {
        // Simple notification implementation
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.textContent = message;
        document.body.appendChild(notification);
        
        setTimeout(() => {
            notification.remove();
        }, 5000);
    }
}