/**
 * Trial Mode Module - No-registration Experience
 * 
 * Enables users to experience HuskyApply without creating an account:
 * - Temporary session management
 * - Limited functionality showcase
 * - Conversion tracking and prompts
 * - Demo data handling
 */

class TrialModeModule {
    constructor() {
        this.core = window.HuskyApply;
        this.initialized = false;
        this.trialSessionId = null;
        this.trialStartTime = null;
        this.trialLimits = {
            maxApplications: 1,
            maxDemoData: 3,
            sessionTimeout: 30 * 60 * 1000 // 30 minutes
        };
        this.trialData = {
            applicationsCreated: 0,
            demoDataUsed: 0,
            featuresExplored: []
        };
    }

    async init() {
        if (this.initialized) return;
        
        console.log('Initializing Trial Mode module...');
        
        this.initializeTrialSession();
        this.setupTrialUI();
        this.setupEventListeners();
        this.startTrialTracking();
        
        this.initialized = true;
        console.log('Trial Mode module initialized');
    }

    initializeTrialSession() {
        // Generate unique trial session ID
        this.trialSessionId = 'trial_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
        this.trialStartTime = Date.now();
        
        // Load existing trial data if available
        const existingData = localStorage.getItem('huskyapply_trial_data');
        if (existingData) {
            try {
                this.trialData = { ...this.trialData, ...JSON.parse(existingData) };
            } catch (error) {
                console.warn('Failed to load trial data:', error);
            }
        }
        
        // Set trial mode flag
        localStorage.setItem('huskyapply_trial_mode', 'true');
        localStorage.setItem('huskyapply_trial_session', this.trialSessionId);
        
        this.core.logMetric('trial_session_started', 1, {
            session_id: this.trialSessionId
        });
    }

    setupTrialUI() {
        // Add trial mode banner
        this.addTrialBanner();
        
        // Modify form placeholders for trial
        this.updateFormForTrial();
        
        // Add trial-specific help text
        this.addTrialHelpTexts();
    }

    addTrialBanner() {
        const banner = document.createElement('div');
        banner.id = 'trial-mode-banner';
        banner.className = 'trial-mode-banner';
        banner.innerHTML = `
            <div class="trial-banner-content">
                <div class="trial-info">
                    <span class="trial-icon">🚀</span>
                    <span class="trial-text">
                        <strong>Free Trial Mode</strong> • 
                        Create <span class="trial-count">${this.trialLimits.maxApplications - this.trialData.applicationsCreated}</span> more cover letter
                    </span>
                </div>
                <div class="trial-actions">
                    <button class="btn btn-sm btn-outline trial-upgrade-btn">
                        Upgrade for Unlimited
                    </button>
                    <button class="trial-close-btn" aria-label="Close trial banner">&times;</button>
                </div>
            </div>
        `;

        // Insert banner after header
        const header = document.querySelector('.header');
        if (header) {
            header.insertAdjacentElement('afterend', banner);
        } else {
            document.body.insertAdjacentElement('afterbegin', banner);
        }

        // Add event listeners
        banner.querySelector('.trial-upgrade-btn')?.addEventListener('click', () => {
            this.showUpgradeModal();
        });

        banner.querySelector('.trial-close-btn')?.addEventListener('click', () => {
            banner.style.display = 'none';
        });
    }

    updateFormForTrial() {
        // Update job URL input placeholder
        const jobUrlInput = document.getElementById('jdUrlInput');
        if (jobUrlInput) {
            jobUrlInput.placeholder = 'Try: https://jobs.example.com/software-engineer (or use our samples)';
            
            // Add sample job suggestions
            this.addJobSuggestions(jobUrlInput);
        }

        // Update file input for trial
        const fileInput = document.getElementById('resumeFileInput');
        if (fileInput) {
            this.addDemoResumeOption(fileInput);
        }

        // Add trial tooltips
        this.addTrialTooltips();
    }

