/**
 * Processing Module
 * Handles real-time job processing updates and SSE connections
 */

import { AppCore } from '../app-core.js';

export default class ProcessingModule {
    constructor() {
        this.initialized = false;
        this.sseConnection = null;
        this.isProcessing = false;
        this.jobId = null;
        this.streamingStarted = false;
    }
    
    async init() {
        if (this.initialized) return;
        
        console.log('⚙️ Initializing Processing Module...');
        
        // Setup processing UI components
        this.setupProcessingSteps();
        this.setupResultActions();
        
        this.initialized = true;
        console.log('✅ Processing Module Ready');
    }
    
    setupProcessingSteps() {
        // Initialize processing step indicators
        const processingSteps = document.querySelectorAll('.processing-step');
        processingSteps.forEach((step, index) => {
            setTimeout(() => {
                step.classList.add('fade-in');
            }, index * 200);
        });
    }
    
    setupResultActions() {
        // Copy button
        const copyBtn = document.getElementById('copyBtn');
        if (copyBtn) {
            copyBtn.addEventListener('click', () => this.copyToClipboard());
        }
        
        // Download button
        const downloadBtn = document.getElementById('downloadBtn');
        if (downloadBtn) {
            downloadBtn.addEventListener('click', () => this.downloadPDF());
        }
        
        // Edit button
        const editBtn = document.getElementById('editBtn');
        if (editBtn) {
            editBtn.addEventListener('click', () => this.enableEditing());
        }
        
        // Regenerate button
        const regenerateBtn = document.getElementById('regenerateBtn');
        if (regenerateBtn) {
            regenerateBtn.addEventListener('click', () => this.regenerateContent());
        }
        
        // Share button
        const shareBtn = document.getElementById('shareBtn');
        if (shareBtn) {
            shareBtn.addEventListener('click', () => this.shareContent());
        }
        
        // Save template button
        const saveTemplateBtn = document.getElementById('saveTemplateBtn');
        if (saveTemplateBtn) {
            saveTemplateBtn.addEventListener('click', () => this.saveAsTemplate());
        }
    }
    
    startStatusUpdates(jobId) {
        if (!jobId) {
            console.error('No job ID provided for status updates');
            return;
        }
        
        this.jobId = jobId;
        AppCore.state.jobId = jobId;
        
        const token = localStorage.getItem('jwtToken');
        if (!token) {
            console.error('No authentication token for SSE connection');
            return;
        }
        
        const sseUrl = `${AppCore.config.API_BASE}/applications/${jobId}/stream?token=${encodeURIComponent(token)}`;
        
        this.sseConnection = new EventSource(sseUrl);
        this.isProcessing = true;
        
        this.setupSSEHandlers();
        this.startProcessingAnimation();
        this.startTimeEstimation();
    }
    
    setupSSEHandlers() {
        const sse = this.sseConnection;
        
        sse.onopen = () => {
            this.addLog('🔗 Connected to processing stream');
            this.updateStatus('processing', 'Connected to processing stream');
        };
        
        sse.onmessage = async (event) => {
            try {
                const data = JSON.parse(event.data);
                await this.handleStatusUpdate(data);
            } catch (error) {
                console.error('SSE message parsing error:', error);
                this.addLog(`❌ Error parsing status: ${error.message}`);
            }
        };
        
        // Handle streaming events specifically
        sse.addEventListener('streaming', async (event) => {
            try {
                const data = JSON.parse(event.data);
                await this.handleStreamingUpdate(data);
            } catch (error) {
                console.error('Streaming event parsing error:', error);
                this.addLog(`❌ Error parsing streaming data: ${error.message}`);
            }
        });
        
        // Handle heartbeat events
        sse.addEventListener('heartbeat', (event) => {
            console.log('📡 Heartbeat received');
        });
        
        sse.onerror = () => {
            this.addLog('❌ Connection error, retrying...');
            setTimeout(() => {
                if (this.isProcessing) {
                    this.reconnectSSE();
                }
            }, 1000);
        };
    }
    
    async handleStatusUpdate(data) {
        const { status, progress, message, step } = data;
        
        this.addLog(`📊 ${message || status}`);
        
        // Update progress
        if (progress !== undefined) {
            this.updateProgress(progress);
        }
        
        // Update current processing step
        if (step) {
            this.updateProcessingStep(step);
        }
        
        // Handle completion
        if (status === 'COMPLETED' || status === 'FAILED') {
            await this.handleCompletion(status);
        }
    }
    
