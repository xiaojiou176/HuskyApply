/**
 * Mobile Optimizations Module - Enhanced Mobile User Experience
 * 
 * Provides comprehensive mobile optimizations including:
 * - Touch-friendly interactions and gestures
 * - Responsive layout adjustments
 * - Mobile-specific UI patterns
 * - Performance optimizations for mobile devices
 * - PWA-like features for better mobile experience
 */

class MobileOptimizationsModule {
    constructor() {
        this.core = window.HuskyApply;
        this.initialized = false;
        this.isMobile = this.detectMobile();
        this.isTouch = this.detectTouch();
        this.orientation = this.getOrientation();
        this.viewportHeight = window.innerHeight;
        this.gestureHandler = null;
        this.touchStartY = 0;
        this.touchStartX = 0;
        this.lastTap = 0;
    }

    async init() {
        if (this.initialized) return;
        
        console.log('Initializing Mobile Optimizations module...');
        
        if (this.isMobile || this.isTouch) {
            this.applyMobileOptimizations();
            this.setupTouchInteractions();
            this.setupGestureHandling();
            this.optimizeViewport();
            this.setupMobileNavigation();
            this.enhanceFormForMobile();
            this.setupPWAFeatures();
        }
        
        this.setupOrientationHandling();
        this.setupMobilePerformance();
        
        this.initialized = true;
        console.log('Mobile Optimizations module initialized');
    }

    detectMobile() {
        const userAgent = navigator.userAgent.toLowerCase();
        const mobileKeywords = [
            'android', 'webos', 'iphone', 'ipad', 'ipod', 'blackberry', 
            'iemobile', 'opera mini', 'mobile', 'tablet'
        ];
        
        return mobileKeywords.some(keyword => userAgent.includes(keyword)) ||
               window.innerWidth <= 768 ||
               ('ontouchstart' in window);
    }

    detectTouch() {
        return 'ontouchstart' in window || 
               navigator.maxTouchPoints > 0 ||
               navigator.msMaxTouchPoints > 0;
    }

    getOrientation() {
        if (screen.orientation) {
            return screen.orientation.angle;
        }
        return window.orientation || 0;
    }

    applyMobileOptimizations() {
        document.body.classList.add('mobile-optimized');
        
        // Apply mobile-specific CSS variables
        document.documentElement.style.setProperty('--mobile-padding', '1rem');
        document.documentElement.style.setProperty('--mobile-font-size', '16px');
        document.documentElement.style.setProperty('--touch-target-size', '44px');
        
        // Optimize touch targets
        this.optimizeTouchTargets();
        
        // Improve scroll behavior
        this.optimizeScrolling();
        
        // Enhance visual feedback
        this.enhanceVisualFeedback();
    }

    optimizeTouchTargets() {
        const interactiveElements = document.querySelectorAll(
            'button, .btn, a, input, select, textarea, [role="button"], [tabindex]'
        );
        
        interactiveElements.forEach(element => {
            const rect = element.getBoundingClientRect();
            const minSize = 44; // iOS/Android recommendation
            
            if (rect.width < minSize || rect.height < minSize) {
                element.style.minHeight = `${minSize}px`;
                element.style.minWidth = `${minSize}px`;
                element.style.display = element.style.display || 'inline-flex';
                element.style.alignItems = 'center';
                element.style.justifyContent = 'center';
            }
            
            // Add touch-friendly padding
            if (!element.style.padding) {
                element.style.padding = '12px 16px';
            }
        });
    }

    optimizeScrolling() {
        // Enable momentum scrolling on iOS
        document.body.style.webkitOverflowScrolling = 'touch';
        
        // Improve scroll performance
        const scrollableElements = document.querySelectorAll(
            '.onboarding-body, .modal-body, .log-container, .letter-content'
        );
        
        scrollableElements.forEach(element => {
            element.style.webkitOverflowScrolling = 'touch';
            element.style.overscrollBehavior = 'contain';
        });
        
        // Handle scroll end for better UX
        let scrollTimer;
        window.addEventListener('scroll', () => {
            clearTimeout(scrollTimer);
            document.body.classList.add('is-scrolling');
            
            scrollTimer = setTimeout(() => {
                document.body.classList.remove('is-scrolling');
            }, 150);
        }, { passive: true });
    }

