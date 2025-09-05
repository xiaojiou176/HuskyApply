/**
 * Core Module - Essential utilities and base functionality
 * 
 * This module contains the core utilities that are needed immediately on page load.
 * It's loaded synchronously to ensure critical functionality is available.
 */

class HuskyApplyCore {
    constructor() {
        this.apiBase = '/api/v1';
        this.token = localStorage.getItem('jwtToken');
        this.eventBus = new EventTarget();
        this.loadedModules = new Set();
        
        this.initializeCore();
    }

    initializeCore() {
        // Check authentication immediately
        if (!this.token) {
            this.redirectToLogin();
            return;
        }

        // Set up global error handling
        this.setupErrorHandling();
        
        // Initialize performance monitoring
        this.initPerformanceMonitoring();
    }

    setupErrorHandling() {
        window.addEventListener('error', (event) => {
            console.error('Global error:', event.error);
            this.logError('javascript_error', event.error.message, event.filename, event.lineno);
        });

        window.addEventListener('unhandledrejection', (event) => {
            console.error('Unhandled promise rejection:', event.reason);
            this.logError('promise_rejection', event.reason.toString());
        });
    }

    initPerformanceMonitoring() {
        // Track page load performance
        window.addEventListener('load', () => {
            setTimeout(() => {
                const perfData = performance.timing;
                const loadTime = perfData.loadEventEnd - perfData.navigationStart;
                
                this.logMetric('page_load_time', loadTime);
                console.log(`Page loaded in ${loadTime}ms`);
            }, 0);
        });

        // Track resource loading
        if ('PerformanceObserver' in window) {
            const observer = new PerformanceObserver((list) => {
                for (const entry of list.getEntries()) {
                    if (entry.entryType === 'resource') {
                        this.logMetric('resource_load_time', entry.duration, {
                            resource: entry.name,
                            type: entry.initiatorType
                        });
                    }
                }
            });
            observer.observe({ entryTypes: ['resource'] });
        }
    }

    // Authentication utilities
    redirectToLogin() {
        window.location.href = 'login.html';
    }

    isAuthenticated() {
        return !!this.token && !this.isTokenExpired();
    }

    isTokenExpired() {
        if (!this.token) return true;
        
        try {
            const payload = JSON.parse(atob(this.token.split('.')[1]));
            return Date.now() >= payload.exp * 1000;
        } catch {
            return true;
        }
    }

    // API utilities
    async apiCall(endpoint, options = {}) {
        const url = `${this.apiBase}${endpoint}`;
        const defaultHeaders = {
            'Content-Type': 'application/json',
        };

        if (this.token) {
            defaultHeaders['Authorization'] = `Bearer ${this.token}`;
        }

        const config = {
            headers: { ...defaultHeaders, ...options.headers },
            ...options
        };

        try {
            const response = await fetch(url, config);
            
            if (response.status === 401) {
                this.redirectToLogin();
                return null;
            }

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            return await response.json();
        } catch (error) {
            console.error('API call failed:', error);
            this.logError('api_error', error.message, url);
            throw error;
        }
    }

    // Dynamic module loading
    async loadModule(moduleName, force = false) {
        if (this.loadedModules.has(moduleName) && !force) {
            return;
        }

        try {
            console.log(`Loading module: ${moduleName}`);
            const startTime = performance.now();
            
            const module = await import(`/modules/${moduleName}.js`);
            
            const loadTime = performance.now() - startTime;
            this.logMetric('module_load_time', loadTime, { module: moduleName });
            
            this.loadedModules.add(moduleName);
            
            // Initialize module if it has an init function
            if (module.init && typeof module.init === 'function') {
                await module.init();
            }
            
            console.log(`Module ${moduleName} loaded in ${loadTime.toFixed(2)}ms`);
            return module;
            
        } catch (error) {
            console.error(`Failed to load module ${moduleName}:`, error);
            this.logError('module_load_error', error.message, moduleName);
            throw error;
        }
    }

    // Event system
    emit(event, data) {
        this.eventBus.dispatchEvent(new CustomEvent(event, { detail: data }));
    }

    on(event, handler) {
        this.eventBus.addEventListener(event, handler);
    }

    off(event, handler) {
        this.eventBus.removeEventListener(event, handler);
    }

    // Logging and metrics
    logError(type, message, details = null) {
        const errorData = {
            type,
            message,
            details,
            timestamp: new Date().toISOString(),
            url: window.location.href,
            userAgent: navigator.userAgent
        };

        // Send to analytics if available
        this.sendAnalytics('error', errorData);
    }

    logMetric(metric, value, tags = {}) {
        const metricData = {
            metric,
            value,
            tags,
            timestamp: new Date().toISOString()
        };

        this.sendAnalytics('metric', metricData);
    }

    sendAnalytics(type, data) {
        // Store analytics locally for batching
        const analytics = JSON.parse(localStorage.getItem('analytics') || '[]');
        analytics.push({ type, data });
        
        // Keep only last 100 entries
        if (analytics.length > 100) {
            analytics.splice(0, analytics.length - 100);
        }
        
        localStorage.setItem('analytics', JSON.stringify(analytics));

        // Send batch if we have enough data or periodically
        if (analytics.length >= 10) {
            this.flushAnalytics();
        }
    }

    async flushAnalytics() {
        const analytics = JSON.parse(localStorage.getItem('analytics') || '[]');
        if (analytics.length === 0) return;

        try {
            // In a real implementation, send to analytics service
            console.log('Flushing analytics:', analytics);
            localStorage.setItem('analytics', '[]');
        } catch (error) {
            console.error('Failed to flush analytics:', error);
        }
    }

    // Utility functions
    debounce(func, wait) {
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

    throttle(func, limit) {
        let lastRun;
        return function(...args) {
            if (!lastRun || (Date.now() - lastRun >= limit)) {
                func.apply(this, args);
                lastRun = Date.now();
            }
        };
    }

    formatBytes(bytes, decimals = 2) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const dm = decimals < 0 ? 0 : decimals;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    }

    formatTime(ms) {
        if (ms < 1000) return `${ms.toFixed(0)}ms`;
        if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
        return `${(ms / 60000).toFixed(1)}m`;
    }
}

// Initialize core and make it globally available
window.HuskyApply = new HuskyApplyCore();

export default window.HuskyApply;