    async handleStreamingUpdate(data) {
        const { status, generatedText, message, streamingData } = data;
        
        // Start showing streaming content if this is the first streaming update
        if (!this.streamingStarted) {
            this.initStreamingDisplay();
            this.streamingStarted = true;
        }
        
        // Update streaming content progressively
        if (generatedText) {
            this.updateStreamingContent(generatedText);
        }
        
        // Update streaming progress
        if (streamingData && streamingData.progress) {
            const progressPercent = Math.round(streamingData.progress * 100);
            this.updateProgress(progressPercent);
            this.addLog(`⚙️ AI Generation: ${progressPercent}% complete`);
        }
        
        // Update token count and other metrics
        if (streamingData) {
            this.updateStreamingMetrics(streamingData);
        }
        
        // Log streaming progress at reasonable intervals
        if (streamingData && streamingData.progress) {
            const progressValue = streamingData.progress;
            if (progressValue > 0 && (progressValue * 100) % 10 < 1) {
                this.addLog(`✨ Generated ${streamingData.tokens_generated || 0} tokens so far...`);
            }
        }
    }
    
    updateStatus(status, message) {
        const statusBadge = document.getElementById('statusBadge');
        const statusText = document.getElementById('statusText');
        
        if (statusBadge) {
            statusBadge.className = `status-badge status-${status}`;
        }
        
        if (statusText) {
            statusText.textContent = message;
        }
    }
    
    updateProgress(progress) {
        const progressBar = document.getElementById('progressBar');
        const progressPercent = document.getElementById('progressPercent');
        
        if (progressBar) {
            progressBar.style.width = `${progress}%`;
        }
        
        if (progressPercent) {
            progressPercent.textContent = `${progress}%`;
        }
    }
    
    updateProcessingStep(currentStep) {
        document.querySelectorAll('.processing-step').forEach((step, index) => {
            const stepNumber = index + 1;
            const isActive = stepNumber === currentStep;
            const isCompleted = stepNumber < currentStep;
            
            step.classList.toggle('active', isActive);
            step.classList.toggle('completed', isCompleted);
            
            const loader = step.querySelector('.step-loader');
            const icon = step.querySelector('.step-icon');
            
            if (isCompleted) {
                if (loader) loader.style.display = 'none';
                if (icon) icon.textContent = '✅';
            } else if (isActive) {
                if (loader) loader.style.display = 'block';
            } else {
                if (loader) loader.style.display = 'none';
            }
        });
    }
    
    startProcessingAnimation() {
        const processingSteps = document.querySelectorAll('.processing-step');
        processingSteps.forEach((step, index) => {
            setTimeout(() => {
                step.classList.add('fade-in');
            }, index * 200);
        });
    }
    
    startTimeEstimation() {
        let remainingTime = 180; // 3 minutes
        const timeElement = document.getElementById('timeRemaining');
        
        const timer = setInterval(() => {
            if (!this.isProcessing) {
                clearInterval(timer);
                return;
            }
            
            remainingTime -= 1;
            const minutes = Math.floor(remainingTime / 60);
            const seconds = remainingTime % 60;
            
            if (timeElement) {
                timeElement.textContent = `${minutes}:${seconds.toString().padStart(2, '0')}`;
            }
            
            if (remainingTime <= 0) {
                clearInterval(timer);
                if (timeElement) {
                    timeElement.textContent = 'Almost done...';
                }
            }
        }, 1000);
    }
    
    async handleCompletion(status) {
        this.isProcessing = false;
        
        if (this.sseConnection) {
            this.sseConnection.close();
            this.sseConnection = null;
        }
        
        if (status === 'COMPLETED') {
            this.updateStatus('completed', 'Generation completed successfully!');
            await this.fetchAndDisplayResult();
            this.showNotification('Cover letter generated successfully!', 'success');
        } else {
            this.updateStatus('failed', 'Generation failed');
            this.showNotification('Generation failed. Please try again.', 'error');
        }
    }
    