    enhanceVisualFeedback() {
        // Add active states for better touch feedback
        const style = document.createElement('style');
        style.textContent = `
            .mobile-optimized button:active,
            .mobile-optimized .btn:active {
                transform: scale(0.98);
                opacity: 0.8;
                transition: transform 0.1s ease, opacity 0.1s ease;
            }
            
            .mobile-optimized input:focus,
            .mobile-optimized select:focus,
            .mobile-optimized textarea:focus {
                border-width: 2px;
                box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.2);
            }
            
            .mobile-optimized .upload-zone:active {
                transform: scale(0.99);
                background: var(--primary-light);
            }
        `;
        document.head.appendChild(style);
    }

    setupTouchInteractions() {
        // Prevent double-tap zoom on buttons
        document.addEventListener('touchend', (e) => {
            if (e.target.matches('button, .btn, [role="button"]')) {
                e.preventDefault();
            }
        }, { passive: false });

        // Handle fast taps
        document.addEventListener('touchstart', (e) => {
            this.touchStartY = e.touches[0].clientY;
            this.touchStartX = e.touches[0].clientX;
        }, { passive: true });

        // Prevent zoom on double tap for form inputs
        let lastTouchEnd = 0;
        document.addEventListener('touchend', (e) => {
            const now = Date.now();
            if (now - lastTouchEnd <= 300) {
                if (e.target.matches('input, select, textarea')) {
                    e.preventDefault();
                }
            }
            lastTouchEnd = now;
        }, { passive: false });
    }

    setupGestureHandling() {
        let startY = 0;
        let startX = 0;
        let distanceY = 0;
        let distanceX = 0;

        document.addEventListener('touchstart', (e) => {
            startY = e.touches[0].clientY;
            startX = e.touches[0].clientX;
        }, { passive: true });

        document.addEventListener('touchmove', (e) => {
            if (!startY || !startX) return;
            
            distanceY = e.touches[0].clientY - startY;
            distanceX = e.touches[0].clientX - startX;
            
            // Handle pull-to-refresh prevention
            if (distanceY > 0 && window.pageYOffset === 0) {
                e.preventDefault();
            }
        }, { passive: false });

        document.addEventListener('touchend', (e) => {
            if (!startY || !startX) return;

            const absDistanceY = Math.abs(distanceY);
            const absDistanceX = Math.abs(distanceX);
            const threshold = 50;

            // Only process swipes, not taps
            if (absDistanceY < threshold && absDistanceX < threshold) {
                startY = 0;
                startX = 0;
                return;
            }

            // Vertical swipes
            if (absDistanceY > absDistanceX) {
                if (distanceY < -threshold) {
                    this.handleSwipeUp();
                } else if (distanceY > threshold) {
                    this.handleSwipeDown();
                }
            }
            // Horizontal swipes
            else {
                if (distanceX < -threshold) {
                    this.handleSwipeLeft();
                } else if (distanceX > threshold) {
                    this.handleSwipeRight();
                }
            }

            startY = 0;
            startX = 0;
        }, { passive: true });
    }

    handleSwipeUp() {
        // Scroll to next section or submit form if at bottom
        const currentScroll = window.pageYOffset;
        const documentHeight = document.documentElement.scrollHeight;
        const windowHeight = window.innerHeight;

        if (currentScroll + windowHeight >= documentHeight - 100) {
            const submitBtn = document.querySelector('#submitBtn:not(:disabled)');
            if (submitBtn && submitBtn.offsetParent !== null) {
                this.highlightElement(submitBtn);
                setTimeout(() => submitBtn.click(), 300);
            }
        } else {
            this.scrollToNextSection();
        }
    }

