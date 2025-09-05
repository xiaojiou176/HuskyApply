/**
 * HuskyApply Frontend - Production Configuration
 * Optimized settings for production deployment with performance, security, and reliability
 */

const ProductionConfig = {
    // Environment Configuration
    environment: 'production',
    debug: false,
    
    // API Configuration
    api: {
        baseUrl: window.location.origin, // Use same origin in production
        version: 'v1',
        timeout: 30000, // 30 seconds
        retryAttempts: 3,
        retryDelay: 1000, // 1 second
        
        // Endpoints
        endpoints: {
            auth: {
                login: '/api/v1/auth/login',
                register: '/api/v1/auth/register',
                refresh: '/api/v1/auth/refresh',
                logout: '/api/v1/auth/logout'
            },
            applications: {
                submit: '/api/v1/applications',
                status: '/api/v1/applications/{jobId}',
                stream: '/api/v1/applications/{jobId}/stream',
                artifact: '/api/v1/applications/{jobId}/artifact'
            },
            uploads: {
                presignedUrl: '/api/v1/uploads/presigned-url'
            },
            dashboard: {
                stats: '/api/v1/dashboard/stats',
                jobs: '/api/v1/dashboard/jobs',
                recentActivity: '/api/v1/dashboard/recent-activity'
            },
            templates: {
                list: '/api/v1/templates',
                create: '/api/v1/templates',
                update: '/api/v1/templates/{id}',
                delete: '/api/v1/templates/{id}'
            },
            batch: {
                submit: '/api/v1/batch-jobs',
                status: '/api/v1/batch-jobs/{id}',
                stream: '/api/v1/batch-jobs/{id}/stream'
            },
            subscriptions: {
                plans: '/api/v1/subscriptions/plans',
                subscribe: '/api/v1/subscriptions/subscribe',
                usage: '/api/v1/subscriptions/usage'
            }
        }
    },
    
    // Authentication Configuration
    auth: {
        tokenKey: 'huskyapply_jwt_token',
        refreshTokenKey: 'huskyapply_refresh_token',
        userKey: 'huskyapply_user',
        tokenExpiredThreshold: 300000, // 5 minutes before expiry
        autoRefreshEnabled: true,
        logoutOnRefreshFailure: true,
        
        // Session configuration
        sessionTimeout: 86400000, // 24 hours
        inactivityTimeout: 3600000, // 1 hour
        extendSessionOnActivity: true
    },
    
    // File Upload Configuration
    uploads: {
        maxFileSize: 52428800, // 50MB
        allowedTypes: [
            'application/pdf',
            'application/msword',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'text/plain'
        ],
        allowedExtensions: ['.pdf', '.doc', '.docx', '.txt'],
        chunkSize: 1048576, // 1MB chunks for large files
        
        // S3 Upload Configuration
        s3: {
            presignedUrlExpiration: 3600, // 1 hour
            retryAttempts: 3,
            retryDelay: 2000 // 2 seconds
        }
    },
    
    // Real-time Communication (SSE)
    sse: {
        reconnectInterval: 5000, // 5 seconds
        maxReconnectAttempts: 10,
        heartbeatInterval: 30000, // 30 seconds
        connectionTimeout: 120000, // 2 minutes
        
        // Event types
        eventTypes: {
            status: 'status',
            progress: 'progress',
            completed: 'completed',
            error: 'error',
            heartbeat: 'heartbeat'
        }
    },
    
    // UI/UX Configuration
    ui: {
        // Theme Configuration
        theme: 'auto', // 'light', 'dark', 'auto'
        
        // Loading States
        loading: {
            showSpinner: true,
            minLoadingTime: 500, // Minimum time to show loading spinner
            spinnerDelay: 200 // Delay before showing spinner
        },
        
        // Notifications
        notifications: {
            duration: {
                success: 5000,
                error: 10000,
                warning: 7000,
                info: 5000
            },
            position: 'top-right',
            maxVisible: 5,
            dismissible: true
        },
        
        // Form Configuration
        forms: {
            autoSave: true,
            autoSaveInterval: 30000, // 30 seconds
            validateOnChange: true,
            showFieldErrors: true,
            submitTimeout: 60000 // 1 minute
        },
        
        // Pagination
        pagination: {
            defaultPageSize: 20,
            pageSizeOptions: [10, 20, 50, 100],
            showSizeSelector: true,
            showPageInfo: true
        },
        
        // Search
        search: {
            debounceDelay: 300, // 300ms
            minQueryLength: 2,
            highlightResults: true
        }
    },
    
    // Performance Configuration
    performance: {
        // Caching
        cache: {
            enabled: true,
            defaultTtl: 300000, // 5 minutes
            maxSize: 100, // Maximum cached items
            
            // Cache keys and TTLs
            items: {
                userProfile: 3600000, // 1 hour
                subscriptionPlans: 1800000, // 30 minutes
                dashboardStats: 300000, // 5 minutes
                templates: 600000 // 10 minutes
            }
        },
        
        // Lazy Loading
        lazyLoading: {
            enabled: true,
            threshold: 200, // Load when 200px from viewport
            rootMargin: '10px'
        },
        
        // Debouncing
        debounce: {
            search: 300,
            resize: 100,
            scroll: 16 // ~60fps
        },
        
        // Virtual Scrolling (for large lists)
        virtualScroll: {
            enabled: true,
            itemHeight: 60,
            overscan: 5
        }
    },
    
    // Security Configuration
    security: {
        // Content Security Policy
        csp: {
            enabled: true,
            directives: {
                'default-src': "'self'",
                'script-src': "'self' 'unsafe-inline' https://js.stripe.com",
                'style-src': "'self' 'unsafe-inline'",
                'img-src': "'self' data: https:",
                'connect-src': "'self' https://api.stripe.com",
                'frame-src': "https://js.stripe.com https://hooks.stripe.com"
            }
        },
        
        // XSS Protection
        xss: {
            sanitizeInput: true,
            escapeOutput: true,
            allowedTags: ['b', 'i', 'em', 'strong'],
            allowedAttributes: ['href', 'title']
        },
        
        // CSRF Protection
        csrf: {
            enabled: true,
            tokenHeaderName: 'X-CSRF-Token'
        },
        
        // Secure Storage
        storage: {
            preferSecure: true,
            encryptSensitiveData: true,
            keyRotationInterval: 604800000 // 1 week
        }
    },
    
    // Analytics and Monitoring
    analytics: {
        enabled: true,
        
        // Error Tracking
        errorTracking: {
            enabled: true,
            sampleRate: 1.0, // 100% in production for critical app
            maxBreadcrumbs: 50,
            
            // Sentry configuration (if used)
            sentry: {
                dsn: window.SENTRY_DSN || null,
                environment: 'production',
                release: window.APP_VERSION || '2.0.0'
            }
        },
        
        // Performance Monitoring
        performance: {
            enabled: true,
            sampleRate: 0.1, // 10% sampling
            
            // Core Web Vitals tracking
            webVitals: {
                enabled: true,
                reportThreshold: {
                    LCP: 2500, // Largest Contentful Paint
                    FID: 100,  // First Input Delay
                    CLS: 0.1   // Cumulative Layout Shift
                }
            }
        },
        
        // Custom Events
        customEvents: {
            enabled: true,
            trackUserActions: true,
            trackPageViews: true,
            trackApiCalls: true
        }
    },
    
    // Feature Flags
    features: {
        // New features can be toggled
        batchProcessing: true,
        templateManagement: true,
        darkMode: true,
        advancedSearch: true,
        realTimeNotifications: true,
        offlineSupport: false, // Not implemented yet
        
        // A/B Testing features
        newDashboardLayout: false,
        enhancedFileUpload: true,
        socialLogin: false
    },
    
    // Accessibility
    accessibility: {
        enabled: true,
        
        // ARIA support
        aria: {
            enabled: true,
            announcePageChanges: true,
            announceFormErrors: true,
            announceLoadingStates: true
        },
        
        // Keyboard navigation
        keyboard: {
            enabled: true,
            skipLinks: true,
            focusManagement: true,
            trapFocus: true
        },
        
        // Screen reader support
        screenReader: {
            enabled: true,
            liveRegions: true,
            descriptiveLabels: true
        },
        
        // High contrast mode
        highContrast: {
            supported: true,
            autoDetect: true
        }
    },
    
    // Internationalization
    i18n: {
        enabled: false, // Not implemented yet
        defaultLocale: 'en-US',
        supportedLocales: ['en-US'],
        fallbackLocale: 'en-US',
        
        // Date/time formatting
        dateFormat: 'MM/DD/YYYY',
        timeFormat: '12h',
        timezone: 'auto' // Detect user's timezone
    },
    
    // PWA Configuration
    pwa: {
        enabled: false, // Not implemented yet
        
        // Service Worker
        serviceWorker: {
            enabled: false,
            updateOnReload: true,
            skipWaiting: true
        },
        
        // Offline Support
        offline: {
            enabled: false,
            cacheStrategy: 'networkFirst',
            fallbackPage: '/offline.html'
        },
        
        // Push Notifications
        pushNotifications: {
            enabled: false,
            vapidPublicKey: null
        }
    },
    
    // Development and Testing
    development: {
        // Mock API (disabled in production)
        mockApi: false,
        
        // Debug logging (disabled in production)
        debugLogging: false,
        
        // Performance profiling
        profiling: false,
        
        // Testing utilities
        testing: {
            enabled: false,
            dataTestIds: false
        }
    },
    
    // Build Configuration
    build: {
        version: window.APP_VERSION || '2.0.0',
        buildTime: window.BUILD_TIME || new Date().toISOString(),
        gitCommit: window.GIT_COMMIT || 'unknown',
        
        // Asset optimization
        assets: {
            minifyJs: true,
            minifyCss: true,
            optimizeImages: true,
            enableSourceMaps: false
        }
    }
};