    addJobSuggestions(inputElement) {
        const suggestionsContainer = document.createElement('div');
        suggestionsContainer.className = 'trial-suggestions';
        suggestionsContainer.innerHTML = `
            <div class="suggestions-header">
                <span class="suggestions-icon">💡</span>
                <span>Try these sample job postings:</span>
            </div>
            <div class="suggestions-list">
                <button class="suggestion-item" data-url="https://example.com/jobs/frontend-developer">
                    Frontend Developer at TechCorp
                </button>
                <button class="suggestion-item" data-url="https://example.com/jobs/data-scientist">
                    Data Scientist at DataCorp
                </button>
                <button class="suggestion-item" data-url="https://example.com/jobs/product-manager">
                    Product Manager at StartupXYZ
                </button>
            </div>
        `;

        // Insert after the job URL input group
        const jobUrlGroup = inputElement.closest('.form-group');
        if (jobUrlGroup) {
            jobUrlGroup.insertAdjacentElement('afterend', suggestionsContainer);
        }

        // Add click handlers for suggestions
        suggestionsContainer.querySelectorAll('.suggestion-item').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                const url = btn.dataset.url;
                inputElement.value = url;
                inputElement.dispatchEvent(new Event('input', { bubbles: true }));
                this.trackTrialFeature('sample_job_used');
                
                // Hide suggestions after selection
                suggestionsContainer.style.display = 'none';
            });
        });

        // Show/hide suggestions based on input focus
        inputElement.addEventListener('focus', () => {
            if (!inputElement.value) {
                suggestionsContainer.style.display = 'block';
            }
        });

        inputElement.addEventListener('input', () => {
            if (inputElement.value) {
                suggestionsContainer.style.display = 'none';
            }
        });
    }

    addDemoResumeOption(fileInput) {
        const demoResumeContainer = document.createElement('div');
        demoResumeContainer.className = 'trial-demo-resume';
        demoResumeContainer.innerHTML = `
            <div class="demo-resume-option">
                <div class="demo-resume-header">
                    <span class="demo-icon">📋</span>
                    <span>Don't have your resume ready?</span>
                </div>
                <button class="btn btn-outline btn-sm use-demo-resume-btn">
                    Use Sample Resume
                </button>
                <p class="demo-resume-note">Perfect for testing our AI capabilities</p>
            </div>
        `;

        // Insert after file input group
        const fileGroup = fileInput.closest('.form-group');
        if (fileGroup) {
            fileGroup.insertAdjacentElement('afterend', demoResumeContainer);
        }

        // Add click handler
        const demoBtn = demoResumeContainer.querySelector('.use-demo-resume-btn');
        demoBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.useDemoResume();
        });
    }

    useDemoResume() {
        const fileInput = document.getElementById('resumeFileInput');
        const fileLabel = document.querySelector('.file-input-label span:last-child');
        
        if (fileLabel) {
            fileLabel.innerHTML = `
                <span style="color: var(--success);">
                    📄 sample-resume-tech.pdf (127 KB) - Demo Resume Loaded
                </span>
            `;
        }

        // Mark as demo data used
        this.trialData.demoDataUsed++;
        this.trackTrialFeature('demo_resume_used');
        this.saveTrialData();

        // Show demo resume details
        this.showDemoResumePreview();
    }

    showDemoResumePreview() {
        const modal = document.createElement('div');
        modal.className = 'demo-resume-preview-modal';
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h3>📄 Demo Resume Preview</h3>
                    <button class="close-btn">&times;</button>
                </div>
                <div class="modal-body">
                    <div class="resume-preview">
                        <h4>Alex Johnson</h4>
                        <p><strong>Senior Software Engineer</strong></p>
                        <p>📧 alex.johnson@email.com • 📱 (555) 123-4567</p>
                        
                        <h5>Experience:</h5>
                        <ul>
                            <li>5+ years full-stack development</li>
                            <li>Expert in React, Node.js, Python</li>
                            <li>Led teams of 3-5 developers</li>
                            <li>Built scalable web applications</li>
                        </ul>
                        
                        <h5>Skills:</h5>
                        <p>JavaScript, TypeScript, React, Node.js, Python, AWS, Docker, Git</p>
                    </div>
                    <p class="demo-note">
                        <strong>Note:</strong> This is sample data for demonstration purposes. 
                        In real use, upload your actual resume for personalized results.
                    </p>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-primary close-preview-btn">Got It</button>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        // Add close handlers
        const closeHandlers = modal.querySelectorAll('.close-btn, .close-preview-btn');
        closeHandlers.forEach(btn => {
            btn.addEventListener('click', () => {
                modal.remove();
            });
        });

        // Close on outside click
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.remove();
            }
        });
    }

    addTrialTooltips() {
        const tooltipConfig = {
            'modelProvider': {
                text: 'Try different AI models to see how they perform!',
                placement: 'top'
            },
            'modelName': {
                text: 'GPT-4o is fastest, Claude is best for creative writing',
                placement: 'top'
            }
        };

        Object.entries(tooltipConfig).forEach(([elementId, config]) => {
            const element = document.getElementById(elementId);
            if (element) {
                this.addTooltip(element, config.text, config.placement);
            }
        });
    }

    addTooltip(element, text, placement = 'top') {
        const tooltip = document.createElement('div');
        tooltip.className = 'trial-tooltip';
        tooltip.innerHTML = `
            <div class="tooltip-content">${text}</div>
            <div class="tooltip-arrow"></div>
        `;

        element.addEventListener('mouseenter', () => {
            document.body.appendChild(tooltip);
            this.positionTooltip(tooltip, element, placement);
            tooltip.classList.add('show');
        });

        element.addEventListener('mouseleave', () => {
            tooltip.remove();
        });
    }

    positionTooltip(tooltip, element, placement) {
        const rect = element.getBoundingClientRect();
        const tooltipRect = tooltip.getBoundingClientRect();

        let left, top;

        switch (placement) {
            case 'top':
                left = rect.left + (rect.width / 2) - (tooltipRect.width / 2);
                top = rect.top - tooltipRect.height - 10;
                break;
            case 'bottom':
                left = rect.left + (rect.width / 2) - (tooltipRect.width / 2);
                top = rect.bottom + 10;
                break;
            case 'left':
                left = rect.left - tooltipRect.width - 10;
                top = rect.top + (rect.height / 2) - (tooltipRect.height / 2);
                break;
            case 'right':
                left = rect.right + 10;
                top = rect.top + (rect.height / 2) - (tooltipRect.height / 2);
                break;
        }

        tooltip.style.left = `${left}px`;
        tooltip.style.top = `${top}px`;
    }

    addTrialHelpTexts() {
        const helpTexts = [
            {
                selector: '#jdUrlInput',
                text: '💡 Tip: Try our sample jobs or paste any job URL from LinkedIn, Indeed, etc.'
            },
            {
                selector: '#resumeFileInput',
                text: '⚡ Quick start: Use our sample resume to test the AI instantly'
            }
        ];

        helpTexts.forEach(({ selector, text }) => {
            const element = document.querySelector(selector);
            if (element) {
                const helpElement = element.parentNode.querySelector('.text-sm');
                if (helpElement) {
                    helpElement.innerHTML = text;
                    helpElement.classList.add('trial-help-text');
                }
            }
        });
    }

    setupEventListeners() {
        // Listen for form submission to check trial limits
        document.addEventListener('submit', (e) => {
            if (e.target.id === 'jobForm') {
                if (!this.canCreateApplication()) {
                    e.preventDefault();
                    this.showTrialLimitModal();
                    return false;
                }
                
                this.trialData.applicationsCreated++;
                this.saveTrialData();
                this.updateTrialBanner();
            }
        });

        // Listen for various trial interactions
        document.addEventListener('click', (e) => {
            const target = e.target;
            
            if (target.matches('.trial-upgrade-btn')) {
                this.showUpgradeModal();
            }
            
            if (target.matches('[data-trial-feature]')) {
                const feature = target.dataset.trialFeature;
                this.trackTrialFeature(feature);
            }
        });

        // Track time spent in trial
        this.setupTrialTimer();
    }

    setupTrialTimer() {
        // Update trial banner every minute
        setInterval(() => {
            this.updateTrialBanner();
            this.checkTrialTimeout();
        }, 60000);

        // Save trial data periodically
        setInterval(() => {
            this.saveTrialData();
        }, 30000);
    }

    canCreateApplication() {
        return this.trialData.applicationsCreated < this.trialLimits.maxApplications;
    }

    checkTrialTimeout() {
        const elapsed = Date.now() - this.trialStartTime;
        if (elapsed > this.trialLimits.sessionTimeout) {
            this.showTrialExpiredModal();
        }
    }

    updateTrialBanner() {
        const banner = document.getElementById('trial-mode-banner');
        if (!banner) return;

        const countElement = banner.querySelector('.trial-count');
        if (countElement) {
            const remaining = this.trialLimits.maxApplications - this.trialData.applicationsCreated;
            countElement.textContent = remaining;
            
            if (remaining === 0) {
                const trialText = banner.querySelector('.trial-text');
                if (trialText) {
                    trialText.innerHTML = `
                        <strong>Trial Completed</strong> • 
                        <span style="color: var(--success);">Upgrade to continue</span>
                    `;
                }
            }
        }
    }

    showTrialLimitModal() {
        const modal = document.createElement('div');
        modal.className = 'trial-limit-modal';
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h3>🚀 Trial Limit Reached</h3>
                </div>
                <div class="modal-body">
                    <div class="limit-message">
                        <p><strong>Great job!</strong> You've completed your free trial application.</p>
                        <p>Ready to unlock unlimited cover letters and advanced features?</p>
                    </div>
                    
                    <div class="trial-stats">
                        <div class="stat-item">
                            <div class="stat-icon">⏱️</div>
                            <div class="stat-content">
                                <div class="stat-value">${this.formatTrialTime()}</div>
                                <div class="stat-label">Time saved</div>
                            </div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-icon">✨</div>
                            <div class="stat-content">
                                <div class="stat-value">${this.trialData.featuresExplored.length}</div>
                                <div class="stat-label">Features explored</div>
                            </div>
                        </div>
                    </div>
                    
                    <div class="upgrade-options">
                        <div class="plan-option recommended">
                            <div class="plan-badge">Most Popular</div>
                            <h4>Pro Plan</h4>
                            <div class="plan-price">$9.99/month</div>
                            <ul class="plan-features">
                                <li>✅ Unlimited cover letters</li>
                                <li>✅ All AI models</li>
                                <li>✅ Custom templates</li>
                                <li>✅ Priority support</li>
                            </ul>
                            <button class="btn btn-primary upgrade-pro-btn">
                                Upgrade to Pro
                            </button>
                        </div>
                        
                        <div class="plan-option">
                            <h4>Free Plan</h4>
                            <div class="plan-price">$0/month</div>
                            <ul class="plan-features">
                                <li>✅ 3 cover letters/month</li>
                                <li>✅ Basic AI model</li>
                                <li>❌ Limited templates</li>
                                <li>❌ Email support only</li>
                            </ul>
                            <button class="btn btn-outline create-free-btn">
                                Create Free Account
                            </button>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-secondary continue-trial-btn">
                        Continue Browsing
                    </button>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        // Add event handlers
        modal.querySelector('.upgrade-pro-btn')?.addEventListener('click', () => {
            this.redirectToSignup('pro');
        });

        modal.querySelector('.create-free-btn')?.addEventListener('click', () => {
            this.redirectToSignup('free');
        });

        modal.querySelector('.continue-trial-btn')?.addEventListener('click', () => {
            modal.remove();
        });

        // Track modal show
        this.core.logMetric('trial_limit_modal_shown', 1);
    }

    showUpgradeModal() {
        // Similar to trial limit modal but focused on upgrade benefits
        const modal = document.createElement('div');
        modal.className = 'upgrade-modal';
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h3>🚀 Unlock Full Potential</h3>
                    <button class="close-btn">&times;</button>
                </div>
                <div class="modal-body">
                    <div class="upgrade-benefits">
                        <h4>Why upgrade?</h4>
                        <div class="benefit-grid">
                            <div class="benefit-item">
                                <div class="benefit-icon">♾️</div>
                                <h5>Unlimited Applications</h5>
                                <p>Create as many cover letters as you need</p>
                            </div>
                            <div class="benefit-item">
                                <div class="benefit-icon">⚙️</div>
                                <h5>All AI Models</h5>
                                <p>Access GPT-4, Claude, and future models</p>
                            </div>
                            <div class="benefit-item">
                                <div class="benefit-icon">🎨</div>
                                <h5>Custom Templates</h5>
                                <p>Professional templates for any industry</p>
                            </div>
                            <div class="benefit-item">
                                <div class="benefit-icon">📊</div>
                                <h5>Success Analytics</h5>
                                <p>Track application success rates</p>
                            </div>
                        </div>
                    </div>
                    
                    <div class="pricing-simple">
                        <div class="price-highlight">
                            <span class="price">$9.99/month</span>
                            <span class="price-note">Cancel anytime</span>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-outline continue-trial-btn">Maybe Later</button>
                    <button class="btn btn-primary upgrade-now-btn">Upgrade Now</button>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        // Event handlers
        modal.querySelector('.close-btn')?.addEventListener('click', () => modal.remove());
        modal.querySelector('.continue-trial-btn')?.addEventListener('click', () => modal.remove());
        modal.querySelector('.upgrade-now-btn')?.addEventListener('click', () => {
            this.redirectToSignup('pro');
        });

        // Track upgrade modal
        this.core.logMetric('upgrade_modal_shown', 1, {
            trigger: 'banner_click'
        });
    }

    trackTrialFeature(feature) {
        if (!this.trialData.featuresExplored.includes(feature)) {
            this.trialData.featuresExplored.push(feature);
        }
        
        this.core.logMetric('trial_feature_used', 1, {
            feature: feature,
            session_id: this.trialSessionId
        });
    }

    formatTrialTime() {
        const elapsed = Date.now() - this.trialStartTime;
        const minutes = Math.floor(elapsed / 60000);
        if (minutes < 1) return 'Less than 1 minute';
        if (minutes === 1) return '1 minute';
        return `${minutes} minutes`;
    }

    saveTrialData() {
        localStorage.setItem('huskyapply_trial_data', JSON.stringify(this.trialData));
    }

    redirectToSignup(plan = 'free') {
        // Track conversion
        this.core.logMetric('trial_conversion', 1, {
            plan: plan,
            session_id: this.trialSessionId,
            features_explored: this.trialData.featuresExplored.length,
            time_spent: this.formatTrialTime()
        });

        // Redirect to signup with trial data
        const signupUrl = `signup.html?plan=${plan}&trial=${this.trialSessionId}&source=trial_mode`;
        window.location.href = signupUrl;
    }

    endTrialSession() {
        localStorage.removeItem('huskyapply_trial_mode');
        localStorage.removeItem('huskyapply_trial_session');
        localStorage.removeItem('huskyapply_trial_data');
        
        this.core.logMetric('trial_session_ended', 1, {
            session_id: this.trialSessionId,
            applications_created: this.trialData.applicationsCreated,
            features_explored: this.trialData.featuresExplored.length,
            duration: this.formatTrialTime()
        });
    }

    destroy() {
        // Clean up trial UI elements
        document.getElementById('trial-mode-banner')?.remove();
        document.querySelectorAll('.trial-suggestions')?.forEach(el => el.remove());
        document.querySelectorAll('.trial-demo-resume')?.forEach(el => el.remove());
        
        this.initialized = false;
    }
}

// Export for dynamic loading
export { TrialModeModule };
export const init = async () => {
    const trialMode = new TrialModeModule();
    await trialMode.init();
    return trialMode;
};