    handleSwipeDown() {
        // Scroll to previous section or show help
        if (window.pageYOffset <= 100) {
            const helpBtn = document.getElementById('help-floating-btn');
            if (helpBtn && !helpBtn.classList.contains('hidden')) {
                this.highlightElement(helpBtn);
                setTimeout(() => helpBtn.click(), 300);
            }
        } else {
            this.scrollToPreviousSection();
        }
    }

    handleSwipeLeft() {
        // Close modals or go to next step in onboarding
        const modal = document.querySelector('.onboarding-modal:not(.hidden)');
        if (modal) {
            const nextBtn = modal.querySelector('#onboarding-next');
            if (nextBtn && !nextBtn.disabled) {
                this.highlightElement(nextBtn);
                setTimeout(() => nextBtn.click(), 300);
            }
        }
    }

    handleSwipeRight() {
        // Go to previous step in onboarding
        const modal = document.querySelector('.onboarding-modal:not(.hidden)');
        if (modal) {
            const prevBtn = modal.querySelector('#onboarding-prev');
            if (prevBtn && !prevBtn.disabled) {
                this.highlightElement(prevBtn);
                setTimeout(() => prevBtn.click(), 300);
            }
        }
    }

    scrollToNextSection() {
        const sections = document.querySelectorAll('.form-group, .card, .onboarding-step');
        const currentSection = this.getCurrentVisibleSection(sections);
        const nextSection = sections[currentSection + 1];
        
        if (nextSection) {
            nextSection.scrollIntoView({ 
                behavior: 'smooth', 
                block: 'start',
                inline: 'nearest'
            });
        }
    }

