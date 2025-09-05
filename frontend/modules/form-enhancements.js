/**
 * Form Enhancements Module - Advanced UI/UX Features
 * 
 * Enhances the job application form with:
 * - Advanced drag & drop with preview
 * - Real-time validation with smart feedback
 * - Progress indicators and smart suggestions
 * - Accessibility improvements
 * - Mobile-first responsive design
 */

class FormEnhancementsModule {
    constructor() {
        this.core = window.HuskyApply;
        this.initialized = false;
        this.dragCounter = 0;
        this.validationRules = {};
        this.smartSuggestions = {};
    }

    async init() {
        if (this.initialized) return;
        
        console.log('Initializing Form Enhancements module...');
        
        this.enhanceFileUpload();
        this.enhanceFormValidation();
        this.addSmartSuggestions();
        this.improveAccessibility();
        this.addProgressIndicators();
        this.setupMobileOptimizations();
        
        this.initialized = true;
        console.log('Form Enhancements module initialized');
    }

    enhanceFileUpload() {
        const fileInput = document.getElementById('resumeFileInput');
        const fileInputContainer = fileInput?.closest('.file-input');
        
        if (!fileInput || !fileInputContainer) return;

        // Create enhanced upload UI
        this.createEnhancedUploadUI(fileInputContainer);
        
        // Setup advanced drag & drop
        this.setupAdvancedDragDrop(fileInputContainer, fileInput);
        
        // Add file preview functionality
        this.setupFilePreview(fileInput);
    }

