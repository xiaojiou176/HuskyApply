/**
 * UI Enhancements Module
 * Provides modern micro-interactions, animations, and enhanced user experience
 */

import { AppCore } from '../app-core.js';

export default class UIEnhancementsModule {
    constructor() {
        this.initialized = false;
        this.notificationContainer = null;
        this.observerAnimations = null;
        this.streamingCursor = null;
        this.fabVisible = false;
        this.currentTheme = localStorage.getItem('huskyapply-theme') || 'light';
        this.magneticElements = new Map();
        this.parallaxElements = [];
        this.counters = new Map();
        this.tabs = new Map();
        this.accordions = new Map();
    }
    
    async init() {
        if (this.initialized) return;
        
        console.log('🎨 Initializing UI Enhancements Module...');
        
        // Setup notification system
        this.setupNotificationSystem();
        
        // Setup intersection observer for animations
        this.setupScrollAnimations();
        
        // Setup enhanced interactions
        this.setupEnhancedInteractions();
        
        // Setup floating action button
        this.setupFloatingActionButton();
        
        // Setup form enhancements
        this.setupFormEnhancements();
        
        // Setup streaming UI enhancements
        this.setupStreamingEnhancements();
        
        // Setup theme system
        this.setupThemeSystem();
        
        // Setup advanced interactions
        this.setupMagneticElements();
        this.setupParallaxElements();
        this.setupTabSystem();
        this.setupAccordionSystem();
        
        // Setup animated counters
        this.setupAnimatedCounters();
        
        // Setup advanced tooltips
        this.setupTooltipSystem();
        
        this.initialized = true;
        console.log('✅ UI Enhancements Module Ready');
    }
    
    setupNotificationSystem() {
        // Create notification container
        this.notificationContainer = document.createElement('div');
        this.notificationContainer.className = 'notification-container';
        document.body.appendChild(this.notificationContainer);
        
        // Auto-hide notifications after 5 seconds
        this.notificationTimer = new Map();
    }
    
    showNotification(message, type = 'info', duration = 5000) {
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        
        const icon = this.getNotificationIcon(type);
        notification.innerHTML = `
            <div style="display: flex; align-items: center; gap: 0.75rem;">
                <span style="font-size: 1.25rem;">${icon}</span>
                <div>
                    <div style="font-weight: 600; margin-bottom: 0.25rem;">${this.getNotificationTitle(type)}</div>
                    <div style="color: var(--gray-600); font-size: 0.875rem;">${message}</div>
                </div>
                <button class="notification-close" style="
                    background: none; 
                    border: none; 
                    font-size: 1.5rem; 
                    cursor: pointer; 
                    opacity: 0.5;
                    transition: opacity 0.2s;
                " onclick="this.parentElement.parentElement.remove()">×</button>
            </div>
        `;
        
        // Add hover effects to close button
        const closeBtn = notification.querySelector('.notification-close');
        closeBtn.addEventListener('mouseenter', () => closeBtn.style.opacity = '1');
        closeBtn.addEventListener('mouseleave', () => closeBtn.style.opacity = '0.5');
        
        this.notificationContainer.appendChild(notification);
        
        // Auto-remove after duration
        if (duration > 0) {
            const timer = setTimeout(() => {
                this.removeNotification(notification);
            }, duration);
            this.notificationTimer.set(notification, timer);
        }
        
        // Remove on click
        notification.addEventListener('click', () => {
            this.removeNotification(notification);
        });
        
        return notification;
    }
    
    removeNotification(notification) {
        if (this.notificationTimer.has(notification)) {
            clearTimeout(this.notificationTimer.get(notification));
            this.notificationTimer.delete(notification);
        }
        
        notification.style.animation = 'slideInNotification 0.3s ease-out reverse';
        setTimeout(() => {
            notification.remove();
        }, 300);
    }
    
    getNotificationIcon(type) {
        const icons = {
            success: '✅',
            error: '❌',
            warning: '⚠️',
            info: 'ℹ️'
        };
        return icons[type] || icons.info;
    }
    
    getNotificationTitle(type) {
        const titles = {
            success: 'Success',
            error: 'Error',
            warning: 'Warning',
            info: 'Information'
        };
        return titles[type] || titles.info;
    }
    
