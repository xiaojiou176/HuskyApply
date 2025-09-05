/**
 * UI Enhancement Module
 * Handles responsive behavior, animations, accessibility, and progressive enhancement
 */

export default class UIModule {
    constructor() {
        this.initialized = false;
        this.intersectionObserver = null;
        this.mediaQueries = new Map();
        this.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    }
    
    async init() {
        if (this.initialized) return;
        
        console.log('🎨 Initializing UI Module...');
        
        // Core UI enhancements
        this.setupIntersectionObserver();
        this.setupMediaQueries();
        this.setupAccessibilityFeatures();
        this.setupAnimations();
        this.setupMobileOptimizations();
        this.setupSkeletonScreens();
        this.setupLazyLoading();
        
        this.initialized = true;
        console.log('✅ UI Module Ready');
    }
    
    setupIntersectionObserver() {
        if (!('IntersectionObserver' in window)) {
            console.warn('IntersectionObserver not supported');
            return;
        }
        
        this.intersectionObserver = new IntersectionObserver(
            (entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        this.handleElementInView(entry.target);
                    } else {
                        this.handleElementOutOfView(entry.target);
                    }
                });
            },
            {
                threshold: [0, 0.1, 0.5, 0.9, 1],
                rootMargin: '50px'
            }
        );
        
        // Observe lazy-loadable elements
        document.querySelectorAll('[data-lazy], [data-animate-in]').forEach(el => {
            this.intersectionObserver.observe(el);
        });
    }
    
    handleElementInView(element) {
        // Animate in
        if (element.hasAttribute('data-animate-in') && !element.classList.contains('animated')) {
            const animationType = element.getAttribute('data-animate-in') || 'fade-in';
            this.animateElement(element, animationType);
            element.classList.add('animated');
        }
        
        // Lazy load
        if (element.hasAttribute('data-lazy')) {
            this.lazyLoadElement(element);
        }
        
        // Preload next section
        if (element.hasAttribute('data-preload-next')) {
            this.preloadNext(element);
        }
    }
    
    handleElementOutOfView(element) {
        // Optional: Handle elements leaving viewport
        if (element.hasAttribute('data-animate-out')) {
            const animationType = element.getAttribute('data-animate-out') || 'fade-out';
            this.animateElement(element, animationType);
        }
    }
    
    setupMediaQueries() {
        // Common breakpoints
        const breakpoints = {
            mobile: '(max-width: 768px)',
            tablet: '(min-width: 769px) and (max-width: 1024px)',
            desktop: '(min-width: 1025px)',
            largeDesktop: '(min-width: 1440px)',
            highDPI: '(-webkit-min-device-pixel-ratio: 2), (min-resolution: 192dpi)'
        };
        
        Object.entries(breakpoints).forEach(([name, query]) => {
            const mediaQuery = window.matchMedia(query);
            this.mediaQueries.set(name, mediaQuery);
            
            mediaQuery.addListener((mq) => {
                document.documentElement.setAttribute(`data-${name}`, mq.matches);
                this.handleBreakpointChange(name, mq.matches);
            });
            
            // Set initial state
            document.documentElement.setAttribute(`data-${name}`, mediaQuery.matches);
        });
    }
    
    handleBreakpointChange(breakpoint, matches) {
        if (matches) {
            document.body.classList.add(`bp-${breakpoint}`);
            
            // Specific optimizations per breakpoint
            switch (breakpoint) {
                case 'mobile':
                    this.optimizeForMobile();
                    break;
                case 'desktop':
                    this.optimizeForDesktop();
                    break;
            }
        } else {
            document.body.classList.remove(`bp-${breakpoint}`);
        }
    }
    
    optimizeForMobile() {
        // Reduce animations on mobile
        if (this.reducedMotion) {
            document.documentElement.style.setProperty('--animation-duration', '0.1s');
        }
        
        // Optimize touch targets
        document.querySelectorAll('button, a, input').forEach(el => {
            el.style.minHeight = '44px'; // iOS recommendation
        });
        
        // Setup mobile menu
        this.setupMobileMenu();
    }
    
    optimizeForDesktop() {
        // Restore full animations
        document.documentElement.style.removeProperty('--animation-duration');
        
        // Setup hover effects
        this.setupHoverEffects();
        
        // Setup keyboard navigation
        this.setupKeyboardNavigation();
    }
    
    setupMobileMenu() {
        const toggle = document.getElementById('mobileMenuToggle');
        const menu = document.getElementById('mobileMenu');
        
        if (toggle && menu) {
            toggle.addEventListener('click', () => {
                const isOpen = menu.classList.contains('open');
                menu.classList.toggle('open');
                toggle.setAttribute('aria-expanded', !isOpen);
                
                // Prevent body scrolling when menu is open
                document.body.style.overflow = isOpen ? '' : 'hidden';
            });
            
            // Close on outside click
            document.addEventListener('click', (e) => {
                if (!menu.contains(e.target) && !toggle.contains(e.target)) {
                    menu.classList.remove('open');
                    toggle.setAttribute('aria-expanded', 'false');
                    document.body.style.overflow = '';
                }
            });
        }
    }
    
    setupAccessibilityFeatures() {
        // Skip link
        this.createSkipLink();
        
        // Focus management
        this.setupFocusManagement();
        
        // Keyboard navigation
        this.setupKeyboardNavigation();
        
        // Screen reader support
        this.setupScreenReaderSupport();
        
        // High contrast support
        this.setupHighContrastSupport();
    }
    
    createSkipLink() {
        const skipLink = document.createElement('a');
        skipLink.href = '#main-content';
        skipLink.className = 'skip-link';
        skipLink.textContent = 'Skip to main content';
        skipLink.addEventListener('click', (e) => {
            e.preventDefault();
            const mainContent = document.getElementById('main-content') || document.querySelector('main');
            if (mainContent) {
                mainContent.focus();
                mainContent.scrollIntoView();
            }
        });
        
        document.body.insertBefore(skipLink, document.body.firstChild);
    }
    
    setupFocusManagement() {
        // Focus trap for modals
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Tab') {
                const modal = document.querySelector('.modal:not(.hidden), .alert-overlay');
                if (modal) {
                    this.trapFocus(e, modal);
                }
            }
        });
        
        // Focus indicators
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Tab') {
                document.body.classList.add('using-keyboard');
            }
        });
        
        document.addEventListener('mousedown', () => {
            document.body.classList.remove('using-keyboard');
        });
    }
    
    trapFocus(e, container) {
        const focusableElements = container.querySelectorAll(
            'a[href], button, textarea, input[type="text"], input[type="radio"], input[type="checkbox"], select'
        );
        
        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];
        
        if (e.shiftKey) {
            if (document.activeElement === firstElement) {
                e.preventDefault();
                lastElement.focus();
            }
        } else {
            if (document.activeElement === lastElement) {
                e.preventDefault();
                firstElement.focus();
            }
        }
    }
    
    setupKeyboardNavigation() {
        document.addEventListener('keydown', (e) => {
            switch (e.key) {
                case 'Escape':
                    this.handleEscape();
                    break;
                case 'Enter':
                    if (e.target.role === 'button' || e.target.classList.contains('clickable')) {
                        e.target.click();
                    }
                    break;
            }
        });
    }
    
    handleEscape() {
        // Close modals
        document.querySelectorAll('.modal:not(.hidden)').forEach(modal => {
            modal.classList.add('hidden');
        });
        
        // Close mobile menu
        const mobileMenu = document.getElementById('mobileMenu');
        const mobileToggle = document.getElementById('mobileMenuToggle');
        
        if (mobileMenu?.classList.contains('open')) {
            mobileMenu.classList.remove('open');
            if (mobileToggle) {
                mobileToggle.setAttribute('aria-expanded', 'false');
                mobileToggle.focus();
            }
            document.body.style.overflow = '';
        }
        
        // Close dropdowns
        document.querySelectorAll('[aria-expanded="true"]').forEach(element => {
            element.setAttribute('aria-expanded', 'false');
        });
    }
    
    setupScreenReaderSupport() {
        // Create announcement region
        const announcer = document.createElement('div');
        announcer.setAttribute('aria-live', 'polite');
        announcer.setAttribute('aria-atomic', 'true');
        announcer.className = 'sr-only';
        announcer.id = 'live-announcer';
        document.body.appendChild(announcer);
        
        // Global announcement function
        window.announceToScreenReader = (message, priority = 'polite') => {
            announcer.setAttribute('aria-live', priority);
            announcer.textContent = message;
            setTimeout(() => {
                announcer.textContent = '';
            }, 1000);
        };
    }
    
    setupHighContrastSupport() {
        // Detect high contrast mode
        const highContrastMQ = window.matchMedia('(prefers-contrast: high)');
        
        const updateHighContrast = (mq) => {
            document.documentElement.setAttribute('data-high-contrast', mq.matches);
        };
        
        highContrastMQ.addListener(updateHighContrast);
        updateHighContrast(highContrastMQ);
    }
    
    setupAnimations() {
        // Respect user's motion preferences
        const reducedMotionMQ = window.matchMedia('(prefers-reduced-motion: reduce)');
        
        const updateMotionPreference = (mq) => {
            this.reducedMotion = mq.matches;
            document.documentElement.setAttribute('data-reduced-motion', mq.matches);
            
            if (mq.matches) {
                document.documentElement.style.setProperty('--animation-duration', '0.01s');
                document.documentElement.style.setProperty('--transition-duration', '0.01s');
            } else {
                document.documentElement.style.removeProperty('--animation-duration');
                document.documentElement.style.removeProperty('--transition-duration');
            }
        };
        
        reducedMotionMQ.addListener(updateMotionPreference);
        updateMotionPreference(reducedMotionMQ);
    }
    
    animateElement(element, animationType) {
        if (this.reducedMotion) return;
        
        element.classList.add('animate', `animate-${animationType}`);
        
        const handleAnimationEnd = () => {
            element.classList.remove('animate', `animate-${animationType}`);
            element.removeEventListener('animationend', handleAnimationEnd);
        };
        
        element.addEventListener('animationend', handleAnimationEnd);
    }
    
    setupHoverEffects() {
        // Add hover effects only on devices that support hover
        if (window.matchMedia('(hover: hover)').matches) {
            document.body.classList.add('has-hover');
        }
    }
    
    setupSkeletonScreens() {
        // Find elements with skeleton loading states
        document.querySelectorAll('[data-skeleton]').forEach(element => {
            this.createSkeletonLoader(element);
        });
    }
    
    createSkeletonLoader(element) {
        const skeletonType = element.getAttribute('data-skeleton');
        const skeleton = document.createElement('div');
        skeleton.className = `skeleton skeleton-${skeletonType}`;
        
        // Insert skeleton before the actual element
        element.parentNode.insertBefore(skeleton, element);
        
        // Hide actual element initially
        element.style.display = 'none';
        
        // Store reference for later removal
        element.skeletonElement = skeleton;
    }
    
    removeSkeleton(element) {
        if (element.skeletonElement) {
            element.skeletonElement.remove();
            element.style.display = '';
            element.skeletonElement = null;
        }
    }
    
    setupLazyLoading() {
        // Setup intersection observer for images
        if ('IntersectionObserver' in window) {
            const imageObserver = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        this.lazyLoadImage(entry.target);
                        imageObserver.unobserve(entry.target);
                    }
                });
            });
            
            // Observe all images with data-src
            document.querySelectorAll('img[data-src]').forEach(img => {
                imageObserver.observe(img);
            });
        }
    }
    
    lazyLoadElement(element) {
        const src = element.getAttribute('data-src');
        const srcset = element.getAttribute('data-srcset');
        
        if (element.tagName === 'IMG') {
            this.lazyLoadImage(element, src, srcset);
        } else if (element.hasAttribute('data-lazy-module')) {
            this.lazyLoadModule(element);
        }
    }
    
    lazyLoadImage(img, src = null, srcset = null) {
        const imageSrc = src || img.getAttribute('data-src');
        const imageSrcset = srcset || img.getAttribute('data-srcset');
        
        if (!imageSrc) return;
        
        // Create new image to preload
        const newImg = new Image();
        
        newImg.onload = () => {
            // Update src
            img.src = imageSrc;
            if (imageSrcset) {
                img.srcset = imageSrcset;
            }
            
            // Add loaded class for animation
            img.classList.add('loaded');
            
            // Remove lazy loading attributes
            img.removeAttribute('data-src');
            img.removeAttribute('data-srcset');
        };
        
        newImg.onerror = () => {
            img.classList.add('error');
        };
        
        // Start loading
        newImg.src = imageSrc;
        if (imageSrcset) {
            newImg.srcset = imageSrcset;
        }
    }
    
    async lazyLoadModule(element) {
        const moduleName = element.getAttribute('data-lazy-module');
        
        try {
            const { ModuleLoader } = window.HuskyApplyCore;
            await ModuleLoader.loadModule(moduleName);
            element.removeAttribute('data-lazy-module');
        } catch (error) {
            console.error('Failed to lazy load module:', moduleName, error);
        }
    }
    
    preloadNext(element) {
        const nextSrc = element.getAttribute('data-preload-next');
        if (nextSrc) {
            const link = document.createElement('link');
            link.rel = 'preload';
            link.href = nextSrc;
            link.as = nextSrc.endsWith('.js') ? 'script' : 'document';
            document.head.appendChild(link);
        }
    }
    
    // Utility methods
    showElement(element, animationType = 'fade-in') {
        element.classList.remove('hidden');
        if (!this.reducedMotion) {
            this.animateElement(element, animationType);
        }
    }
    
    hideElement(element, animationType = 'fade-out') {
        if (this.reducedMotion) {
            element.classList.add('hidden');
        } else {
            this.animateElement(element, animationType);
            element.addEventListener('animationend', () => {
                element.classList.add('hidden');
            }, { once: true });
        }
    }
    
    smoothScrollTo(target, offset = 0) {
        const element = typeof target === 'string' ? document.querySelector(target) : target;
        if (element) {
            const elementPosition = element.getBoundingClientRect().top + window.pageYOffset;
            const targetPosition = elementPosition - offset;
            
            window.scrollTo({
                top: targetPosition,
                behavior: this.reducedMotion ? 'auto' : 'smooth'
            });
        }
    }
    
    // Cleanup
    destroy() {
        if (this.intersectionObserver) {
            this.intersectionObserver.disconnect();
        }
        
        this.mediaQueries.forEach(mq => {
            mq.removeListener(this.handleBreakpointChange);
        });
        
        this.initialized = false;
    }
}