    async fetchAndDisplayResult() {
        try {
            const response = await fetch(`${AppCore.config.API_BASE}/applications/${this.jobId}/artifact`, {
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('jwtToken')}`
                }
            });
            
            if (response.ok) {
                const data = await response.json();
                this.displayResult(data);
            } else {
                throw new Error(`Failed to fetch result: ${response.status}`);
            }
        } catch (error) {
            console.error('Error fetching result:', error);
            this.showNotification('Failed to load generated content', 'error');
        }
    }
    
    displayResult(data) {
        // Show results section
        document.getElementById('resultsSection')?.classList.remove('hidden');
        document.getElementById('progressSection')?.classList.add('hidden');
        
        // Update content
        const outputTextarea = document.getElementById('artifactOutput');
        if (outputTextarea) {
            outputTextarea.value = data.generatedText || '';
            this.updateContentStats(data.generatedText || '');
        }
        
        // Update metadata
        const statsElement = document.getElementById('resultsStats');
        if (statsElement && data.metadata) {
            const { processingTime, wordCount } = data.metadata;
            statsElement.innerHTML = `Generated in <strong>${processingTime || '2.3 seconds'}</strong> • <strong>${wordCount || this.countWords(data.generatedText)} words</strong>`;
        }
        
        // Scroll to results
        document.getElementById('resultsSection')?.scrollIntoView({
            behavior: 'smooth'
        });
        
        // Cache result for offline viewing
        if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
            navigator.serviceWorker.controller.postMessage({
                type: 'CACHE_JOB_RESULT',
                payload: {
                    jobId: this.jobId,
                    content: data.generatedText,
                    metadata: data.metadata
                }
            });
        }
    }
    
    initStreamingDisplay() {
        // Show streaming content area within progress section
        const progressSection = document.getElementById('progressSection');
        if (progressSection && !document.getElementById('streamingContent')) {
            const streamingDiv = document.createElement('div');
            streamingDiv.id = 'streamingContent';
            streamingDiv.innerHTML = `
                <div class="streaming-preview" style="margin-top: 1rem; padding: 1rem; border: 1px solid #e5e7eb; border-radius: 8px; background-color: #f9fafb;">
                    <h4 style="margin: 0 0 0.5rem 0; font-size: 14px; font-weight: 600; color: #374151;">
                        ⚙️ Live AI Generation Preview
                    </h4>
                    <div id="streamingText" class="streaming-text" style="
                        min-height: 120px; 
                        padding: 0.75rem; 
                        background: white; 
                        border: 1px solid #d1d5db; 
                        border-radius: 6px; 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        font-size: 14px;
                        line-height: 1.5;
                        white-space: pre-wrap;
                        word-wrap: break-word;
                        color: #374151;
                    ">Waiting for AI to start generating...</div>
                    <div class="streaming-metrics" style="margin-top: 0.5rem; display: flex; justify-content: space-between; font-size: 12px; color: #6b7280;">
                        <span id="tokenCount">0 tokens</span>
                        <span id="qualityScore">Quality: --</span>
                        <span id="estimatedCost">Cost: $0.00</span>
                    </div>
                </div>
            `;
            progressSection.querySelector('.card-body').appendChild(streamingDiv);
        }
    }
    
    updateStreamingContent(generatedText) {
        const streamingTextDiv = document.getElementById('streamingText');
        if (streamingTextDiv) {
            // Add typing effect by gradually revealing content
            streamingTextDiv.textContent = generatedText;
            
            // Add a cursor effect
            streamingTextDiv.style.borderRight = '2px solid #2563EB';
            
            // Auto-scroll to keep up with new content
            streamingTextDiv.scrollTop = streamingTextDiv.scrollHeight;
            
            // Update status to show we're actively streaming
            this.updateStatus('streaming', 'AI is generating your cover letter...');
        }
    }
    
    updateStreamingMetrics(streamingData) {
        // Update token count
        const tokenCountElement = document.getElementById('tokenCount');
        if (tokenCountElement && streamingData.tokens_generated) {
            tokenCountElement.textContent = `${streamingData.tokens_generated} tokens`;
        }
        
        // Update quality score
        const qualityScoreElement = document.getElementById('qualityScore');
        if (qualityScoreElement && streamingData.quality_score) {
            const percentage = Math.round(streamingData.quality_score * 100);
            qualityScoreElement.textContent = `Quality: ${percentage}%`;
            qualityScoreElement.style.color = percentage > 80 ? '#10b981' : percentage > 60 ? '#f59e0b' : '#ef4444';
        }
        
        // Update estimated cost
        const estimatedCostElement = document.getElementById('estimatedCost');
        if (estimatedCostElement && streamingData.cost_so_far) {
            const cost = parseFloat(streamingData.cost_so_far);
            estimatedCostElement.textContent = `Cost: $${cost.toFixed(4)}`;
        }
    }
    
    updateContentStats(content) {
        const wordCount = this.countWords(content);
        const charCount = content.length;
        
        const wordCountElement = document.getElementById('wordCount');
        const charCountElement = document.getElementById('charCount');
        
        if (wordCountElement) wordCountElement.textContent = `${wordCount} words`;
        if (charCountElement) charCountElement.textContent = `${charCount} characters`;
    }
    
    countWords(text) {
        return text.trim().split(/\s+/).filter(word => word.length > 0).length;
    }
    
    async copyToClipboard() {
        const content = document.getElementById('artifactOutput')?.value;
        if (content) {
            try {
                await navigator.clipboard.writeText(content);
                this.showNotification('Copied to clipboard!', 'success');
                
                // Visual feedback
                const copyBtn = document.getElementById('copyBtn');
                const originalText = copyBtn?.querySelector('.btn-text')?.textContent;
                const btnText = copyBtn?.querySelector('.btn-text');
                const btnIcon = copyBtn?.querySelector('.btn-icon');
                
                if (btnText && btnIcon) {
                    btnIcon.textContent = '✅';
                    btnText.textContent = 'Copied!';
                    
                    setTimeout(() => {
                        btnIcon.textContent = '📋';
                        btnText.textContent = originalText || 'Copy to Clipboard';
                    }, 2000);
                }
                
            } catch (error) {
                console.error('Clipboard error:', error);
                this.showNotification('Failed to copy to clipboard', 'error');
            }
        }
    }
    
    downloadPDF() {
        // This would integrate with a PDF generation service
        this.showNotification('PDF download will be available soon!', 'info');
    }
    
    enableEditing() {
        const textarea = document.getElementById('artifactOutput');
        if (textarea) {
            textarea.readOnly = !textarea.readOnly;
            textarea.focus();
            
            const editBtn = document.getElementById('editBtn');
            const btnText = editBtn?.querySelector('.btn-text');
            const btnIcon = editBtn?.querySelector('.btn-icon');
            
            if (btnText && btnIcon) {
                if (textarea.readOnly) {
                    btnIcon.textContent = '✏️';
                    btnText.textContent = 'Edit Content';
                } else {
                    btnIcon.textContent = '💾';
                    btnText.textContent = 'Save Changes';
                }
            }
        }
    }
    
    regenerateContent() {
        if (confirm('Are you sure you want to regenerate? This will replace the current content.')) {
            // Reset to form section
            document.getElementById('resultsSection')?.classList.add('hidden');
            document.querySelector('.form-section')?.classList.remove('hidden');
            this.scrollToForm();
        }
    }
    
    shareContent() {
        if (navigator.share) {
            navigator.share({
                title: 'My HuskyApply Cover Letter',
                text: document.getElementById('artifactOutput')?.value
            });
        } else {
            // Fallback to clipboard
            this.copyToClipboard();
        }
    }
    
    async saveAsTemplate() {
        const content = document.getElementById('artifactOutput')?.value;
        if (content) {
            try {
                // Load templates module dynamically
                const { ModuleLoader } = window.HuskyApplyCore;
                const templatesModule = await ModuleLoader.loadModule('templates');
                
                // Open template creation dialog with pre-filled content
                templatesModule.createTemplateFromContent(content);
                
            } catch (error) {
                console.error('Error loading templates module:', error);
                this.showNotification('Template saving will be available soon!', 'info');
            }
        }
    }
    
    scrollToForm() {
        document.getElementById('application-form')?.scrollIntoView({
            behavior: 'smooth'
        });
    }
    
    reconnectSSE() {
        if (this.sseConnection) {
            this.sseConnection.close();
        }
        
        setTimeout(() => {
            if (this.isProcessing && this.jobId) {
                this.startStatusUpdates(this.jobId);
            }
        }, 1000);
    }
    
    addLog(message) {
        const logOutput = document.getElementById('logOutput');
        if (logOutput) {
            const timestamp = new Date().toLocaleTimeString();
            const logEntry = `[${timestamp}] ${message}\n`;
            logOutput.textContent += logEntry;
            logOutput.scrollTop = logOutput.scrollHeight;
        }
    }
    
    showNotification(message, type = 'info') {
        // Load notifications module dynamically if needed
        if (window.HuskyApplyCore && window.HuskyApplyCore.ModuleLoader) {
            window.HuskyApplyCore.ModuleLoader.loadModule('notifications').then(notificationModule => {
                notificationModule.showToast(message, type);
            });
        } else {
            // Fallback simple notification
            const notification = document.createElement('div');
            notification.className = `notification ${type}`;
            notification.textContent = message;
            document.body.appendChild(notification);
            
            setTimeout(() => {
                notification.remove();
            }, 5000);
        }
    }
}