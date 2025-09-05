/**
 * HuskyApply Core Application Module
 * Minimal critical functionality loaded immediately
 */

// ======================================
// CRITICAL CONFIGURATION & STATE
// ======================================

export const AppCore = {
    // Core configuration - only essential values
    config: {
        API_BASE: window.location.origin + '/api/v1',
        MAX_FILE_SIZE: 10 * 1024 * 1024, // 10MB
        SUPPORTED_FORMATS: ['.pdf', '.doc', '.docx']
    },
    
    // Minimal state for core functionality
    state: {
        isAuthenticated: false,
        user: null,
        theme: localStorage.getItem('theme') || 'light',
        currentModule: null
    },
    
    // Module registry for dynamic loading
    modules: new Map(),
    
    // Performance metrics
    metrics: {
        loadStartTime: performance.now(),
        moduleLoadTimes: new Map()
    }
};

// ======================================
// CRITICAL AUTHENTICATION
// ======================================

export class AuthCore {
    static init() {
        const token = localStorage.getItem('jwtToken');
        AppCore.state.isAuthenticated = !!token;
        
        // Only redirect if on protected pages without token
        if (!token && this.isProtectedPage()) {
            window.location.href = 'login.html';
            return false;
        }
        
        return true;
    }
    
    static isProtectedPage() {
        const path = window.location.pathname;
        return path.includes('dashboard') || 
               path.includes('templates') || 
               path === '/index.html' || 
               path === '/';
    }
    
    static getToken() {
        return localStorage.getItem('jwtToken');
    }
    
    static setToken(token) {
        localStorage.setItem('jwtToken', token);
        AppCore.state.isAuthenticated = true;
    }
    
    static clearToken() {
        localStorage.removeItem('jwtToken');
        AppCore.state.isAuthenticated = false;
        AppCore.state.user = null;
    }
}

// ======================================
// DYNAMIC MODULE LOADER
// ======================================

export class ModuleLoader {
    static async loadModule(moduleName, options = {}) {
        const startTime = performance.now();
        
        try {
            // Check if already loaded
            if (AppCore.modules.has(moduleName)) {
                return AppCore.modules.get(moduleName);
            }
            
            console.log(`🔄 Loading module: ${moduleName}`);
            
            // Show loading indicator if specified
            if (options.showLoading) {
                this.showModuleLoading(moduleName);
            }
            
            // Dynamic import based on module name
            let module;
            switch (moduleName) {
                case 'auth':
                    module = await import('./modules/auth.js');
                    break;
                case 'dashboard':
                    module = await import('./modules/dashboard.js');
                    break;
                case 'templates':
                    module = await import('./modules/templates.js');
                    break;
                case 'form':
                    module = await import('./modules/form.js');
                    break;
                case 'processing':
                    module = await import('./modules/processing.js');
                    break;
                case 'notifications':
                    module = await import('./modules/notifications.js');
                    break;
                case 'ui':
                    module = await import('./modules/ui.js');
                    break;
                default:
                    throw new Error(`Unknown module: ${moduleName}`);
            }
            
            // Initialize module if it has an init function
            if (module.default && typeof module.default.init === 'function') {
                await module.default.init();
            }
            
            // Store in registry
            AppCore.modules.set(moduleName, module.default || module);
            AppCore.state.currentModule = moduleName;
            
            // Track performance
            const loadTime = performance.now() - startTime;
            AppCore.metrics.moduleLoadTimes.set(moduleName, loadTime);
            
            console.log(`✅ Module loaded: ${moduleName} (${loadTime.toFixed(2)}ms)`);
            
            // Hide loading indicator
            if (options.showLoading) {
                this.hideModuleLoading();
            }
            
            return AppCore.modules.get(moduleName);
            
        } catch (error) {
            console.error(`❌ Failed to load module: ${moduleName}`, error);
            
            if (options.showLoading) {
                this.hideModuleLoading();
            }
            
            // Show user-friendly error
            this.showModuleError(moduleName, error.message);
            throw error;
        }
    }
    
    static showModuleLoading(moduleName) {
        // Create or show loading overlay
        let overlay = document.getElementById('moduleLoadingOverlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'moduleLoadingOverlay';
            overlay.className = 'module-loading-overlay';
            overlay.innerHTML = `
                <div class="module-loading-content">
                    <div class="loading-spinner"></div>
                    <p class="loading-text">Loading ${moduleName} module...</p>
                </div>
            `;
            document.body.appendChild(overlay);
        }
        
        overlay.querySelector('.loading-text').textContent = `Loading ${moduleName} module...`;
        overlay.style.display = 'flex';
    }
    
    static hideModuleLoading() {
        const overlay = document.getElementById('moduleLoadingOverlay');
        if (overlay) {
            overlay.style.display = 'none';
        }
    }
    