// Utility Functions
const ConfigUtils = {
    /**
     * Get API endpoint URL with parameters
     */
    getApiUrl(endpoint, params = {}) {
        let url = ProductionConfig.api.baseUrl + endpoint;
        
        // Replace path parameters
        Object.entries(params).forEach(([key, value]) => {
            url = url.replace(`{${key}}`, encodeURIComponent(value));
        });
        
        return url;
    },
    
    /**
     * Check if feature is enabled
     */
    isFeatureEnabled(featureName) {
        return ProductionConfig.features[featureName] === true;
    },
    
    /**
     * Get cache TTL for a specific item
     */
    getCacheTtl(itemType) {
        return ProductionConfig.performance.cache.items[itemType] || 
               ProductionConfig.performance.cache.defaultTtl;
    },
    
    /**
     * Check if file type is allowed for upload
     */
    isFileTypeAllowed(fileType, fileName) {
        const isTypeAllowed = ProductionConfig.uploads.allowedTypes.includes(fileType);
        const extension = fileName.toLowerCase().substring(fileName.lastIndexOf('.'));
        const isExtensionAllowed = ProductionConfig.uploads.allowedExtensions.includes(extension);
        
        return isTypeAllowed && isExtensionAllowed;
    },
    
    /**
     * Get notification duration based on type
     */
    getNotificationDuration(type) {
        return ProductionConfig.ui.notifications.duration[type] || 5000;
    },
    
    /**
     * Validate configuration on load
     */
    validateConfig() {
        const issues = [];
        
        // Check required environment variables
        if (!ProductionConfig.api.baseUrl) {
            issues.push('API base URL is not configured');
        }
        
        // Check feature dependencies
        if (ProductionConfig.features.batchProcessing && !ProductionConfig.features.templateManagement) {
            console.warn('Batch processing works better with template management enabled');
        }
        
        // Log validation results
        if (issues.length > 0) {
            console.error('Configuration validation failed:', issues);
        } else {
            console.info('Configuration validation passed');
        }
        
        return issues;
    }
};