    scrollToPreviousSection() {
        const sections = document.querySelectorAll('.form-group, .card, .onboarding-step');
        const currentSection = this.getCurrentVisibleSection(sections);
        const prevSection = sections[currentSection - 1];
        
        if (prevSection) {
            prevSection.scrollIntoView({ 
                behavior: 'smooth', 
                block: 'start',
                inline: 'nearest'
            });
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

    highlightElement(element) {
        element.classList.add('gesture-highlight');
        setTimeout(() => {
            element.classList.remove('gesture-highlight');
        }, 500);
        
        // Add highlight style if not exists
        if (!document.querySelector('#gesture-highlight-style')) {
            const style = document.createElement('style');
            style.id = 'gesture-highlight-style';
            style.textContent = `
                .gesture-highlight {
                    animation: gestureHighlight 0.5s ease-out !important;
                    box-shadow: 0 0 20px rgba(37, 99, 235, 0.6) !important;
                }
                
                @keyframes gestureHighlight {
                    0% { transform: scale(1); }
                    50% { transform: scale(1.05); }
                    100% { transform: scale(1); }
                }
            `;
            document.head.appendChild(style);
        }
    }

    optimizeViewport() {
        // Handle viewport height changes (mobile keyboard)
        const initialViewportHeight = window.innerHeight;
        
        const handleViewportChange = () => {
            const currentHeight = window.innerHeight;
            const heightDiff = initialViewportHeight - currentHeight;
            
            // Detect virtual keyboard
            if (heightDiff > 150) {
                document.body.classList.add('keyboard-open');
                this.adjustForKeyboard(heightDiff);
            } else {
                document.body.classList.remove('keyboard-open');
                this.restoreFromKeyboard();
            }
        };

        window.addEventListener('resize', this.core.debounce(handleViewportChange, 100));
        window.addEventListener('orientationchange', () => {
            setTimeout(handleViewportChange, 500);
        });
    }

    adjustForKeyboard(keyboardHeight) {
        // Find active input
        const activeInput = document.activeElement;
        if (activeInput && activeInput.matches('input, select, textarea')) {
            const inputRect = activeInput.getBoundingClientRect();
            const visibleHeight = window.innerHeight;
            
            // If input is hidden behind keyboard
            if (inputRect.bottom > visibleHeight - 50) {
                const scrollAmount = inputRect.bottom - visibleHeight + 100;
                window.scrollBy(0, scrollAmount);
            }
        }

        // Adjust fixed elements
        const fixedElements = document.querySelectorAll('.help-floating-btn, .trial-mode-banner');
        fixedElements.forEach(element => {
            element.style.transform = `translateY(-${Math.min(keyboardHeight, 200)}px)`;
        });
    }

    restoreFromKeyboard() {
        // Restore fixed elements
        const fixedElements = document.querySelectorAll('.help-floating-btn, .trial-mode-banner');
        fixedElements.forEach(element => {
            element.style.transform = '';
        });
    }

    setupMobileNavigation() {
        // Add mobile-friendly navigation
        const nav = document.querySelector('.nav');
        if (nav && this.isMobile) {
            // Convert to mobile menu
            this.createMobileMenu(nav);
        }

        // Add floating action button for key actions
        this.createFloatingActionButton();
    }

    createMobileMenu(nav) {
        const menuToggle = document.createElement('button');
        menuToggle.className = 'mobile-menu-toggle';
        menuToggle.innerHTML = `
            <span class="hamburger-line"></span>
            <span class="hamburger-line"></span>
            <span class="hamburger-line"></span>
        `;
        
        nav.classList.add('mobile-nav');
        nav.insertBefore(menuToggle, nav.firstChild);
        
        menuToggle.addEventListener('click', () => {
            nav.classList.toggle('nav-open');
            menuToggle.classList.toggle('active');
        });
        
        // Close menu on outside click
        document.addEventListener('click', (e) => {
            if (!nav.contains(e.target)) {
                nav.classList.remove('nav-open');
                menuToggle.classList.remove('active');
            }
        });
        
        // Add mobile menu styles
        this.addMobileMenuStyles();
    }

    addMobileMenuStyles() {
        const style = document.createElement('style');
        style.textContent = `
            .mobile-nav {
                position: relative;
            }
            
            .mobile-menu-toggle {
                display: flex;
                flex-direction: column;
                justify-content: center;
                width: 44px;
                height: 44px;
                background: none;
                border: none;
                cursor: pointer;
                padding: 8px;
            }
            
            .hamburger-line {
                width: 24px;
                height: 2px;
                background: var(--gray-700);
                margin: 2px 0;
                transition: var(--transition);
            }
            
            .mobile-menu-toggle.active .hamburger-line:nth-child(1) {
                transform: rotate(45deg) translate(5px, 5px);
            }
            
            .mobile-menu-toggle.active .hamburger-line:nth-child(2) {
                opacity: 0;
            }
            
            .mobile-menu-toggle.active .hamburger-line:nth-child(3) {
                transform: rotate(-45deg) translate(7px, -6px);
            }
            
            .mobile-nav a {
                display: block;
                padding: 12px 16px;
                border-bottom: 1px solid var(--gray-200);
            }
            
            .mobile-nav.nav-open {
                background: var(--white);
                box-shadow: var(--shadow-lg);
                border-radius: var(--radius-lg);
                position: absolute;
                top: 100%;
                right: 0;
                z-index: 1000;
                min-width: 200px;
            }
        `;
        document.head.appendChild(style);
    }

    createFloatingActionButton() {
        const fab = document.createElement('button');
        fab.className = 'mobile-fab';
        fab.innerHTML = `
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
            </svg>
        `;
        fab.setAttribute('aria-label', 'Quick actions');
        
        document.body.appendChild(fab);
        
        fab.addEventListener('click', () => {
            this.showQuickActions();
        });
        
        // Add FAB styles
        this.addFABStyles();
    }

    addFABStyles() {
        const style = document.createElement('style');
        style.textContent = `
            .mobile-fab {
                position: fixed;
                bottom: 80px;
                right: 20px;
                width: 56px;
                height: 56px;
                background: var(--primary);
                color: var(--white);
                border: none;
                border-radius: 50%;
                box-shadow: var(--shadow-lg);
                cursor: pointer;
                z-index: 100;
                display: flex;
                align-items: center;
                justify-content: center;
                transition: var(--transition);
            }
            
            .mobile-fab:hover {
                background: var(--primary-hover);
                transform: scale(1.1);
            }
            
            .mobile-fab:active {
                transform: scale(0.95);
            }
        `;
        document.head.appendChild(style);
    }

    showQuickActions() {
        const actions = [
            { icon: '📋', text: 'Copy Result', action: () => document.getElementById('copyBtn')?.click() },
            { icon: '💾', text: 'Download', action: () => document.getElementById('downloadBtn')?.click() },
            { icon: '❓', text: 'Help', action: () => document.getElementById('help-floating-btn')?.click() },
            { icon: '🔄', text: 'Restart', action: () => this.restartForm() }
        ];
        
        const quickMenu = document.createElement('div');
        quickMenu.className = 'quick-actions-menu';
        quickMenu.innerHTML = `
            <div class="quick-actions-content">
                ${actions.map(action => `
                    <button class="quick-action-item" data-action="${action.text}">
                        <span class="quick-action-icon">${action.icon}</span>
                        <span class="quick-action-text">${action.text}</span>
                    </button>
                `).join('')}
            </div>
        `;
        
        document.body.appendChild(quickMenu);
        
        // Animate in
        setTimeout(() => quickMenu.classList.add('show'), 10);
        
        // Handle clicks
        quickMenu.addEventListener('click', (e) => {
            const actionItem = e.target.closest('.quick-action-item');
            if (actionItem) {
                const actionText = actionItem.dataset.action;
                const action = actions.find(a => a.text === actionText);
                if (action) {
                    action.action();
                }
                this.closeQuickActions(quickMenu);
            }
        });
        
        // Close on outside click
        setTimeout(() => {
            document.addEventListener('click', (e) => {
                if (!quickMenu.contains(e.target) && !e.target.matches('.mobile-fab')) {
                    this.closeQuickActions(quickMenu);
                }
            }, { once: true });
        }, 100);
        
        this.addQuickActionsStyles();
    }

    closeQuickActions(menu) {
        menu.classList.remove('show');
        setTimeout(() => menu.remove(), 300);
    }

    addQuickActionsStyles() {
        if (document.querySelector('#quick-actions-style')) return;
        
        const style = document.createElement('style');
        style.id = 'quick-actions-style';
        style.textContent = `
            .quick-actions-menu {
                position: fixed;
                bottom: 150px;
                right: 20px;
                background: var(--white);
                border-radius: var(--radius-xl);
                box-shadow: var(--shadow-xl);
                z-index: 1000;
                opacity: 0;
                transform: scale(0.8) translateY(20px);
                transition: var(--transition);
                overflow: hidden;
            }
            
            .quick-actions-menu.show {
                opacity: 1;
                transform: scale(1) translateY(0);
            }
            
            .quick-action-item {
                display: flex;
                align-items: center;
                gap: 12px;
                width: 100%;
                padding: 16px 20px;
                border: none;
                background: none;
                text-align: left;
                cursor: pointer;
                transition: var(--transition);
                border-bottom: 1px solid var(--gray-100);
            }
            
            .quick-action-item:last-child {
                border-bottom: none;
            }
            
            .quick-action-item:hover {
                background: var(--gray-50);
            }
            
            .quick-action-icon {
                font-size: 18px;
            }
            
            .quick-action-text {
                color: var(--gray-700);
                font-size: 14px;
                font-weight: 500;
            }
        `;
        document.head.appendChild(style);
    }

    enhanceFormForMobile() {
        // Improve form UX on mobile
        const form = document.getElementById('jobForm');
        if (!form) return;

        // Auto-scroll to validation errors
        form.addEventListener('invalid', (e) => {
            setTimeout(() => {
                e.target.scrollIntoView({
                    behavior: 'smooth',
                    block: 'center',
                    inline: 'nearest'
                });
            }, 100);
        }, true);

        // Optimize file input for mobile
        const fileInput = document.getElementById('resumeFileInput');
        if (fileInput && this.isMobile) {
            fileInput.setAttribute('capture', 'environment');
            fileInput.setAttribute('accept', '.pdf,.doc,.docx,image/*');
        }

        // Add mobile-specific input enhancements
        this.enhanceMobileInputs();
    }

    enhanceMobileInputs() {
        const inputs = document.querySelectorAll('input, textarea');
        
        inputs.forEach(input => {
            // Prevent zoom on focus for iOS
            if (input.type !== 'file') {
                input.style.fontSize = '16px';
            }
            
            // Add better input types for mobile keyboards
            if (input.id === 'jdUrlInput') {
                input.setAttribute('inputmode', 'url');
                input.setAttribute('autocomplete', 'url');
            }
            
            // Add smooth scrolling to focused input
            input.addEventListener('focus', () => {
                setTimeout(() => {
                    input.scrollIntoView({
                        behavior: 'smooth',
                        block: 'center',
                        inline: 'nearest'
                    });
                }, 300); // Wait for keyboard animation
            });
        });
    }

    setupOrientationHandling() {
        const handleOrientationChange = () => {
            const newOrientation = this.getOrientation();
            
            if (newOrientation !== this.orientation) {
                this.orientation = newOrientation;
                document.body.classList.toggle('landscape', Math.abs(newOrientation) === 90);
                
                // Adjust layout for orientation
                setTimeout(() => {
                    this.adjustLayoutForOrientation();
                }, 100);
                
                // Track orientation change
                this.core.logMetric('orientation_change', 1, {
                    from: this.orientation,
                    to: newOrientation
                });
            }
        };

        window.addEventListener('orientationchange', handleOrientationChange);
        screen.orientation?.addEventListener('change', handleOrientationChange);
    }

    adjustLayoutForOrientation() {
        const isLandscape = Math.abs(this.orientation) === 90;
        
        // Adjust onboarding modal for landscape
        const onboardingModal = document.querySelector('.onboarding-modal .onboarding-content');
        if (onboardingModal) {
            if (isLandscape) {
                onboardingModal.style.maxHeight = '85vh';
                onboardingModal.style.width = '95vw';
            } else {
                onboardingModal.style.maxHeight = '';
                onboardingModal.style.width = '';
            }
        }
        
        // Adjust form layout
        const form = document.getElementById('jobForm');
        if (form) {
            form.classList.toggle('landscape-mode', isLandscape);
        }
    }

    setupPWAFeatures() {
        // Add to home screen prompt
        let deferredPrompt;
        
        window.addEventListener('beforeinstallprompt', (e) => {
            e.preventDefault();
            deferredPrompt = e;
            this.showInstallBanner();
        });

        window.addEventListener('appinstalled', () => {
            this.core.logMetric('pwa_installed', 1);
            this.hideInstallBanner();
        });
    }

    showInstallBanner() {
        const banner = document.createElement('div');
        banner.className = 'install-banner';
        banner.innerHTML = `
            <div class="install-banner-content">
                <div class="install-info">
                    <span class="install-icon">📱</span>
                    <div class="install-text">
                        <div class="install-title">Install HuskyApply</div>
                        <div class="install-subtitle">Access faster and work offline</div>
                    </div>
                </div>
                <div class="install-actions">
                    <button class="btn btn-sm btn-primary install-btn">Install</button>
                    <button class="btn btn-sm btn-outline dismiss-btn">Not now</button>
                </div>
            </div>
        `;
        
        document.body.appendChild(banner);
        
        // Handle install
        banner.querySelector('.install-btn').addEventListener('click', async () => {
            if (deferredPrompt) {
                deferredPrompt.prompt();
                const { outcome } = await deferredPrompt.userChoice;
                this.core.logMetric('pwa_install_prompt', 1, { outcome });
                deferredPrompt = null;
            }
            this.hideInstallBanner();
        });
        
        // Handle dismiss
        banner.querySelector('.dismiss-btn').addEventListener('click', () => {
            this.hideInstallBanner();
            localStorage.setItem('pwa_install_dismissed', Date.now().toString());
        });
        
        this.addInstallBannerStyles();
    }

    hideInstallBanner() {
        const banner = document.querySelector('.install-banner');
        if (banner) {
            banner.remove();
        }
    }

    addInstallBannerStyles() {
        if (document.querySelector('#install-banner-style')) return;
        
        const style = document.createElement('style');
        style.id = 'install-banner-style';
        style.textContent = `
            .install-banner {
                position: fixed;
                bottom: 0;
                left: 0;
                right: 0;
                background: var(--white);
                border-top: 1px solid var(--gray-200);
                box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.1);
                z-index: 1000;
                animation: slideInUp 0.3s ease;
            }
            
            @keyframes slideInUp {
                from { transform: translateY(100%); }
                to { transform: translateY(0); }
            }
            
            .install-banner-content {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 16px;
                max-width: 100%;
            }
            
            .install-info {
                display: flex;
                align-items: center;
                gap: 12px;
                flex: 1;
            }
            
            .install-icon {
                font-size: 24px;
            }
            
            .install-title {
                font-weight: 600;
                color: var(--gray-800);
                font-size: 14px;
            }
            
            .install-subtitle {
                font-size: 12px;
                color: var(--gray-600);
            }
            
            .install-actions {
                display: flex;
                gap: 8px;
                align-items: center;
            }
        `;
        document.head.appendChild(style);
    }

    setupMobilePerformance() {
        // Reduce animations on low-end devices
        if (navigator.hardwareConcurrency <= 2) {
            document.body.classList.add('reduce-motion');
        }
        
        // Lazy load images
        if ('IntersectionObserver' in window) {
            const imageObserver = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const img = entry.target;
                        if (img.dataset.src) {
                            img.src = img.dataset.src;
                            img.removeAttribute('data-src');
                            imageObserver.unobserve(img);
                        }
                    }
                });
            });
            
            document.querySelectorAll('img[data-src]').forEach(img => {
                imageObserver.observe(img);
            });
        }
        
        // Optimize for battery
        this.optimizeForBattery();
    }

    optimizeForBattery() {
        if ('getBattery' in navigator) {
            navigator.getBattery().then(battery => {
                if (battery.level < 0.2 || battery.charging === false) {
                    document.body.classList.add('battery-saver');
                    this.enableBatterySaver();
                }
                
                battery.addEventListener('levelchange', () => {
                    if (battery.level < 0.2) {
                        this.enableBatterySaver();
                    } else if (battery.level > 0.3) {
                        this.disableBatterySaver();
                    }
                });
            });
        }
    }

    enableBatterySaver() {
        document.body.classList.add('battery-saver');
        // Reduce update frequencies, disable non-essential animations
        this.core.logMetric('battery_saver_enabled', 1);
    }

    disableBatterySaver() {
        document.body.classList.remove('battery-saver');
        this.core.logMetric('battery_saver_disabled', 1);
    }

    restartForm() {
        const form = document.getElementById('jobForm');
        if (form && confirm('Are you sure you want to restart the form?')) {
            form.reset();
            window.scrollTo({ top: 0, behavior: 'smooth' });
            this.core.logMetric('form_restart_mobile', 1);
        }
    }

    destroy() {
        // Clean up mobile-specific elements and listeners
        document.querySelectorAll('.mobile-fab, .install-banner, .quick-actions-menu').forEach(el => {
            el.remove();
        });
        
        document.body.classList.remove('mobile-optimized', 'keyboard-open', 'battery-saver');
        
        this.initialized = false;
    }
}

// Export for dynamic loading
export { MobileOptimizationsModule };
export const init = async () => {
    const mobileOptimizations = new MobileOptimizationsModule();
    await mobileOptimizations.init();
    return mobileOptimizations;
};