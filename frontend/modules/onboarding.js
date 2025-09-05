/**
 * Onboarding Module - New User Guidance and Trial Experience
 * 
 * This module provides a guided experience for new users including:
 * - Interactive onboarding flow
 * - Trial mode (no registration required)
 * - Demo data and examples
 * - Progress tracking and celebration
 */

class OnboardingModule {
    constructor() {
        this.core = window.HuskyApply;
        this.initialized = false;
        this.currentStep = 0;
        this.isTrialMode = false;
        this.onboardingSteps = [
            {
                id: 'welcome',
                title: 'Welcome to HuskyApply!',
                description: 'Let us show you how AI can transform your job applications',
                action: 'showWelcome'
            },
            {
                id: 'demo-job',
                title: 'Try with Sample Data',
                description: 'Experience our AI with a real job posting example',
                action: 'showDemoJob'
            },
            {
                id: 'upload-guide',
                title: 'Upload Your Resume',
                description: 'Drag & drop or browse for your resume file',
                action: 'showUploadGuide'
            },
            {
                id: 'ai-selection',
                title: 'Choose AI Model',
                description: 'Select the best AI model for your application',
                action: 'showAISelection'
            },
            {
                id: 'processing',
                title: 'Watch AI in Action',
                description: 'See real-time AI processing of your application',
                action: 'showProcessingDemo'
            },
            {
                id: 'results',
                title: 'Your Personalized Cover Letter',
                description: 'Review and customize your AI-generated content',
                action: 'showResults'
            },
            {
                id: 'completion',
                title: 'Congratulations!',
                description: 'You\'ve completed your first AI-powered application',
                action: 'showCompletion'
            }
        ];
    }

    async init() {
        if (this.initialized) return;
        
        console.log('Initializing Onboarding module...');
        
        this.detectNewUser();
        this.setupEventListeners();
        this.createOnboardingUI();
        this.initialized = true;
        
        console.log('Onboarding module initialized');
    }

    detectNewUser() {
        const hasSeenOnboarding = localStorage.getItem('huskyapply_onboarding_completed');
        const isFirstVisit = !localStorage.getItem('huskyapply_visited_before');
        
        if (!hasSeenOnboarding || isFirstVisit) {
            this.showOnboardingModal();
        }
        
        localStorage.setItem('huskyapply_visited_before', 'true');
    }

    setupEventListeners() {
        // Listen for user interactions that might trigger guidance
        document.addEventListener('click', (e) => {
            if (e.target.matches('.start-trial-btn')) {
                this.startTrialMode();
            }
            
            if (e.target.matches('.skip-onboarding-btn')) {
                this.skipOnboarding();
            }
            
            if (e.target.matches('.restart-onboarding-btn')) {
                this.restartOnboarding();
            }
        });

        // Form focus events for contextual help
        document.addEventListener('focus', (e) => {
            if (this.isTrialMode && e.target.matches('input, select, textarea')) {
                this.showContextualHelp(e.target);
            }
        }, true);
    }

    createOnboardingUI() {
        // Create onboarding overlay structure
        const onboardingHTML = `
            <div id="onboarding-modal" class="onboarding-modal hidden">
                <div class="onboarding-content">
                    <div class="onboarding-header">
                        <div class="onboarding-progress">
                            <div class="progress-track">
                                <div class="progress-fill" id="onboarding-progress-fill"></div>
                            </div>
                            <span class="progress-text" id="onboarding-progress-text">Step 1 of 7</span>
                        </div>
                        <button class="close-btn" id="close-onboarding">&times;</button>
                    </div>
                    
                    <div class="onboarding-body" id="onboarding-body">
                        <!-- Dynamic content will be inserted here -->
                    </div>
                    
                    <div class="onboarding-footer">
                        <button class="btn btn-secondary" id="onboarding-prev" disabled>Previous</button>
                        <div class="onboarding-actions">
                            <button class="btn btn-outline skip-onboarding-btn">Skip Tour</button>
                            <button class="btn btn-primary" id="onboarding-next">Get Started</button>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- Floating help button -->
            <button id="help-floating-btn" class="help-floating-btn hidden">
                <span class="help-icon">?</span>
                <span class="help-text">Need Help?</span>
            </button>
            
            <!-- Contextual tooltips -->
            <div id="contextual-tooltip" class="contextual-tooltip hidden">
                <div class="tooltip-content"></div>
                <div class="tooltip-arrow"></div>
            </div>
        `;
        
        document.body.insertAdjacentHTML('beforeend', onboardingHTML);
        this.bindOnboardingEvents();
    }