// Initialize configuration validation
document.addEventListener('DOMContentLoaded', () => {
    ConfigUtils.validateConfig();
    
    // Set global configuration
    window.HuskyApplyConfig = ProductionConfig;
    window.ConfigUtils = ConfigUtils;
    
    // Set up CSP if enabled
    if (ProductionConfig.security.csp.enabled) {
        const meta = document.createElement('meta');
        meta.setAttribute('http-equiv', 'Content-Security-Policy');
        
        const directives = Object.entries(ProductionConfig.security.csp.directives)
            .map(([key, value]) => `${key} ${value}`)
            .join('; ');
        
        meta.setAttribute('content', directives);
        document.head.appendChild(meta);
    }
});

// Export for module usage
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { ProductionConfig, ConfigUtils };
}

// Global error handler for production
window.addEventListener('error', (event) => {
    if (ProductionConfig.analytics.errorTracking.enabled) {
        // Log error to monitoring service
        console.error('Global error caught:', {
            message: event.message,
            filename: event.filename,
            lineno: event.lineno,
            colno: event.colno,
            stack: event.error?.stack
        });
        
        // Send to error tracking service if configured
        if (ProductionConfig.analytics.errorTracking.sentry?.dsn) {
            // Sentry error reporting would go here
        }
    }
});

// Unhandled promise rejection handler
window.addEventListener('unhandledrejection', (event) => {
    if (ProductionConfig.analytics.errorTracking.enabled) {
        console.error('Unhandled promise rejection:', event.reason);
    }
});

// Performance observer for Core Web Vitals
if (ProductionConfig.analytics.performance.webVitals.enabled && 
    'PerformanceObserver' in window) {
    
    const observer = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
            if (ProductionConfig.analytics.performance.enabled) {
                console.info(`Performance metric: ${entry.name} = ${entry.value}`);
            }
        }
    });
    
    observer.observe({ type: 'measure', buffered: true });
    observer.observe({ type: 'paint', buffered: true });
    observer.observe({ type: 'largest-contentful-paint', buffered: true });
    observer.observe({ type: 'first-input', buffered: true });
    observer.observe({ type: 'layout-shift', buffered: true });
}