    static showModuleError(moduleName, message) {
        // Simple error notification
        const error = document.createElement('div');
        error.className = 'module-error-toast';
        error.innerHTML = `
            <span class="error-icon">⚠️</span>
            <span>Failed to load ${moduleName}: ${message}</span>
            <button onclick="this.parentElement.remove()">×</button>
        `;
        document.body.appendChild(error);
        
        // Auto remove after 5 seconds
        setTimeout(() => error.remove(), 5000);
    }
}

// ======================================
// CRITICAL THEME MANAGEMENT
// ======================================

export class ThemeCore {
    static init() {
        this.applyTheme(AppCore.state.theme);
        this.setupToggle();
    }
    
    static applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        AppCore.state.theme = theme;
        localStorage.setItem('theme', theme);
    }
    
    static setupToggle() {
        const toggle = document.getElementById('themeToggle');
        if (toggle) {
            toggle.addEventListener('click', () => {
                const newTheme = AppCore.state.theme === 'light' ? 'dark' : 'light';
                this.applyTheme(newTheme);
            });
        }
    }
}

// ======================================
// CRITICAL ERROR HANDLING
// ======================================

export class ErrorHandler {
    static init() {
        // Global error handling
        window.addEventListener('error', this.handleError.bind(this));
        window.addEventListener('unhandledrejection', this.handlePromiseRejection.bind(this));
    }
    
    static handleError(event) {
        console.error('Global error:', event.error);
        this.logError('JavaScript Error', event.error.message, event.error.stack);
    }
    
    static handlePromiseRejection(event) {
        console.error('Unhandled promise rejection:', event.reason);
        this.logError('Promise Rejection', event.reason);
        event.preventDefault();
    }
    
    static logError(type, message, stack = null) {
        // Log to console and potentially send to analytics
        const errorData = {
            type,
            message,
            stack,
            timestamp: new Date().toISOString(),
            userAgent: navigator.userAgent,
            url: window.location.href
        };
        
        // Store in session storage for debugging
        try {
            const errors = JSON.parse(sessionStorage.getItem('huskyapply_errors') || '[]');
            errors.push(errorData);
            // Keep only last 10 errors
            if (errors.length > 10) errors.shift();
            sessionStorage.setItem('huskyapply_errors', JSON.stringify(errors));
        } catch (e) {
            console.warn('Failed to store error in session storage');
        }
    }
}

// ======================================
// UTILITY FUNCTIONS
// ======================================

export function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

export function throttle(func, limit) {
    let inThrottle;
    return function(...args) {
        if (!inThrottle) {
            func.apply(this, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

// ======================================
// PERFORMANCE MONITORING
// ======================================

export class PerformanceMonitor {
    static init() {
        // Mark critical loading points
        performance.mark('app-core-loaded');
        
        // Monitor Core Web Vitals if supported
        if ('PerformanceObserver' in window) {
            this.observeWebVitals();
        }
    }
    
    static observeWebVitals() {
        try {
            // Largest Contentful Paint
            new PerformanceObserver((list) => {
                for (const entry of list.getEntries()) {
                    console.log('LCP:', entry.startTime);
                }
            }).observe({ entryTypes: ['largest-contentful-paint'] });
            
            // First Input Delay
            new PerformanceObserver((list) => {
                for (const entry of list.getEntries()) {
                    console.log('FID:', entry.processingStart - entry.startTime);
                }
            }).observe({ entryTypes: ['first-input'] });
            
            // Cumulative Layout Shift
            new PerformanceObserver((list) => {
                let clsValue = 0;
                for (const entry of list.getEntries()) {
                    if (!entry.hadRecentInput) {
                        clsValue += entry.value;
                    }
                }
                console.log('CLS:', clsValue);
            }).observe({ entryTypes: ['layout-shift'] });
        } catch (error) {
            console.warn('Performance monitoring not fully supported:', error);
        }
    }
    
    static getMetrics() {
        return {
            loadTime: performance.now() - AppCore.metrics.loadStartTime,
            moduleLoadTimes: Object.fromEntries(AppCore.metrics.moduleLoadTimes),
            navigation: performance.getEntriesByType('navigation')[0],
            resources: performance.getEntriesByType('resource').length
        };
    }
}

// ======================================
// CORE INITIALIZATION
// ======================================

export async function initializeCore() {
    console.log('🐺 HuskyApply Core Initializing...');
    
    try {
        // Critical authentication check
        if (!AuthCore.init()) {
            return; // Redirected to login
        }
        
        // Initialize core systems
        ThemeCore.init();
        ErrorHandler.init();
        PerformanceMonitor.init();
        
        console.log('✅ HuskyApply Core Ready');
        
        // Mark core as loaded
        performance.mark('app-core-ready');
        
        return true;
        
    } catch (error) {
        console.error('❌ Core initialization failed:', error);
        ErrorHandler.logError('Core Init', error.message, error.stack);
        throw error;
    }
}

// Export core for global access
window.HuskyApplyCore = {
    AppCore,
    AuthCore,
    ModuleLoader,
    ThemeCore,
    PerformanceMonitor,
    initializeCore
};