    bindOnboardingEvents() {
        const modal = document.getElementById('onboarding-modal');
        const closeBtn = document.getElementById('close-onboarding');
        const prevBtn = document.getElementById('onboarding-prev');
        const nextBtn = document.getElementById('onboarding-next');
        const helpBtn = document.getElementById('help-floating-btn');

        closeBtn?.addEventListener('click', () => this.hideOnboarding());
        prevBtn?.addEventListener('click', () => this.previousStep());
        nextBtn?.addEventListener('click', () => this.nextStep());
        helpBtn?.addEventListener('click', () => this.showHelpMenu());

        // Close modal on outside click
        modal?.addEventListener('click', (e) => {
            if (e.target === modal) {
                this.hideOnboarding();
            }
        });
    }

    showOnboardingModal() {
        const modal = document.getElementById('onboarding-modal');
        if (modal) {
            modal.classList.remove('hidden');
            this.currentStep = 0;
            this.renderCurrentStep();
            
            // Track onboarding start
            this.core.logMetric('onboarding_started', 1);
        }
    }

    hideOnboarding() {
        const modal = document.getElementById('onboarding-modal');
        if (modal) {
            modal.classList.add('hidden');
            this.showFloatingHelp();
        }
    }

    renderCurrentStep() {
        const step = this.onboardingSteps[this.currentStep];
        if (!step) return;

        const body = document.getElementById('onboarding-body');
        const prevBtn = document.getElementById('onboarding-prev');
        const nextBtn = document.getElementById('onboarding-next');
        const progressFill = document.getElementById('onboarding-progress-fill');
        const progressText = document.getElementById('onboarding-progress-text');

        // Update progress
        const progress = ((this.currentStep + 1) / this.onboardingSteps.length) * 100;
        if (progressFill) progressFill.style.width = `${progress}%`;
        if (progressText) progressText.textContent = `Step ${this.currentStep + 1} of ${this.onboardingSteps.length}`;

        // Update navigation buttons
        if (prevBtn) prevBtn.disabled = this.currentStep === 0;
        if (nextBtn) {
            nextBtn.textContent = this.currentStep === this.onboardingSteps.length - 1 ? 'Complete' : 'Next';
        }

        // Render step content
        if (body && this[step.action]) {
            this[step.action](body);
        }
    }

    showWelcome(container) {
        container.innerHTML = `
            <div class="onboarding-step welcome-step">
                <div class="step-icon">🚀</div>
                <h2>Welcome to HuskyApply!</h2>
                <p>Transform your job hunting with AI-powered cover letters that get you noticed.</p>
                
                <div class="feature-highlights">
                    <div class="feature-item">
                        <span class="feature-icon">🎯</span>
                        <span>Personalized for each job</span>
                    </div>
                    <div class="feature-item">
                        <span class="feature-icon">⚡</span>
                        <span>Generated in under 2 minutes</span>
                    </div>
                    <div class="feature-item">
                        <span class="feature-icon">🔥</span>
                        <span>Powered by latest AI models</span>
                    </div>
                </div>
                
                <div class="welcome-actions">
                    <button class="btn btn-primary start-trial-btn">
                        🎉 Try Free Sample
                    </button>
                    <p class="trial-note">No signup required • Takes 2 minutes</p>
                </div>
            </div>
        `;
    }