    createEnhancedUploadUI(container) {
        const enhancedHTML = `
            <div class="enhanced-file-upload">
                <div class="upload-zone" id="upload-zone">
                    <div class="upload-content">
                        <div class="upload-icon-container">
                            <svg class="upload-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                                <polyline points="7,10 12,15 17,10"/>
                                <line x1="12" y1="15" x2="12" y2="3"/>
                            </svg>
                        </div>
                        <div class="upload-text">
                            <p class="upload-primary">Drop your resume here</p>
                            <p class="upload-secondary">or <button type="button" class="upload-browse-btn">browse files</button></p>
                            <p class="upload-formats">Supports PDF, DOC, DOCX • Max 10MB</p>
                        </div>
                    </div>
                    
                    <div class="upload-progress hidden" id="upload-progress">
                        <div class="progress-circle">
                            <svg class="progress-ring" viewBox="0 0 36 36">
                                <path class="progress-ring-bg" 
                                      d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"/>
                                <path class="progress-ring-fill" 
                                      d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"/>
                            </svg>
                            <div class="progress-text">0%</div>
                        </div>
                        <p class="progress-status">Uploading...</p>
                    </div>
                </div>
                
                <div class="file-preview hidden" id="file-preview">
                    <div class="preview-content">
                        <div class="file-info">
                            <div class="file-icon">📄</div>
                            <div class="file-details">
                                <div class="file-name" id="preview-file-name"></div>
                                <div class="file-size" id="preview-file-size"></div>
                                <div class="file-status" id="preview-file-status">
                                    <span class="status-icon">✅</span>
                                    <span class="status-text">Ready to upload</span>
                                </div>
                            </div>
                        </div>
                        <div class="file-actions">
                            <button type="button" class="btn btn-sm btn-outline preview-btn">
                                👁️ Preview
                            </button>
                            <button type="button" class="btn btn-sm btn-secondary remove-file-btn">
                                🗑️ Remove
                            </button>
                        </div>
                    </div>
                    
                    <div class="file-analysis" id="file-analysis">
                        <div class="analysis-loading">
                            <div class="analysis-spinner"></div>
                            <p>Analyzing resume...</p>
                        </div>
                        <div class="analysis-results hidden">
                            <h5>📊 Resume Analysis</h5>
                            <div class="analysis-metrics">
                                <div class="metric">
                                    <span class="metric-label">Sections Found:</span>
                                    <span class="metric-value" id="sections-count">-</span>
                                </div>
                                <div class="metric">
                                    <span class="metric-label">Keywords:</span>
                                    <span class="metric-value" id="keywords-count">-</span>
                                </div>
                                <div class="metric">
                                    <span class="metric-label">Format Score:</span>
                                    <span class="metric-value" id="format-score">-</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        container.innerHTML = enhancedHTML;

        // Bind events for enhanced UI
        const browseBtn = container.querySelector('.upload-browse-btn');
        const removeBtn = container.querySelector('.remove-file-btn');
        const previewBtn = container.querySelector('.preview-btn');

        browseBtn?.addEventListener('click', () => {
            const fileInput = document.getElementById('resumeFileInput');
            fileInput?.click();
        });

        removeBtn?.addEventListener('click', () => {
            this.removeFile();
        });

        previewBtn?.addEventListener('click', () => {
            this.showFilePreview();
        });
    }

    setupAdvancedDragDrop(container, fileInput) {
        const uploadZone = container.querySelector('#upload-zone');
        if (!uploadZone) return;

        const dragEvents = ['dragenter', 'dragover', 'dragleave', 'drop'];
        
        // Prevent default drag behaviors
        dragEvents.forEach(eventName => {
            uploadZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
            });
            
            document.body.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
            });
        });

        // Handle drag enter/leave for visual feedback
        uploadZone.addEventListener('dragenter', (e) => {
            this.dragCounter++;
            uploadZone.classList.add('drag-over');
            this.showDropIndicator();
        });

        uploadZone.addEventListener('dragleave', (e) => {
            this.dragCounter--;
            if (this.dragCounter === 0) {
                uploadZone.classList.remove('drag-over');
                this.hideDropIndicator();
            }
        });

        // Handle file drop
        uploadZone.addEventListener('drop', (e) => {
            this.dragCounter = 0;
            uploadZone.classList.remove('drag-over');
            this.hideDropIndicator();
            
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                this.handleFileSelection(files[0], fileInput);
            }
        });

        // Handle regular file input
        fileInput.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                this.handleFileSelection(e.target.files[0], fileInput);
            }
        });
    }

    showDropIndicator() {
        const indicator = document.getElementById('drop-indicator') || this.createDropIndicator();
        indicator.classList.add('show');
    }

    hideDropIndicator() {
        const indicator = document.getElementById('drop-indicator');
        if (indicator) {
            indicator.classList.remove('show');
        }
    }

    createDropIndicator() {
        const indicator = document.createElement('div');
        indicator.id = 'drop-indicator';
        indicator.className = 'drop-indicator';
        indicator.innerHTML = `
            <div class="drop-content">
                <div class="drop-icon">📁</div>
                <p>Release to upload your resume</p>
            </div>
        `;
        
        document.body.appendChild(indicator);
        return indicator;
    }

    async handleFileSelection(file, fileInput) {
        // Validate file
        const validation = this.validateFile(file);
        if (!validation.isValid) {
            this.showFileError(validation.errors);
            return;
        }

        // Update file input
        const dataTransfer = new DataTransfer();
        dataTransfer.items.add(file);
        fileInput.files = dataTransfer.files;

        // Show file preview
        this.showFileInfo(file);
        
        // Start file analysis
        this.analyzeFile(file);

        // Track file upload
        this.core.logMetric('file_selected', 1, {
            file_size: file.size,
            file_type: file.type,
            file_name: file.name
        });
    }

    validateFile(file) {
        const errors = [];
        const maxSize = 10 * 1024 * 1024; // 10MB
        const allowedTypes = [
            'application/pdf',
            'application/msword',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
        ];
        const allowedExtensions = ['.pdf', '.doc', '.docx'];

        // Size validation
        if (file.size > maxSize) {
            errors.push(`File size (${this.core.formatBytes(file.size)}) exceeds 10MB limit`);
        }

        // Type validation
        const fileExtension = '.' + file.name.split('.').pop().toLowerCase();
        if (!allowedTypes.includes(file.type) && !allowedExtensions.includes(fileExtension)) {
            errors.push('Only PDF, DOC, and DOCX files are supported');
        }

        // Name validation
        if (file.name.length > 100) {
            errors.push('File name is too long (max 100 characters)');
        }

        return {
            isValid: errors.length === 0,
            errors
        };
    }

    showFileInfo(file) {
        const uploadZone = document.getElementById('upload-zone');
        const filePreview = document.getElementById('file-preview');
        
        if (uploadZone) uploadZone.classList.add('hidden');
        if (filePreview) {
            filePreview.classList.remove('hidden');
            
            // Update file details
            const fileName = filePreview.querySelector('#preview-file-name');
            const fileSize = filePreview.querySelector('#preview-file-size');
            
            if (fileName) fileName.textContent = file.name;
            if (fileSize) fileSize.textContent = this.core.formatBytes(file.size);
        }
    }

    async analyzeFile(file) {
        const analysisContainer = document.getElementById('file-analysis');
        if (!analysisContainer) return;

        // Show loading state
        const loadingEl = analysisContainer.querySelector('.analysis-loading');
        const resultsEl = analysisContainer.querySelector('.analysis-results');
        
        if (loadingEl) loadingEl.style.display = 'block';
        if (resultsEl) resultsEl.classList.add('hidden');

        try {
            // Simulate file analysis (in real app, this would call an API)
            await new Promise(resolve => setTimeout(resolve, 2000));
            
            // Mock analysis results
            const analysis = this.getMockAnalysis(file);
            
            // Update UI with results
            this.displayAnalysisResults(analysis);
            
        } catch (error) {
            console.error('File analysis failed:', error);
            this.showAnalysisError();
        }
    }

    getMockAnalysis(file) {
        const fileType = file.type;
        const fileSize = file.size;
        
        // Mock analysis based on file characteristics
        return {
            sectionsFound: 5 + Math.floor(Math.random() * 3), // 5-7 sections
            keywordsCount: 15 + Math.floor(Math.random() * 10), // 15-24 keywords
            formatScore: 85 + Math.floor(Math.random() * 15), // 85-99%
            suggestions: [
                'Strong technical skills section identified',
                'Professional experience clearly formatted',
                'Consider adding more quantified achievements'
            ]
        };
    }

    displayAnalysisResults(analysis) {
        const analysisContainer = document.getElementById('file-analysis');
        if (!analysisContainer) return;

        const loadingEl = analysisContainer.querySelector('.analysis-loading');
        const resultsEl = analysisContainer.querySelector('.analysis-results');
        
        if (loadingEl) loadingEl.style.display = 'none';
        if (resultsEl) {
            resultsEl.classList.remove('hidden');
            
            // Update metrics
            const sectionsEl = resultsEl.querySelector('#sections-count');
            const keywordsEl = resultsEl.querySelector('#keywords-count');
            const formatScoreEl = resultsEl.querySelector('#format-score');
            
            if (sectionsEl) sectionsEl.textContent = analysis.sectionsFound;
            if (keywordsEl) keywordsEl.textContent = analysis.keywordsCount;
            if (formatScoreEl) {
                formatScoreEl.textContent = `${analysis.formatScore}%`;
                formatScoreEl.className = `metric-value ${analysis.formatScore >= 90 ? 'excellent' : analysis.formatScore >= 75 ? 'good' : 'needs-improvement'}`;
            }
        }
    }

    removeFile() {
        const fileInput = document.getElementById('resumeFileInput');
        const uploadZone = document.getElementById('upload-zone');
        const filePreview = document.getElementById('file-preview');
        
        if (fileInput) {
            fileInput.value = '';
        }
        
        if (uploadZone) uploadZone.classList.remove('hidden');
        if (filePreview) filePreview.classList.add('hidden');
        
        this.core.logMetric('file_removed', 1);
    }

    setupFilePreview(fileInput) {
        // Add preview functionality for supported file types
        fileInput.addEventListener('change', async (e) => {
            const file = e.target.files[0];
            if (!file) return;

            if (file.type === 'application/pdf') {
                await this.generatePDFPreview(file);
            }
        });
    }

    async generatePDFPreview(file) {
        try {
            // In a real implementation, you would use PDF.js or similar
            // For now, we'll show a placeholder
            this.showPreviewModal({
                type: 'pdf',
                name: file.name,
                size: file.size,
                preview: 'PDF preview would be shown here using PDF.js'
            });
        } catch (error) {
            console.error('PDF preview generation failed:', error);
        }
    }

    showPreviewModal(fileData) {
        const modal = document.createElement('div');
        modal.className = 'file-preview-modal';
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h3>📄 File Preview: ${fileData.name}</h3>
                    <button class="close-btn">&times;</button>
                </div>
                <div class="modal-body">
                    <div class="preview-container">
                        ${fileData.type === 'pdf' ? 
                            '<div class="pdf-preview">PDF preview functionality would be implemented here</div>' :
                            '<div class="text-preview">Text content preview would be shown here</div>'
                        }
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-primary close-preview-btn">Close Preview</button>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        // Event handlers
        modal.querySelector('.close-btn')?.addEventListener('click', () => modal.remove());
        modal.querySelector('.close-preview-btn')?.addEventListener('click', () => modal.remove());
        
        modal.addEventListener('click', (e) => {
            if (e.target === modal) modal.remove();
        });
    }

    enhanceFormValidation() {
        const form = document.getElementById('jobForm');
        if (!form) return;

        // Setup real-time validation for all inputs
        const inputs = form.querySelectorAll('input, select, textarea');
        inputs.forEach(input => {
            this.setupInputValidation(input);
        });

        // Add form-wide validation
        this.setupFormValidation(form);
    }

    setupInputValidation(input) {
        const validationConfig = this.getValidationConfig(input);
        if (!validationConfig) return;

        // Real-time validation on input
        input.addEventListener('input', this.core.debounce(() => {
            this.validateInput(input, validationConfig);
        }, 300));

        // Validation on blur
        input.addEventListener('blur', () => {
            this.validateInput(input, validationConfig);
        });

        // Special handling for URL inputs
        if (input.type === 'url') {
            this.setupURLValidation(input);
        }
    }

    getValidationConfig(input) {
        const configs = {
            'jdUrlInput': {
                required: true,
                type: 'url',
                pattern: /^https?:\/\/.+/,
                message: 'Please enter a valid job posting URL'
            },
            'resumeFileInput': {
                required: true,
                type: 'file',
                accept: ['.pdf', '.doc', '.docx'],
                maxSize: 10 * 1024 * 1024
            },
            'modelProvider': {
                required: true,
                type: 'select'
            }
        };

        return configs[input.id] || null;
    }

    async validateInput(input, config) {
        const value = input.value.trim();
        const validation = { isValid: true, errors: [], warnings: [] };

        // Required validation
        if (config.required && !value) {
            validation.isValid = false;
            validation.errors.push('This field is required');
        }

        // Type-specific validation
        switch (config.type) {
            case 'url':
                if (value && !this.isValidURL(value)) {
                    validation.isValid = false;
                    validation.errors.push('Please enter a valid URL starting with http:// or https://');
                } else if (value) {
                    // Check if URL is accessible (smart validation)
                    const urlCheck = await this.checkURLAccessibility(value);
                    if (!urlCheck.accessible) {
                        validation.warnings.push('URL may not be accessible. Please verify it\'s correct.');
                    }
                }
                break;

            case 'file':
                if (input.files && input.files.length > 0) {
                    const fileValidation = this.validateFile(input.files[0]);
                    if (!fileValidation.isValid) {
                        validation.isValid = false;
                        validation.errors.push(...fileValidation.errors);
                    }
                }
                break;
        }

        // Update UI with validation results
        this.showValidationFeedback(input, validation);

        return validation;
    }

    async checkURLAccessibility(url) {
        try {
            // In a real implementation, you might use a proxy service
            // For now, we'll do basic format checking
            const urlObj = new URL(url);
            return {
                accessible: true,
                domain: urlObj.hostname
            };
        } catch (error) {
            return {
                accessible: false,
                error: error.message
            };
        }
    }

    showValidationFeedback(input, validation) {
        const container = input.closest('.form-group');
        if (!container) return;

        // Remove existing feedback
        const existingFeedback = container.querySelectorAll('.validation-feedback, .validation-warning');
        existingFeedback.forEach(el => el.remove());

        // Remove existing validation classes
        input.classList.remove('valid', 'invalid', 'warning');

        if (!validation.isValid) {
            input.classList.add('invalid');
            validation.errors.forEach(error => {
                const feedback = document.createElement('div');
                feedback.className = 'validation-feedback error';
                feedback.innerHTML = `
                    <span class="feedback-icon">❌</span>
                    <span class="feedback-text">${error}</span>
                `;
                container.appendChild(feedback);
            });
        } else if (validation.warnings.length > 0) {
            input.classList.add('warning');
            validation.warnings.forEach(warning => {
                const feedback = document.createElement('div');
                feedback.className = 'validation-warning';
                feedback.innerHTML = `
                    <span class="feedback-icon">⚠️</span>
                    <span class="feedback-text">${warning}</span>
                `;
                container.appendChild(feedback);
            });
        } else if (input.value.trim()) {
            input.classList.add('valid');
            const feedback = document.createElement('div');
            feedback.className = 'validation-feedback success';
            feedback.innerHTML = `
                <span class="feedback-icon">✅</span>
                <span class="feedback-text">Looks good!</span>
            `;
            container.appendChild(feedback);
        }
    }

    setupURLValidation(input) {
        // Add smart URL suggestions
        input.addEventListener('input', this.core.debounce(() => {
            const value = input.value.trim();
            if (value && !value.startsWith('http')) {
                this.showURLSuggestion(input, value);
            }
        }, 500));
    }

    showURLSuggestion(input, value) {
        const container = input.closest('.form-group');
        if (!container) return;

        // Remove existing suggestions
        const existingSuggestion = container.querySelector('.url-suggestion');
        if (existingSuggestion) existingSuggestion.remove();

        // Create suggestion
        const suggestion = document.createElement('div');
        suggestion.className = 'url-suggestion';
        suggestion.innerHTML = `
            <div class="suggestion-content">
                <span class="suggestion-icon">💡</span>
                <span>Did you mean: </span>
                <button type="button" class="suggestion-btn" data-url="https://${value}">
                    https://${value}
                </button>
            </div>
        `;

        container.appendChild(suggestion);

        // Handle suggestion click
        suggestion.querySelector('.suggestion-btn')?.addEventListener('click', (e) => {
            const suggestedUrl = e.target.dataset.url;
            input.value = suggestedUrl;
            input.dispatchEvent(new Event('input', { bubbles: true }));
            suggestion.remove();
        });

        // Auto-remove suggestion after 5 seconds
        setTimeout(() => suggestion.remove(), 5000);
    }

    isValidURL(string) {
        try {
            const url = new URL(string);
            return url.protocol === 'http:' || url.protocol === 'https:';
        } catch {
            return false;
        }
    }

    addSmartSuggestions() {
        // Add AI model recommendations based on job URL
        this.setupModelRecommendations();
        
        // Add job URL suggestions based on popular sites
        this.setupJobSiteSuggestions();
    }

    setupModelRecommendations() {
        const jobUrlInput = document.getElementById('jdUrlInput');
        const modelSelect = document.getElementById('modelProvider');
        
        if (!jobUrlInput || !modelSelect) return;

        jobUrlInput.addEventListener('input', this.core.debounce(async () => {
            const url = jobUrlInput.value.trim();
            if (url) {
                const recommendation = await this.getModelRecommendation(url);
                this.showModelRecommendation(recommendation);
            }
        }, 1000));
    }

    async getModelRecommendation(url) {
        try {
            const urlObj = new URL(url);
            const domain = urlObj.hostname.toLowerCase();
            
            // Smart recommendations based on company/domain
            if (domain.includes('google') || domain.includes('microsoft') || domain.includes('amazon')) {
                return {
                    provider: 'openai',
                    model: 'gpt-4o',
                    reason: 'Recommended for tech companies - best at technical skills matching'
                };
            } else if (domain.includes('startup') || domain.includes('creative') || domain.includes('agency')) {
                return {
                    provider: 'anthropic',
                    model: 'claude-3-5-sonnet-20241022',
                    reason: 'Great for creative roles and startups - excellent writing style'
                };
            } else {
                return {
                    provider: 'openai',
                    model: 'gpt-4o',
                    reason: 'Our most popular choice - works great for most industries'
                };
            }
        } catch (error) {
            return {
                provider: 'openai',
                model: 'gpt-4o',
                reason: 'Default recommendation - reliable for all job types'
            };
        }
    }

    showModelRecommendation(recommendation) {
        const modelContainer = document.getElementById('modelProvider')?.closest('.form-group');
        if (!modelContainer) return;

        // Remove existing recommendation
        const existingRec = modelContainer.querySelector('.model-recommendation');
        if (existingRec) existingRec.remove();

        // Create recommendation UI
        const recElement = document.createElement('div');
        recElement.className = 'model-recommendation';
        recElement.innerHTML = `
            <div class="recommendation-content">
                <div class="recommendation-header">
                    <span class="rec-icon">🎯</span>
                    <span class="rec-title">Recommended AI Model</span>
                </div>
                <div class="recommendation-details">
                    <div class="rec-model">
                        ${recommendation.provider === 'openai' ? 'OpenAI GPT-4o' : 'Anthropic Claude'}
                    </div>
                    <div class="rec-reason">${recommendation.reason}</div>
                    <button type="button" class="btn btn-sm btn-primary use-recommendation-btn">
                        Use This Recommendation
                    </button>
                </div>
            </div>
        `;

        modelContainer.appendChild(recElement);

        // Handle recommendation acceptance
        recElement.querySelector('.use-recommendation-btn')?.addEventListener('click', () => {
            const providerSelect = document.getElementById('modelProvider');
            const modelSelect = document.getElementById('modelName');
            
            if (providerSelect) {
                providerSelect.value = recommendation.provider;
                providerSelect.dispatchEvent(new Event('change'));
            }
            
            setTimeout(() => {
                if (modelSelect) {
                    modelSelect.value = recommendation.model;
                }
            }, 100);

            recElement.remove();
            this.core.logMetric('ai_recommendation_accepted', 1);
        });

        // Auto-remove after 10 seconds
        setTimeout(() => recElement.remove(), 10000);
    }

    improveAccessibility() {
        // Add ARIA labels and descriptions
        this.addARIALabels();
        
        // Improve keyboard navigation
        this.enhanceKeyboardNavigation();
        
        // Add screen reader announcements
        this.setupScreenReaderAnnouncements();
    }

    addARIALabels() {
        const accessibilityMappings = {
            'jdUrlInput': {
                'aria-describedby': 'url-help',
                'aria-label': 'Job description URL'
            },
            'resumeFileInput': {
                'aria-describedby': 'file-help',
                'aria-label': 'Resume file upload'
            },
            'modelProvider': {
                'aria-label': 'AI model provider selection'
            }
        };

        Object.entries(accessibilityMappings).forEach(([id, attrs]) => {
            const element = document.getElementById(id);
            if (element) {
                Object.entries(attrs).forEach(([attr, value]) => {
                    element.setAttribute(attr, value);
                });
            }
        });
    }

    enhanceKeyboardNavigation() {
        // Add keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            // Alt + S to submit form
            if (e.altKey && e.key === 's') {
                e.preventDefault();
                const submitBtn = document.getElementById('submitBtn');
                submitBtn?.click();
            }
            
            // Alt + R to reset form
            if (e.altKey && e.key === 'r') {
                e.preventDefault();
                const form = document.getElementById('jobForm');
                if (confirm('Are you sure you want to reset the form?')) {
                    form?.reset();
                }
            }
        });

        // Improve focus management
        this.setupFocusManagement();
    }

    setupFocusManagement() {
        const form = document.getElementById('jobForm');
        if (!form) return;

        // Focus management for dynamic content
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                mutation.addedNodes.forEach((node) => {
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        // Auto-focus first input in new dynamic content
                        const firstInput = node.querySelector('input, select, textarea, button');
                        if (firstInput && node.classList.contains('auto-focus')) {
                            firstInput.focus();
                        }
                    }
                });
            });
        });

        observer.observe(form, { childList: true, subtree: true });
    }

    addProgressIndicators() {
        // Add form completion progress
        this.createFormProgressIndicator();
        
        // Add step-by-step guidance
        this.setupStepGuidance();
    }

    createFormProgressIndicator() {
        const form = document.getElementById('jobForm');
        if (!form) return;

        const progressContainer = document.createElement('div');
        progressContainer.className = 'form-progress-container';
        progressContainer.innerHTML = `
            <div class="form-progress">
                <div class="progress-header">
                    <h4>Form Completion</h4>
                    <span class="progress-percentage" id="form-progress-percentage">0%</span>
                </div>
                <div class="progress-bar-container">
                    <div class="progress-bar" id="form-progress-bar"></div>
                </div>
                <div class="progress-steps">
                    <div class="step" data-step="url">
                        <span class="step-icon">🔗</span>
                        <span class="step-label">Job URL</span>
                    </div>
                    <div class="step" data-step="resume">
                        <span class="step-icon">📄</span>
                        <span class="step-label">Resume</span>
                    </div>
                    <div class="step" data-step="model">
                        <span class="step-icon">⚙️</span>
                        <span class="step-label">AI Model</span>
                    </div>
                    <div class="step" data-step="ready">
                        <span class="step-icon">🚀</span>
                        <span class="step-label">Ready</span>
                    </div>
                </div>
            </div>
        `;

        form.insertBefore(progressContainer, form.firstChild);

        // Update progress on input changes
        form.addEventListener('input', () => {
            this.updateFormProgress();
        });

        form.addEventListener('change', () => {
            this.updateFormProgress();
        });

        // Initial progress calculation
        this.updateFormProgress();
    }

    updateFormProgress() {
        const requiredFields = [
            { id: 'jdUrlInput', step: 'url' },
            { id: 'resumeFileInput', step: 'resume' },
            { id: 'modelProvider', step: 'model' }
        ];

        let completedFields = 0;
        const stepElements = document.querySelectorAll('.progress-steps .step');

        requiredFields.forEach(({ id, step }) => {
            const field = document.getElementById(id);
            const stepElement = document.querySelector(`.step[data-step="${step}"]`);
            
            if (field && this.isFieldCompleted(field)) {
                completedFields++;
                stepElement?.classList.add('completed');
            } else {
                stepElement?.classList.remove('completed');
            }
        });

        const progressPercentage = Math.round((completedFields / requiredFields.length) * 100);
        const progressBar = document.getElementById('form-progress-bar');
        const progressText = document.getElementById('form-progress-percentage');

        if (progressBar) progressBar.style.width = `${progressPercentage}%`;
        if (progressText) progressText.textContent = `${progressPercentage}%`;

        // Update ready step
        const readyStep = document.querySelector('.step[data-step="ready"]');
        if (progressPercentage === 100) {
            readyStep?.classList.add('completed');
        } else {
            readyStep?.classList.remove('completed');
        }

        // Track progress
        this.core.logMetric('form_progress', progressPercentage);
    }

    isFieldCompleted(field) {
        switch (field.type) {
            case 'file':
                return field.files && field.files.length > 0;
            case 'url':
                return field.value.trim() && this.isValidURL(field.value.trim());
            default:
                return field.value.trim() !== '';
        }
    }

    setupMobileOptimizations() {
        // Detect mobile device
        const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
        
        if (isMobile) {
            document.body.classList.add('mobile-device');
            this.applyMobileOptimizations();
        }

        // Handle orientation changes
        window.addEventListener('orientationchange', () => {
            setTimeout(() => {
                this.adjustLayoutForOrientation();
            }, 100);
        });
    }

    applyMobileOptimizations() {
        // Optimize touch targets
        const buttons = document.querySelectorAll('button, .btn');
        buttons.forEach(btn => {
            if (!btn.classList.contains('btn-sm')) {
                btn.style.minHeight = '44px'; // iOS recommendation
                btn.style.minWidth = '44px';
            }
        });

        // Improve file upload on mobile
        const fileInput = document.getElementById('resumeFileInput');
        if (fileInput) {
            fileInput.setAttribute('capture', 'environment'); // Allow camera capture
        }

        // Add mobile-specific gestures
        this.setupMobileGestures();
    }

    setupMobileGestures() {
        let touchStartY = 0;
        let touchEndY = 0;

        document.addEventListener('touchstart', (e) => {
            touchStartY = e.changedTouches[0].screenY;
        }, { passive: true });

        document.addEventListener('touchend', (e) => {
            touchEndY = e.changedTouches[0].screenY;
            this.handleGesture(touchStartY, touchEndY);
        }, { passive: true });
    }

    handleGesture(startY, endY) {
        const threshold = 50;
        const diff = startY - endY;

        if (Math.abs(diff) < threshold) return;

        if (diff > 0) {
            // Swipe up - scroll to next section
            this.scrollToNextSection();
        } else {
            // Swipe down - scroll to previous section
            this.scrollToPreviousSection();
        }
    }

    scrollToNextSection() {
        const sections = document.querySelectorAll('.form-group, .card');
        const currentSection = this.getCurrentVisibleSection(sections);
        const nextSection = sections[currentSection + 1];
        
        if (nextSection) {
            nextSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    scrollToPreviousSection() {
        const sections = document.querySelectorAll('.form-group, .card');
        const currentSection = this.getCurrentVisibleSection(sections);
        const prevSection = sections[currentSection - 1];
        
        if (prevSection) {
            prevSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    getCurrentVisibleSection(sections) {
        const scrollTop = window.pageYOffset;
        const windowHeight = window.innerHeight;
        
        for (let i = 0; i < sections.length; i++) {
            const section = sections[i];
            const rect = section.getBoundingClientRect();
            
            if (rect.top <= windowHeight / 2 && rect.bottom >= windowHeight / 2) {
                return i;
            }
        }
        
        return 0;
    }

    adjustLayoutForOrientation() {
        const form = document.getElementById('jobForm');
        if (!form) return;

        if (window.orientation === 90 || window.orientation === -90) {
            // Landscape mode
            form.classList.add('landscape-mode');
        } else {
            // Portrait mode
            form.classList.remove('landscape-mode');
        }
    }

    destroy() {
        // Clean up event listeners and UI elements
        const dynamicElements = [
            '#drop-indicator',
            '.form-progress-container',
            '.model-recommendation',
            '.url-suggestion',
            '.validation-feedback',
            '.validation-warning'
        ];

        dynamicElements.forEach(selector => {
            document.querySelectorAll(selector).forEach(el => el.remove());
        });

        this.initialized = false;
    }
}

// Export for dynamic loading
export { FormEnhancementsModule };
export const init = async () => {
    const formEnhancements = new FormEnhancementsModule();
    await formEnhancements.init();
    return formEnhancements;
};