    setupScrollAnimations() {
        // Use Intersection Observer for performant scroll animations
        this.observerAnimations = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('animate-fade-in');
                    this.observerAnimations.unobserve(entry.target);
                }
            });
        }, {
            threshold: 0.1,
            rootMargin: '0px 0px -50px 0px'
        });
        
        // Observe elements that should animate on scroll
        const animatedElements = document.querySelectorAll('.card, .form-group, h1, h2, h3');
        animatedElements.forEach(el => {
            this.observerAnimations.observe(el);
        });
    }
    
    setupEnhancedInteractions() {
        // Add enhanced button effects
        document.addEventListener('click', (e) => {
            if (e.target.matches('.btn')) {
                this.createRippleEffect(e);
            }
        });
        
        // Add card hover effects
        const cards = document.querySelectorAll('.card');
        cards.forEach(card => {
            if (!card.classList.contains('card-interactive')) {
                card.classList.add('card-interactive');
            }
        });
        
        // Add smooth focus transitions
        const focusableElements = document.querySelectorAll('input, textarea, select, button, [tabindex]');
        focusableElements.forEach(el => {
            el.addEventListener('focus', this.handleFocusIn.bind(this));
            el.addEventListener('blur', this.handleFocusOut.bind(this));
        });
    }
    
    createRippleEffect(event) {
        const button = event.target;
        const rect = button.getBoundingClientRect();
        const ripple = document.createElement('div');
        
        const size = Math.max(rect.width, rect.height);
        const x = event.clientX - rect.left - size / 2;
        const y = event.clientY - rect.top - size / 2;
        
        ripple.style.cssText = `
            position: absolute;
            width: ${size}px;
            height: ${size}px;
            left: ${x}px;
            top: ${y}px;
            background: rgba(255, 255, 255, 0.3);
            border-radius: 50%;
            pointer-events: none;
            transform: scale(0);
            animation: ripple 0.6s ease-out;
            z-index: 1;
        `;
        
        // Add ripple keyframes if not exists
        if (!document.getElementById('ripple-styles')) {
            const style = document.createElement('style');
            style.id = 'ripple-styles';
            style.textContent = `
                @keyframes ripple {
                    to {
                        transform: scale(2);
                        opacity: 0;
                    }
                }
            `;
            document.head.appendChild(style);
        }
        
        button.style.position = 'relative';
        button.style.overflow = 'hidden';
        button.appendChild(ripple);
        
        setTimeout(() => {
            ripple.remove();
        }, 600);
    }
    
    handleFocusIn(event) {
        event.target.classList.add('focus-enhanced');
        event.target.style.transform = 'scale(1.02)';
        event.target.style.transition = 'transform 0.2s ease';
    }
    
    handleFocusOut(event) {
        event.target.classList.remove('focus-enhanced');
        event.target.style.transform = 'scale(1)';
    }
    
    setupFloatingActionButton() {
        const fab = document.createElement('button');
        fab.className = 'fab';
        fab.innerHTML = '💬';
        fab.title = 'Help & Support';
        fab.style.display = 'none';
        
        fab.addEventListener('click', () => {
            this.showHelpDialog();
        });
        
        document.body.appendChild(fab);
        
        // Show FAB when scrolled down
        let lastScrollY = 0;
        window.addEventListener('scroll', () => {
            const scrollY = window.scrollY;
            
            if (scrollY > 300 && !this.fabVisible) {
                fab.style.display = 'flex';
                fab.style.animation = 'fadeInScale 0.3s ease-out';
                this.fabVisible = true;
            } else if (scrollY <= 300 && this.fabVisible) {
                fab.style.animation = 'fadeInScale 0.3s ease-out reverse';
                setTimeout(() => {
                    fab.style.display = 'none';
                }, 300);
                this.fabVisible = false;
            }
            
            lastScrollY = scrollY;
        });
        
        this.fab = fab;
    }
    
    showHelpDialog() {
        const dialog = document.createElement('div');
        dialog.className = 'help-dialog';
        dialog.innerHTML = `
            <div class="help-dialog-backdrop" style="
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: rgba(0, 0, 0, 0.5);
                z-index: 2000;
                display: flex;
                align-items: center;
                justify-content: center;
                animation: fadeIn 0.3s ease-out;
            ">
                <div class="help-dialog-content" style="
                    background: white;
                    border-radius: 12px;
                    padding: 2rem;
                    max-width: 500px;
                    max-height: 80vh;
                    overflow-y: auto;
                    margin: 1rem;
                    animation: fadeInScale 0.3s ease-out;
                    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
                ">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem;">
                        <h3 style="margin: 0; color: var(--gray-800);">🐺 HuskyApply Help</h3>
                        <button class="close-dialog" style="
                            background: none;
                            border: none;
                            font-size: 1.5rem;
                            cursor: pointer;
                            color: var(--gray-500);
                            transition: color 0.2s;
                        ">×</button>
                    </div>
                    
                    <div style="space-y: 1rem;">
                        <div class="help-section">
                            <h4 style="color: var(--primary); margin-bottom: 0.5rem;">📝 How to Generate Cover Letters</h4>
                            <ul style="color: var(--gray-600); line-height: 1.6; margin: 0; padding-left: 1.5rem;">
                                <li>Paste the job posting URL in the first field</li>
                                <li>Upload your resume (PDF, DOC, DOCX)</li>
                                <li>Select your preferred AI model</li>
                                <li>Click "Generate Cover Letter"</li>
                            </ul>
                        </div>
                        
                        <div class="help-section" style="margin-top: 1.5rem;">
                            <h4 style="color: var(--primary); margin-bottom: 0.5rem;">⚙️ AI Model Recommendations</h4>
                            <ul style="color: var(--gray-600); line-height: 1.6; margin: 0; padding-left: 1.5rem;">
                                <li><strong>GPT-4o:</strong> Best quality, slightly more expensive</li>
                                <li><strong>GPT-4 Turbo:</strong> Great balance of quality and speed</li>
                                <li><strong>Claude 3.5 Sonnet:</strong> Excellent for creative writing</li>
                                <li><strong>GPT-3.5 Turbo:</strong> Fastest and most cost-effective</li>
                            </ul>
                        </div>
                        
                        <div class="help-section" style="margin-top: 1.5rem;">
                            <h4 style="color: var(--primary); margin-bottom: 0.5rem;">⚡ Features</h4>
                            <ul style="color: var(--gray-600); line-height: 1.6; margin: 0; padding-left: 1.5rem;">
                                <li>Real-time streaming generation</li>
                                <li>Live quality and cost tracking</li>
                                <li>Copy, edit, and download results</li>
                                <li>Smart caching for faster responses</li>
                            </ul>
                        </div>
                        
                        <div style="margin-top: 2rem; padding-top: 1.5rem; border-top: 1px solid var(--gray-200);">
                            <p style="color: var(--gray-600); text-align: center; margin: 0;">
                                Need more help? <a href="https://github.com/xiaojiou176/HuskyApply/issues" style="color: var(--primary);">Create a GitHub Issue</a>
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        document.body.appendChild(dialog);
        
        // Close dialog handlers
        const closeBtn = dialog.querySelector('.close-dialog');
        const backdrop = dialog.querySelector('.help-dialog-backdrop');
        
        closeBtn.addEventListener('click', () => this.closeHelpDialog(dialog));
        backdrop.addEventListener('click', (e) => {
            if (e.target === backdrop) {
                this.closeHelpDialog(dialog);
            }
        });
        
        // Close on Escape key
        const escapeHandler = (e) => {
            if (e.key === 'Escape') {
                this.closeHelpDialog(dialog);
                document.removeEventListener('keydown', escapeHandler);
            }
        };
        document.addEventListener('keydown', escapeHandler);
        
        // Add fade-in styles
        if (!document.getElementById('dialog-styles')) {
            const style = document.createElement('style');
            style.id = 'dialog-styles';
            style.textContent = `
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
            `;
            document.head.appendChild(style);
        }
    }
    
    closeHelpDialog(dialog) {
        const backdrop = dialog.querySelector('.help-dialog-backdrop');
        backdrop.style.animation = 'fadeIn 0.3s ease-out reverse';
        setTimeout(() => {
            dialog.remove();
        }, 300);
    }
    
    setupFormEnhancements() {
        // Convert regular inputs to floating labels
        const formGroups = document.querySelectorAll('.form-group');
        formGroups.forEach(group => {
            const input = group.querySelector('input, textarea, select');
            const label = group.querySelector('.form-label');
            
            if (input && label && !group.classList.contains('floating-label')) {
                this.convertToFloatingLabel(group, input, label);
            }
        });
        
        // Enhanced file upload
        this.setupEnhancedFileUpload();
        
        // Form validation enhancements
        this.setupFormValidation();
    }
    
    convertToFloatingLabel(group, input, label) {
        group.classList.add('floating-label');
        
        // Add placeholder for floating label to work
        if (!input.placeholder) {
            input.placeholder = ' ';
        }
        
        // Position label after input for CSS to work
        input.parentNode.insertBefore(label, input.nextSibling);
    }
    
    setupEnhancedFileUpload() {
        const fileInputs = document.querySelectorAll('input[type="file"]');
        fileInputs.forEach(input => {
            const container = input.closest('.file-input');
            if (container) {
                this.enhanceFileInput(input, container);
            }
        });
    }
    
    enhanceFileInput(input, container) {
        const label = container.querySelector('.file-input-label');
        if (!label) return;
        
        // Add drag and drop functionality
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            container.addEventListener(eventName, this.preventDefaults);
        });
        
        ['dragenter', 'dragover'].forEach(eventName => {
            container.addEventListener(eventName, () => {
                label.classList.add('drag-over');
            });
        });
        
        ['dragleave', 'drop'].forEach(eventName => {
            container.addEventListener(eventName, () => {
                label.classList.remove('drag-over');
            });
        });
        
        container.addEventListener('drop', (e) => {
            const files = e.dataTransfer.files;
            input.files = files;
            this.updateFileInputLabel(input, label, files);
        });
        
        input.addEventListener('change', (e) => {
            this.updateFileInputLabel(input, label, e.target.files);
        });
    }
    
    preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }
    
    updateFileInputLabel(input, label, files) {
        if (files.length > 0) {
            const file = files[0];
            const size = (file.size / 1024 / 1024).toFixed(1);
            label.innerHTML = `
                <span>📄</span>
                <div>
                    <div style="font-weight: 600;">${file.name}</div>
                    <div style="font-size: 0.875rem; color: var(--gray-500);">${size} MB</div>
                </div>
            `;
        } else {
            label.innerHTML = `
                <span>📄</span>
                <span>Drop your resume here or click to browse</span>
            `;
        }
    }
    
    setupFormValidation() {
        const forms = document.querySelectorAll('form');
        forms.forEach(form => {
            form.addEventListener('submit', (e) => {
                if (!this.validateForm(form)) {
                    e.preventDefault();
                    this.showNotification('Please fix the errors in the form', 'error');
                }
            });
        });
    }
    
    validateForm(form) {
        let isValid = true;
        const inputs = form.querySelectorAll('input, textarea, select');
        
        inputs.forEach(input => {
            if (input.required && !input.value.trim()) {
                this.showFieldError(input, 'This field is required');
                isValid = false;
            } else if (input.type === 'email' && input.value && !this.isValidEmail(input.value)) {
                this.showFieldError(input, 'Please enter a valid email address');
                isValid = false;
            } else if (input.type === 'url' && input.value && !this.isValidUrl(input.value)) {
                this.showFieldError(input, 'Please enter a valid URL');
                isValid = false;
            } else {
                this.clearFieldError(input);
            }
        });
        
        return isValid;
    }
    
    showFieldError(input, message) {
        input.classList.add('error');
        
        let errorElement = input.parentNode.querySelector('.field-error');
        if (!errorElement) {
            errorElement = document.createElement('div');
            errorElement.className = 'field-error';
            errorElement.style.cssText = `
                color: var(--error);
                font-size: 0.875rem;
                margin-top: 0.25rem;
            `;
            input.parentNode.appendChild(errorElement);
        }
        
        errorElement.textContent = message;
    }
    
    clearFieldError(input) {
        input.classList.remove('error');
        const errorElement = input.parentNode.querySelector('.field-error');
        if (errorElement) {
            errorElement.remove();
        }
    }
    
    isValidEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }
    
    isValidUrl(url) {
        try {
            new URL(url);
            return true;
        } catch {
            return false;
        }
    }
    
    setupStreamingEnhancements() {
        // Setup streaming cursor
        this.streamingCursor = document.createElement('span');
        this.streamingCursor.className = 'streaming-cursor';
        this.streamingCursor.textContent = '|';
    }
    
    // Public methods for other modules to use
    addStreamingCursor(element) {
        if (this.streamingCursor && !element.querySelector('.streaming-cursor')) {
            element.appendChild(this.streamingCursor.cloneNode(true));
        }
    }
    
    removeStreamingCursor(element) {
        const cursor = element.querySelector('.streaming-cursor');
        if (cursor) {
            cursor.remove();
        }
    }
    
    updateProgress(progressElement, percentage) {
        if (progressElement && progressElement.classList.contains('progress-ring-fill')) {
            const circumference = 2 * Math.PI * 45; // radius = 45
            const offset = circumference - (percentage / 100) * circumference;
            progressElement.style.strokeDasharray = `${circumference - offset} ${circumference}`;
        }
    }
    
    showTypingIndicator(container) {
        const indicator = document.createElement('div');
        indicator.className = 'typing-indicator';
        indicator.innerHTML = '⏳ Processing request';
        container.appendChild(indicator);
        return indicator;
    }
    
    hideTypingIndicator(indicator) {
        if (indicator && indicator.parentNode) {
            indicator.style.animation = 'fadeInUp 0.3s ease-out reverse';
            setTimeout(() => {
                indicator.remove();
            }, 300);
        }
    }
    
    // Cleanup method
    destroy() {
        if (this.observerAnimations) {
            this.observerAnimations.disconnect();
        }
        
        if (this.notificationContainer) {
            this.notificationContainer.remove();
        }
        
        if (this.fab) {
            this.fab.remove();
        }
        
        // Clear timers
        this.notificationTimer.forEach(timer => clearTimeout(timer));
        this.notificationTimer.clear();
        
        this.initialized = false;
    }
    
    // ======================================
    // ADVANCED UI FEATURES
    // ======================================
    
    setupThemeSystem() {
        // Apply saved theme
        document.documentElement.setAttribute('data-theme', this.currentTheme);
        
        // Create theme toggle button
        const themeToggle = document.createElement('button');
        themeToggle.className = 'theme-toggle';
        themeToggle.innerHTML = `
            <span class="icon-sun">☀️</span>
            <span class="icon-moon">🌙</span>
        `;
        themeToggle.title = 'Toggle theme';
        themeToggle.setAttribute('aria-label', 'Toggle between light and dark theme');
        
        themeToggle.addEventListener('click', () => {
            this.toggleTheme();
        });
        
        document.body.appendChild(themeToggle);
        this.themeToggle = themeToggle;
        
        // Listen for system theme changes
        const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        mediaQuery.addEventListener('change', (e) => {
            if (!localStorage.getItem('huskyapply-theme')) {
                this.setTheme(e.matches ? 'dark' : 'light');
            }
        });
    }
    
    toggleTheme() {
        const newTheme = this.currentTheme === 'light' ? 'dark' : 'light';
        this.setTheme(newTheme);
        
        // Show theme change notification
        this.showNotification(
            `Switched to ${newTheme} theme`, 
            'success', 
            2000
        );
    }
    
    setTheme(theme) {
        this.currentTheme = theme;
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('huskyapply-theme', theme);
        
        // Animate theme transition
        document.documentElement.style.transition = 'background-color 0.3s ease, color 0.3s ease';
        setTimeout(() => {
            document.documentElement.style.transition = '';
        }, 300);
    }
    
    setupMagneticElements() {
        const magneticSelectors = ['.magnetic-button', '.fab', '.card-interactive'];
        magneticSelectors.forEach(selector => {
            document.querySelectorAll(selector).forEach(element => {
                this.addMagneticEffect(element);
            });
        });
    }
    
    addMagneticEffect(element) {
        const strength = 0.3;
        const bounds = element.getBoundingClientRect();
        const centerX = bounds.left + bounds.width / 2;
        const centerY = bounds.top + bounds.height / 2;
        
        const mouseMoveHandler = (e) => {
            if (!element.matches(':hover')) return;
            
            const deltaX = (e.clientX - centerX) * strength;
            const deltaY = (e.clientY - centerY) * strength;
            
            element.style.transform = `translate(${deltaX}px, ${deltaY}px) scale(1.02)`;
        };
        
        const mouseLeaveHandler = () => {
            element.style.transform = '';
        };
        
        element.addEventListener('mousemove', mouseMoveHandler);
        element.addEventListener('mouseleave', mouseLeaveHandler);
        
        this.magneticElements.set(element, { mouseMoveHandler, mouseLeaveHandler });
    }
    
    setupParallaxElements() {
        const parallaxElements = document.querySelectorAll('.parallax-element');
        
        parallaxElements.forEach(element => {
            const speed = parseFloat(element.dataset.speed) || 0.5;
            this.parallaxElements.push({ element, speed });
        });
        
        if (this.parallaxElements.length > 0) {
            window.addEventListener('scroll', this.handleParallax.bind(this), { passive: true });
        }
    }
    
    handleParallax() {
        const scrollY = window.pageYOffset;
        
        this.parallaxElements.forEach(({ element, speed }) => {
            const yPos = scrollY * speed;
            element.style.transform = `translateY(${yPos}px)`;
        });
    }
    
    setupTabSystem() {
        const tabContainers = document.querySelectorAll('.tabs-container');
        
        tabContainers.forEach(container => {
            const tabs = container.querySelectorAll('.tab-item');
            const indicator = container.querySelector('.tab-indicator');
            
            if (!indicator) {
                const newIndicator = document.createElement('div');
                newIndicator.className = 'tab-indicator';
                container.appendChild(newIndicator);
            }
            
            const tabSystem = {
                container,
                tabs: Array.from(tabs),
                indicator: container.querySelector('.tab-indicator'),
                activeIndex: 0
            };
            
            tabs.forEach((tab, index) => {
                tab.addEventListener('click', () => {
                    this.activateTab(tabSystem, index);
                });
            });
            
            // Initialize first tab
            this.activateTab(tabSystem, 0);
            this.tabs.set(container, tabSystem);
        });
    }
    
    activateTab(tabSystem, index) {
        const { tabs, indicator } = tabSystem;
        const targetTab = tabs[index];
        
        // Update active states
        tabs.forEach(tab => tab.classList.remove('active'));
        targetTab.classList.add('active');
        
        // Move indicator
        const tabRect = targetTab.getBoundingClientRect();
        const containerRect = tabSystem.container.getBoundingClientRect();
        
        indicator.style.width = `${tabRect.width}px`;
        indicator.style.height = `${tabRect.height}px`;
        indicator.style.left = `${tabRect.left - containerRect.left}px`;
        indicator.style.top = `${tabRect.top - containerRect.top}px`;
        
        tabSystem.activeIndex = index;
        
        // Dispatch custom event
        tabSystem.container.dispatchEvent(new CustomEvent('tabChanged', {
            detail: { index, tab: targetTab }
        }));
    }
    
    setupAccordionSystem() {
        const accordionItems = document.querySelectorAll('.accordion-item');
        
        accordionItems.forEach(item => {
            const header = item.querySelector('.accordion-header');
            const content = item.querySelector('.accordion-content');
            const icon = item.querySelector('.accordion-icon');
            
            if (!header || !content) return;
            
            const accordionData = {
                item,
                header,
                content,
                icon,
                isOpen: item.classList.contains('active')
            };
            
            header.addEventListener('click', () => {
                this.toggleAccordion(accordionData);
            });
            
            this.accordions.set(item, accordionData);
        });
    }
    
    toggleAccordion(accordionData) {
        const { item, content, isOpen } = accordionData;
        
        if (isOpen) {
            // Close
            item.classList.remove('active');
            content.style.maxHeight = '0px';
            accordionData.isOpen = false;
        } else {
            // Open
            item.classList.add('active');
            content.style.maxHeight = `${content.scrollHeight}px`;
            accordionData.isOpen = true;
        }
        
        // Dispatch event
        item.dispatchEvent(new CustomEvent('accordionToggled', {
            detail: { isOpen: accordionData.isOpen }
        }));
    }
    
    setupAnimatedCounters() {
        const counterElements = document.querySelectorAll('.animated-counter');
        
        const counterObserver = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting && !this.counters.has(entry.target)) {
                    this.animateCounter(entry.target);
                }
            });
        }, { threshold: 0.5 });
        
        counterElements.forEach(element => {
            counterObserver.observe(element);
        });
    }
    
    animateCounter(element) {
        const target = parseInt(element.dataset.target) || parseInt(element.textContent) || 0;
        const duration = parseInt(element.dataset.duration) || 2000;
        const startTime = performance.now();
        
        const animate = (currentTime) => {
            const elapsed = currentTime - startTime;
            const progress = Math.min(elapsed / duration, 1);
            
            // Ease-out animation
            const easeOut = 1 - Math.pow(1 - progress, 3);
            const currentValue = Math.floor(target * easeOut);
            
            element.textContent = currentValue.toLocaleString();
            
            if (progress < 1) {
                requestAnimationFrame(animate);
            }
        };
        
        requestAnimationFrame(animate);
        this.counters.set(element, true);
    }
    
    setupTooltipSystem() {
        const tooltipTriggers = document.querySelectorAll('.tooltip-trigger');
        
        tooltipTriggers.forEach(trigger => {
            const tooltip = trigger.querySelector('.tooltip');
            if (!tooltip) return;
            
            let showTimeout, hideTimeout;
            
            trigger.addEventListener('mouseenter', () => {
                clearTimeout(hideTimeout);
                showTimeout = setTimeout(() => {
                    tooltip.style.opacity = '1';
                    tooltip.style.visibility = 'visible';
                    tooltip.style.transform = 'translateX(-50%) translateY(-4px)';
                }, 300);
            });
            
            trigger.addEventListener('mouseleave', () => {
                clearTimeout(showTimeout);
                hideTimeout = setTimeout(() => {
                    tooltip.style.opacity = '0';
                    tooltip.style.visibility = 'hidden';
                    tooltip.style.transform = 'translateX(-50%) translateY(0)';
                }, 100);
            });
        });
    }
    
    // ======================================
    // ENHANCED PUBLIC METHODS
    // ======================================
    
    createInteractiveButton(text, options = {}) {
        const button = document.createElement('button');
        button.className = `btn btn-enhanced ${options.variant || 'primary'} ${options.magnetic ? 'magnetic-button' : ''}`;
        button.innerHTML = text;
        
        if (options.ripple !== false) {
            button.classList.add('ripple-effect');
        }
        
        if (options.magnetic) {
            this.addMagneticEffect(button);
        }
        
        return button;
    }
    
    createInteractiveCard(content, options = {}) {
        const card = document.createElement('div');
        card.className = `card card-interactive ${options.lift ? 'hover-lift' : ''} ${options.tilt ? 'hover-tilt' : ''}`;
        card.innerHTML = content;
        
        return card;
    }
    
    createProgressRing(percentage, options = {}) {
        const container = document.createElement('div');
        container.className = 'progress-ring';
        container.innerHTML = `
            <svg>
                <circle class="progress-ring-bg" cx="60" cy="60" r="45"></circle>
                <circle class="progress-ring-fill" cx="60" cy="60" r="45"></circle>
            </svg>
            <div class="progress-percentage">${percentage}%</div>
        `;
        
        this.updateProgress(container.querySelector('.progress-ring-fill'), percentage);
        return container;
    }
    
    createLikeButton(isLiked = false) {
        const button = document.createElement('button');
        button.className = `like-button ${isLiked ? 'liked' : ''}`;
        button.innerHTML = isLiked ? '❤️' : '🤍';
        button.setAttribute('aria-label', isLiked ? 'Unlike' : 'Like');
        
        button.addEventListener('click', () => {
            const currentlyLiked = button.classList.contains('liked');
            button.classList.toggle('liked', !currentlyLiked);
            button.innerHTML = currentlyLiked ? '🤍' : '❤️';
            button.setAttribute('aria-label', currentlyLiked ? 'Like' : 'Unlike');
            
            // Dispatch custom event
            button.dispatchEvent(new CustomEvent('likeToggled', {
                detail: { liked: !currentlyLiked }
            }));
        });
        
        return button;
    }
    
    createRatingSystem(maxRating = 5, currentRating = 0) {
        const container = document.createElement('div');
        container.className = 'rating-container';
        
        for (let i = 1; i <= maxRating; i++) {
            const star = document.createElement('span');
            star.className = `rating-star ${i <= currentRating ? 'active' : ''}`;
            star.innerHTML = '⭐';
            star.setAttribute('data-rating', i);
            
            star.addEventListener('click', () => {
                this.setRating(container, i);
            });
            
            container.appendChild(star);
        }
        
        return container;
    }
    
    setRating(container, rating) {
        const stars = container.querySelectorAll('.rating-star');
        
        stars.forEach((star, index) => {
            star.classList.toggle('active', index < rating);
        });
        
        // Dispatch custom event
        container.dispatchEvent(new CustomEvent('ratingChanged', {
            detail: { rating }
        }));
    }
    
    showMorphingLoader(container) {
        const loader = document.createElement('div');
        loader.className = 'morphing-loader';
        container.appendChild(loader);
        return loader;
    }
    
    // Enhanced notification with action buttons
    showActionNotification(message, type = 'info', actions = [], duration = 0) {
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        
        const icon = this.getNotificationIcon(type);
        notification.innerHTML = `
            <div style="display: flex; align-items: flex-start; gap: 0.75rem;">
                <span style="font-size: 1.25rem; flex-shrink: 0;">${icon}</span>
                <div style="flex: 1;">
                    <div style="font-weight: 600; margin-bottom: 0.25rem;">${this.getNotificationTitle(type)}</div>
                    <div style="color: var(--text-secondary); font-size: 0.875rem; margin-bottom: ${actions.length > 0 ? '1rem' : '0'};">${message}</div>
                    ${actions.length > 0 ? `
                        <div style="display: flex; gap: 0.5rem; margin-top: 0.75rem;">
                            ${actions.map(action => `
                                <button class="notification-action" data-action="${action.id}" style="
                                    background: ${action.primary ? 'var(--primary)' : 'transparent'};
                                    color: ${action.primary ? 'white' : 'var(--primary)'};
                                    border: 1px solid var(--primary);
                                    padding: 0.5rem 1rem;
                                    border-radius: var(--radius);
                                    font-size: 0.875rem;
                                    cursor: pointer;
                                    transition: all 0.2s;
                                ">${action.text}</button>
                            `).join('')}
                        </div>
                    ` : ''}
                </div>
                <button class="notification-close" style="
                    background: none; 
                    border: none; 
                    font-size: 1.5rem; 
                    cursor: pointer; 
                    opacity: 0.5;
                    transition: opacity 0.2s;
                    flex-shrink: 0;
                ">×</button>
            </div>
        `;
        
        // Handle action clicks
        notification.querySelectorAll('.notification-action').forEach(button => {
            button.addEventListener('click', (e) => {
                const actionId = e.target.dataset.action;
                const action = actions.find(a => a.id === actionId);
                if (action && action.handler) {
                    action.handler();
                }
                this.removeNotification(notification);
            });
            
            // Add hover effects
            button.addEventListener('mouseenter', (e) => {
                if (!e.target.dataset.primary) {
                    e.target.style.background = 'var(--primary-light)';
                    e.target.style.color = 'white';
                }
            });
            
            button.addEventListener('mouseleave', (e) => {
                if (!e.target.dataset.primary) {
                    e.target.style.background = 'transparent';
                    e.target.style.color = 'var(--primary)';
                }
            });
        });
        
        // Handle close button
        const closeBtn = notification.querySelector('.notification-close');
        closeBtn.addEventListener('click', () => this.removeNotification(notification));
        closeBtn.addEventListener('mouseenter', () => closeBtn.style.opacity = '1');
        closeBtn.addEventListener('mouseleave', () => closeBtn.style.opacity = '0.5');
        
        this.notificationContainer.appendChild(notification);
        
        // Auto-remove after duration (if specified)
        if (duration > 0) {
            const timer = setTimeout(() => {
                this.removeNotification(notification);
            }, duration);
            this.notificationTimer.set(notification, timer);
        }
        
        return notification;
    }
    
    // Clean up all advanced features
    destroyAdvancedFeatures() {
        // Remove magnetic effects
        this.magneticElements.forEach((handlers, element) => {
            element.removeEventListener('mousemove', handlers.mouseMoveHandler);
            element.removeEventListener('mouseleave', handlers.mouseLeaveHandler);
        });
        this.magneticElements.clear();
        
        // Remove parallax scroll listener
        if (this.parallaxElements.length > 0) {
            window.removeEventListener('scroll', this.handleParallax.bind(this));
        }
        this.parallaxElements = [];
        
        // Remove theme toggle
        if (this.themeToggle) {
            this.themeToggle.remove();
        }
        
        // Clear maps
        this.counters.clear();
        this.tabs.clear();
        this.accordions.clear();
    }
}