    showDemoJob(container) {
        const sampleJobs = [
            {
                company: 'TechCorp Inc.',
                title: 'Senior Software Engineer',
                url: 'https://example.com/jobs/senior-software-engineer',
                description: 'Full-stack development position with React and Node.js'
            },
            {
                company: 'DataSystems LLC',
                title: 'Data Scientist',
                url: 'https://example.com/jobs/data-scientist',
                description: 'Machine learning and analytics role with Python'
            },
            {
                company: 'DesignStudio Co.',
                title: 'UX Designer',
                url: 'https://example.com/jobs/ux-designer',
                description: 'User experience design for mobile applications'
            }
        ];

        container.innerHTML = `
            <div class="onboarding-step demo-job-step">
                <div class="step-icon">💼</div>
                <h2>Let's Start with a Sample Job</h2>
                <p>Choose from these sample job postings to see HuskyApply in action:</p>
                
                <div class="sample-jobs">
                    ${sampleJobs.map((job, index) => `
                        <div class="sample-job" data-job-index="${index}">
                            <div class="job-header">
                                <h4>${job.title}</h4>
                                <span class="company">${job.company}</span>
                            </div>
                            <p class="job-description">${job.description}</p>
                            <button class="btn btn-outline use-sample-btn" data-url="${job.url}">
                                Use This Sample
                            </button>
                        </div>
                    `).join('')}
                </div>
                
                <div class="step-note">
                    <p>💡 <strong>Pro Tip:</strong> In real use, you'd paste any job posting URL from LinkedIn, Indeed, or company career pages.</p>
                </div>
            </div>
        `;

        // Add event listeners for sample job selection
        container.querySelectorAll('.use-sample-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const url = e.target.dataset.url;
                this.fillDemoJobUrl(url);
                this.nextStep();
            });
        });
    }

    showUploadGuide(container) {
        container.innerHTML = `
            <div class="onboarding-step upload-guide-step">
                <div class="step-icon">📄</div>
                <h2>Upload Your Resume</h2>
                <p>Your resume helps our AI understand your background and create personalized cover letters.</p>
                
                <div class="upload-demo">
                    <div class="demo-upload-zone">
                        <div class="upload-icon">📁</div>
                        <p><strong>Drag your resume here</strong></p>
                        <p>or <button class="btn btn-link demo-browse-btn">browse files</button></p>
                        <small>Supports PDF, DOC, DOCX • Max 10MB</small>
                    </div>
                </div>
                
                <div class="upload-tips">
                    <h4>Upload Tips:</h4>
                    <ul>
                        <li>✅ Use your most recent resume</li>
                        <li>✅ PDF format works best</li>
                        <li>✅ Include relevant keywords</li>
                        <li>❌ Don't include personal photos</li>
                    </ul>
                </div>
                
                <div class="trial-option">
                    <hr>
                    <p><strong>Don't have your resume ready?</strong></p>
                    <button class="btn btn-secondary use-demo-resume-btn">
                        📋 Use Sample Resume
                    </button>
                    <small>We'll use a sample resume for this demo</small>
                </div>
            </div>
        `;

        // Add event listeners
        const demoResumeBtn = container.querySelector('.use-demo-resume-btn');
        demoResumeBtn?.addEventListener('click', () => {
            this.useDemoResume();
            this.nextStep();
        });

        const browsBtn = container.querySelector('.demo-browse-btn');
        browsBtn?.addEventListener('click', () => {
            this.triggerRealFileUpload();
        });
    }

    showAISelection(container) {
        container.innerHTML = `
            <div class="onboarding-step ai-selection-step">
                <div class="step-icon">⚙️</div>
                <h2>Choose Your AI Assistant</h2>
                <p>Different AI models have unique strengths. Here's our recommendation:</p>
                
                <div class="ai-models-comparison">
                    <div class="ai-model recommended">
                        <div class="model-badge">Recommended</div>
                        <h4>OpenAI GPT-4o</h4>
                        <div class="model-features">
                            <div class="feature">⚡ Fastest processing</div>
                            <div class="feature">🎯 Best for tech roles</div>
                            <div class="feature">📊 Most popular choice</div>
                        </div>
                        <button class="btn btn-primary select-model-btn" data-provider="openai" data-model="gpt-4o">
                            Select GPT-4o
                        </button>
                    </div>
                    
                    <div class="ai-model">
                        <h4>Anthropic Claude</h4>
                        <div class="model-features">
                            <div class="feature">✍️ Creative writing</div>
                            <div class="feature">📚 Great for academia</div>
                            <div class="feature">🎨 Excellent for creative roles</div>
                        </div>
                        <button class="btn btn-outline select-model-btn" data-provider="anthropic" data-model="claude-3-5-sonnet-20241022">
                            Select Claude
                        </button>
                    </div>
                </div>
                
                <div class="ai-explanation">
                    <h4>💡 How Our AI Works:</h4>
                    <div class="process-steps">
                        <div class="process-step">1️⃣ Analyzes the job requirements</div>
                        <div class="process-step">2️⃣ Reviews your resume and experience</div>
                        <div class="process-step">3️⃣ Creates personalized, compelling content</div>
                        <div class="process-step">4️⃣ Formats it professionally</div>
                    </div>
                </div>
            </div>
        `;

        // Add event listeners for model selection
        container.querySelectorAll('.select-model-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const provider = e.target.dataset.provider;
                const model = e.target.dataset.model;
                this.selectAIModel(provider, model);
                this.nextStep();
            });
        });
    }

    showProcessingDemo(container) {
        container.innerHTML = `
            <div class="onboarding-step processing-demo-step">
                <div class="step-icon">⚙️</div>
                <h2>Watch AI Create Your Cover Letter</h2>
                <p>Our AI is now analyzing the job posting and your resume to create a personalized cover letter.</p>
                
                <div class="demo-processing">
                    <div class="processing-stages">
                        <div class="stage" data-stage="analysis">
                            <div class="stage-icon">🔍</div>
                            <div class="stage-content">
                                <h4>Analyzing Job Requirements</h4>
                                <p>Understanding key skills and qualifications...</p>
                            </div>
                            <div class="stage-status">⏳</div>
                        </div>
                        
                        <div class="stage" data-stage="matching">
                            <div class="stage-icon">🎯</div>
                            <div class="stage-content">
                                <h4>Matching Your Experience</h4>
                                <p>Finding relevant skills from your resume...</p>
                            </div>
                            <div class="stage-status">⏳</div>
                        </div>
                        
                        <div class="stage" data-stage="writing">
                            <div class="stage-icon">✍️</div>
                            <div class="stage-content">
                                <h4>Crafting Personalized Content</h4>
                                <p>Writing compelling, tailored content...</p>
                            </div>
                            <div class="stage-status">⏳</div>
                        </div>
                        
                        <div class="stage" data-stage="formatting">
                            <div class="stage-icon">📄</div>
                            <div class="stage-content">
                                <h4>Professional Formatting</h4>
                                <p>Applying professional formatting...</p>
                            </div>
                            <div class="stage-status">⏳</div>
                        </div>
                    </div>
                    
                    <div class="processing-progress">
                        <div class="progress-bar">
                            <div class="progress-fill" id="demo-progress"></div>
                        </div>
                        <p class="progress-text">Processing... <span id="demo-progress-percent">0%</span></p>
                    </div>
                </div>
                
                <div class="processing-tips">
                    <h4>⏱️ Real Processing Time:</h4>
                    <ul>
                        <li>Simple jobs: 30-60 seconds</li>
                        <li>Complex jobs: 1-2 minutes</li>
                        <li>Multiple applications: Process in batches</li>
                    </ul>
                </div>
            </div>
        `;

        // Start demo processing animation
        this.startDemoProcessing();
    }

    showResults(container) {
        const sampleCoverLetter = `Dear Hiring Manager,

I am writing to express my strong interest in the Senior Software Engineer position at TechCorp Inc. With over 5 years of experience in full-stack development and a proven track record of delivering scalable web applications, I am excited about the opportunity to contribute to your innovative team.

Your job posting highlights the need for expertise in React and Node.js, which aligns perfectly with my background. In my current role at ABC Technologies, I led the development of a customer portal using React and Node.js that increased user engagement by 40% and reduced page load times by 60%. Additionally, I architected and implemented a microservices infrastructure that improved system reliability and reduced deployment time from hours to minutes.

What particularly excites me about this role is TechCorp's commitment to pushing technological boundaries. I am passionate about staying at the forefront of technology trends and have recently completed certifications in cloud architecture and modern DevOps practices. I believe my experience in building resilient, user-focused applications would be valuable in helping TechCorp achieve its ambitious goals.

I would welcome the opportunity to discuss how my technical expertise and passion for innovation can contribute to your team's success. Thank you for considering my application.

Best regards,
[Your Name]`;

        container.innerHTML = `
            <div class="onboarding-step results-step">
                <div class="step-icon">✨</div>
                <h2>Your AI-Generated Cover Letter</h2>
                <p>Here's a personalized cover letter based on the job requirements and your experience:</p>
                
                <div class="results-preview">
                    <div class="cover-letter-preview">
                        <div class="preview-header">
                            <h4>📄 Cover Letter Preview</h4>
                            <div class="quality-score">
                                <span class="score">95%</span>
                                <span class="label">Match Score</span>
                            </div>
                        </div>
                        
                        <div class="letter-content">
                            ${sampleCoverLetter.split('\n\n').map(paragraph => 
                                `<p>${paragraph}</p>`
                            ).join('')}
                        </div>
                    </div>
                    
                    <div class="results-actions">
                        <button class="btn btn-secondary demo-copy-btn">
                            📋 Copy Text
                        </button>
                        <button class="btn btn-secondary demo-download-btn">
                            💾 Download PDF
                        </button>
                        <button class="btn btn-primary demo-edit-btn">
                            ✏️ Edit & Customize
                        </button>
                    </div>
                </div>
                
                <div class="ai-insights">
                    <h4>🎯 AI Insights:</h4>
                    <div class="insight-items">
                        <div class="insight">✅ Highlighted relevant React/Node.js experience</div>
                        <div class="insight">✅ Included quantified achievements</div>
                        <div class="insight">✅ Matched company values and culture</div>
                        <div class="insight">✅ Professional tone and structure</div>
                    </div>
                </div>
            </div>
        `;

        // Add demo interactions
        const copyBtn = container.querySelector('.demo-copy-btn');
        const downloadBtn = container.querySelector('.demo-download-btn');
        const editBtn = container.querySelector('.demo-edit-btn');

        copyBtn?.addEventListener('click', () => {
            this.showTooltip(copyBtn, 'Copied to clipboard! ✅');
        });

        downloadBtn?.addEventListener('click', () => {
            this.showTooltip(downloadBtn, 'PDF downloaded! 📄');
        });

        editBtn?.addEventListener('click', () => {
            this.showEditDemo();
        });
    }

    showCompletion(container) {
        container.innerHTML = `
            <div class="onboarding-step completion-step">
                <div class="step-icon celebration">🎉</div>
                <h2>Congratulations!</h2>
                <p>You've successfully created your first AI-powered cover letter!</p>
                
                <div class="completion-stats">
                    <div class="stat-item">
                        <div class="stat-value">2:15</div>
                        <div class="stat-label">Time Saved</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value">95%</div>
                        <div class="stat-label">Match Score</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value">3x</div>
                        <div class="stat-label">Faster Than Manual</div>
                    </div>
                </div>
                
                <div class="next-steps">
                    <h4>🚀 Ready to Get Started?</h4>
                    <div class="next-step-options">
                        <button class="btn btn-primary create-account-btn">
                            Create Free Account
                        </button>
                        <button class="btn btn-outline try-another-btn">
                            Try Another Job
                        </button>
                    </div>
                    
                    <div class="pricing-preview">
                        <p><strong>Free Plan:</strong> 3 cover letters per month</p>
                        <p><strong>Pro Plan:</strong> Unlimited + advanced features</p>
                    </div>
                </div>
                
                <div class="success-tips">
                    <h4>💡 Tips for Success:</h4>
                    <ul>
                        <li>Customize each letter for the specific job</li>
                        <li>Review and personalize AI suggestions</li>
                        <li>Track which applications get responses</li>
                        <li>Use our templates for consistency</li>
                    </ul>
                </div>
            </div>
        `;

        // Add completion actions
        const createAccountBtn = container.querySelector('.create-account-btn');
        const tryAnotherBtn = container.querySelector('.try-another-btn');

        createAccountBtn?.addEventListener('click', () => {
            this.completeOnboarding();
            this.redirectToSignup();
        });

        tryAnotherBtn?.addEventListener('click', () => {
            this.completeOnboarding();
            this.startNewApplication();
        });
    }

    // Helper methods for onboarding flow
    fillDemoJobUrl(url) {
        const jobUrlInput = document.getElementById('jdUrlInput');
        if (jobUrlInput) {
            jobUrlInput.value = url;
            jobUrlInput.classList.add('demo-filled');
        }
    }

    useDemoResume() {
        const fileInput = document.getElementById('resumeFileInput');
        const fileLabel = document.querySelector('.file-input-label span:last-child');
        
        if (fileLabel) {
            fileLabel.textContent = 'demo-resume.pdf (125 KB) - Sample Resume Loaded';
            fileInput?.classList.add('demo-filled');
        }
    }

    selectAIModel(provider, model) {
        const providerSelect = document.getElementById('modelProvider');
        const modelSelect = document.getElementById('modelName');
        
        if (providerSelect) {
            providerSelect.value = provider;
            // Trigger change event to show correct models
            providerSelect.dispatchEvent(new Event('change'));
        }
        
        if (modelSelect) {
            setTimeout(() => {
                modelSelect.value = model;
                modelSelect.classList.add('demo-filled');
            }, 100);
        }
    }

    startDemoProcessing() {
        const stages = ['analysis', 'matching', 'writing', 'formatting'];
        let currentStage = 0;
        let progress = 0;

        const updateProgress = () => {
            progress += Math.random() * 15 + 5;
            if (progress > 100) progress = 100;

            const progressFill = document.getElementById('demo-progress');
            const progressPercent = document.getElementById('demo-progress-percent');
            
            if (progressFill) progressFill.style.width = `${progress}%`;
            if (progressPercent) progressPercent.textContent = `${Math.round(progress)}%`;

            // Update stage status
            if (currentStage < stages.length) {
                const stageEl = document.querySelector(`[data-stage="${stages[currentStage]}"]`);
                if (stageEl) {
                    const status = stageEl.querySelector('.stage-status');
                    if (status) {
                        if (progress > (currentStage + 1) * 25) {
                            status.textContent = '✅';
                            currentStage++;
                        }
                    }
                }
            }

            if (progress < 100) {
                setTimeout(updateProgress, 200 + Math.random() * 300);
            } else {
                setTimeout(() => this.nextStep(), 1000);
            }
        };

        updateProgress();
    }

    showTooltip(element, message) {
        const tooltip = document.getElementById('contextual-tooltip');
        if (!tooltip) return;

        const content = tooltip.querySelector('.tooltip-content');
        if (content) content.textContent = message;

        // Position tooltip
        const rect = element.getBoundingClientRect();
        tooltip.style.left = `${rect.left + rect.width / 2}px`;
        tooltip.style.top = `${rect.bottom + 10}px`;
        
        tooltip.classList.remove('hidden');

        setTimeout(() => {
            tooltip.classList.add('hidden');
        }, 2000);
    }

    nextStep() {
        if (this.currentStep < this.onboardingSteps.length - 1) {
            this.currentStep++;
            this.renderCurrentStep();
            
            // Track step completion
            this.core.logMetric('onboarding_step_completed', 1, {
                step: this.currentStep,
                step_name: this.onboardingSteps[this.currentStep - 1]?.id
            });
        } else {
            this.completeOnboarding();
        }
    }

    previousStep() {
        if (this.currentStep > 0) {
            this.currentStep--;
            this.renderCurrentStep();
        }
    }

    completeOnboarding() {
        localStorage.setItem('huskyapply_onboarding_completed', 'true');
        this.hideOnboarding();
        this.showCompletionCelebration();
        
        // Track completion
        this.core.logMetric('onboarding_completed', 1, {
            steps_completed: this.currentStep + 1,
            total_steps: this.onboardingSteps.length
        });
    }

    skipOnboarding() {
        localStorage.setItem('huskyapply_onboarding_completed', 'true');
        this.hideOnboarding();
        this.showFloatingHelp();
        
        // Track skip
        this.core.logMetric('onboarding_skipped', 1, {
            step_when_skipped: this.currentStep
        });
    }

    showCompletionCelebration() {
        // Show a brief celebration animation
        const celebration = document.createElement('div');
        celebration.className = 'completion-celebration';
        celebration.innerHTML = `
            <div class="celebration-content">
                <div class="celebration-icon">🎉</div>
                <h3>Welcome to HuskyApply!</h3>
                <p>You're all set to create amazing cover letters</p>
            </div>
        `;
        
        document.body.appendChild(celebration);
        
        setTimeout(() => {
            celebration.classList.add('fade-out');
            setTimeout(() => celebration.remove(), 500);
        }, 3000);
    }

    showFloatingHelp() {
        const helpBtn = document.getElementById('help-floating-btn');
        if (helpBtn) {
            helpBtn.classList.remove('hidden');
        }
    }

    startTrialMode() {
        this.isTrialMode = true;
        // Enable trial mode features
        this.enableTrialFeatures();
        this.nextStep();
    }

    enableTrialFeatures() {
        // Show trial mode indicator
        const trialBanner = document.createElement('div');
        trialBanner.className = 'trial-mode-banner';
        trialBanner.innerHTML = `
            <div class="trial-content">
                <span class="trial-icon">🎯</span>
                <span>Trial Mode - Experience HuskyApply risk-free!</span>
                <button class="btn btn-sm btn-outline">Upgrade to Pro</button>
            </div>
        `;
        
        document.querySelector('.header')?.appendChild(trialBanner);
    }

    // Public methods for external integration
    restartOnboarding() {
        localStorage.removeItem('huskyapply_onboarding_completed');
        this.currentStep = 0;
        this.showOnboardingModal();
    }

    triggerRealFileUpload() {
        const realFileInput = document.getElementById('resumeFileInput');
        realFileInput?.click();
    }

    showContextualHelp(element) {
        const helpTexts = {
            'jdUrlInput': 'Paste any job posting URL from LinkedIn, Indeed, or company websites',
            'resumeFileInput': 'Upload your latest resume in PDF, DOC, or DOCX format',
            'modelProvider': 'OpenAI is great for most jobs, Claude excels at creative writing',
            'modelName': 'GPT-4o is our fastest and most popular choice'
        };

        const helpText = helpTexts[element.id];
        if (helpText) {
            this.showTooltip(element, helpText);
        }
    }

    destroy() {
        const modal = document.getElementById('onboarding-modal');
        const helpBtn = document.getElementById('help-floating-btn');
        const tooltip = document.getElementById('contextual-tooltip');
        
        modal?.remove();
        helpBtn?.remove();
        tooltip?.remove();
        
        this.initialized = false;
    }
}

// Export for dynamic loading
export { OnboardingModule };
export const init = async () => {
    const onboarding = new OnboardingModule();
    await onboarding.init();
    return onboarding;
};