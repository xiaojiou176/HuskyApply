/**
 * User Analytics Module - Enhanced User Behavior Tracking
 * 
 * Tracks detailed user interactions, conversion funnels, and user experience metrics:
 * - Form completion rates and abandonment points
 * - Feature usage and engagement patterns
 * - Performance metrics and error tracking
 * - A/B testing support for UI improvements
 */

class UserAnalyticsModule {
    constructor() {
        this.core = window.HuskyApply;
        this.initialized = false;
        this.sessionId = this.generateSessionId();
        this.sessionStartTime = Date.now();
        this.pageLoadTime = null;
        this.interactions = [];
        this.conversionFunnel = {
            pageView: false,
            formStart: false,
            jobUrlEntered: false,
            resumeUploaded: false,
            modelSelected: false,
            formSubmitted: false,
            resultViewed: false,
            resultActioned: false
        };
        this.performanceMetrics = {
            pageLoadTime: 0,
            timeToFirstInteraction: 0,
            formCompletionTime: 0,
            errorCount: 0,
            helpUsage: 0
        };
        this.userBehaviorPatterns = {
            scrollDepth: 0,
            timeOnPage: 0,
            clickHeatmap: {},
            formFieldFocusTime: {},
            abandonmentPoints: []
        };
    }

    async init() {
        if (this.initialized) return;
        
        console.log('Initializing User Analytics module...');
        
        this.setupPageTracking();
        this.setupInteractionTracking();
        this.setupFormAnalytics();
        this.setupPerformanceMonitoring();
        this.setupConversionTracking();
        this.setupErrorTracking();
        this.startSessionTracking();
        
        this.initialized = true;
        console.log('User Analytics module initialized');
    }

    generateSessionId() {
        return 'session_' + Date.now() + '_' + Math.random().toString(36).substring(2, 11);
    }

    setupPageTracking() {
        // Track page load
        window.addEventListener('load', () => {
            this.pageLoadTime = Date.now() - this.sessionStartTime;
            this.performanceMetrics.pageLoadTime = this.pageLoadTime;
            this.conversionFunnel.pageView = true;
            
            this.trackEvent('page_loaded', {
                load_time: this.pageLoadTime,
                session_id: this.sessionId,
                user_agent: navigator.userAgent,
                viewport: `${window.innerWidth}x${window.innerHeight}`,
                referrer: document.referrer
            });
        });

        // Track page visibility changes
        document.addEventListener('visibilitychange', () => {
            this.trackEvent('page_visibility_change', {
                hidden: document.hidden,
                timestamp: Date.now() - this.sessionStartTime
            });
        });

        // Track page unload
        window.addEventListener('beforeunload', () => {
            this.flushAnalytics();
        });

        // Track scroll depth
        this.setupScrollTracking();
    }

    setupScrollTracking() {
        let maxScrollDepth = 0;
        const trackScrollDepth = this.core.throttle(() => {
            const scrollTop = window.pageYOffset;
            const docHeight = document.documentElement.scrollHeight - window.innerHeight;
            const scrollPercentage = Math.round((scrollTop / docHeight) * 100);
            
            if (scrollPercentage > maxScrollDepth) {
                maxScrollDepth = scrollPercentage;
                this.userBehaviorPatterns.scrollDepth = maxScrollDepth;
                
                // Track milestone scroll depths
                if (maxScrollDepth >= 25 && !this.scrollMilestones?.quarter) {
                    this.trackEvent('scroll_milestone', { depth: 25 });
                    this.scrollMilestones = { ...this.scrollMilestones, quarter: true };
                }
                if (maxScrollDepth >= 50 && !this.scrollMilestones?.half) {
                    this.trackEvent('scroll_milestone', { depth: 50 });
                    this.scrollMilestones = { ...this.scrollMilestones, half: true };
                }
                if (maxScrollDepth >= 75 && !this.scrollMilestones?.threeQuarter) {
                    this.trackEvent('scroll_milestone', { depth: 75 });
                    this.scrollMilestones = { ...this.scrollMilestones, threeQuarter: true };
                }
                if (maxScrollDepth >= 90 && !this.scrollMilestones?.near_end) {
                    this.trackEvent('scroll_milestone', { depth: 90 });
                    this.scrollMilestones = { ...this.scrollMilestones, near_end: true };
                }
            }
        }, 250);

        window.addEventListener('scroll', trackScrollDepth);
        this.scrollMilestones = {};
    }

    setupInteractionTracking() {
        // Track all clicks with heat map data
        document.addEventListener('click', (e) => {
            const target = e.target;
            const rect = target.getBoundingClientRect();
            const clickData = {
                element: target.tagName.toLowerCase(),
                id: target.id || null,
                class: target.className || null,
                text: target.textContent?.substring(0, 50) || null,
                x: e.clientX,
                y: e.clientY,
                viewport_x: rect.left,
                viewport_y: rect.top,
                timestamp: Date.now() - this.sessionStartTime
            };

            this.interactions.push(clickData);
            this.updateClickHeatmap(clickData);
            
            // Track first interaction
            if (!this.performanceMetrics.timeToFirstInteraction) {
                this.performanceMetrics.timeToFirstInteraction = clickData.timestamp;
                this.trackEvent('first_interaction', {
                    time_to_interaction: clickData.timestamp,
                    element: clickData.element
                });
            }

            this.trackEvent('element_click', clickData);

            // Track specific UI element interactions
            this.trackSpecificInteractions(target, clickData);
        });

        // Track form interactions
        document.addEventListener('focus', (e) => {
            if (e.target.matches('input, select, textarea')) {
                const fieldId = e.target.id || e.target.name || 'unknown';
                this.userBehaviorPatterns.formFieldFocusTime[fieldId] = Date.now();
                
                this.trackEvent('form_field_focus', {
                    field: fieldId,
                    field_type: e.target.type,
                    timestamp: Date.now() - this.sessionStartTime
                });
            }
        }, true);

        document.addEventListener('blur', (e) => {
            if (e.target.matches('input, select, textarea')) {
                const fieldId = e.target.id || e.target.name || 'unknown';
                const focusStartTime = this.userBehaviorPatterns.formFieldFocusTime[fieldId];
                
                if (focusStartTime) {
                    const focusDuration = Date.now() - focusStartTime;
                    delete this.userBehaviorPatterns.formFieldFocusTime[fieldId];
                    
                    this.trackEvent('form_field_blur', {
                        field: fieldId,
                        focus_duration: focusDuration,
                        value_length: e.target.value?.length || 0,
                        filled: !!e.target.value
                    });
                }
            }
        }, true);
    }

    updateClickHeatmap(clickData) {
        const key = `${Math.floor(clickData.x / 50)}_${Math.floor(clickData.y / 50)}`;
        if (!this.userBehaviorPatterns.clickHeatmap[key]) {
            this.userBehaviorPatterns.clickHeatmap[key] = 0;
        }
        this.userBehaviorPatterns.clickHeatmap[key]++;
    }

    trackSpecificInteractions(target, clickData) {
        // Track button clicks
        if (target.matches('button, .btn')) {
            this.trackEvent('button_click', {
                button_text: target.textContent?.trim(),
                button_type: target.type || 'button',
                form_id: target.closest('form')?.id || null
            });
        }

        // Track navigation clicks
        if (target.matches('a, [role="button"]')) {
            this.trackEvent('navigation_click', {
                link_text: target.textContent?.trim(),
                href: target.href || null
            });
        }

        // Track help interactions
        if (target.matches('.help-floating-btn, .contextual-tooltip, [data-help]')) {
            this.performanceMetrics.helpUsage++;
            this.trackEvent('help_interaction', {
                help_type: target.className,
                help_content: target.dataset.help || null
            });
        }

        // Track trial mode interactions
        if (target.matches('[class*="trial"], [data-trial-feature]')) {
            this.trackEvent('trial_feature_interaction', {
                feature: target.dataset.trialFeature || target.className,
                trial_stage: this.getTrialStage()
            });
        }
    }

    setupFormAnalytics() {
        const form = document.getElementById('jobForm');
        if (!form) return;

        // Track form start
        const firstFormInteraction = (e) => {
            if (!this.conversionFunnel.formStart) {
                this.conversionFunnel.formStart = true;
                this.trackEvent('form_start', {
                    first_field: e.target.id || e.target.name,
                    time_on_page: Date.now() - this.sessionStartTime
                });
                
                // Remove listener after first interaction
                form.removeEventListener('focus', firstFormInteraction, true);
            }
        };

        form.addEventListener('focus', firstFormInteraction, true);

        // Track form field changes
        form.addEventListener('change', (e) => {
            const field = e.target;
            this.trackFormFieldChange(field);
        });

        // Track form validation errors
        form.addEventListener('invalid', (e) => {
            this.performanceMetrics.errorCount++;
            this.trackEvent('form_validation_error', {
                field: e.target.id || e.target.name,
                error_type: e.target.validationMessage,
                field_value_length: e.target.value?.length || 0
            });
        });

        // Track form submission attempt
        form.addEventListener('submit', (e) => {
            this.conversionFunnel.formSubmitted = true;
            this.performanceMetrics.formCompletionTime = Date.now() - this.sessionStartTime;
            
            this.trackEvent('form_submit_attempt', {
                completion_time: this.performanceMetrics.formCompletionTime,
                form_valid: form.checkValidity()
            });
        });
    }

    trackFormFieldChange(field) {
        const fieldId = field.id || field.name || 'unknown';
        const fieldValue = field.value;

        // Update conversion funnel
        if (fieldId === 'jdUrlInput' && fieldValue) {
            this.conversionFunnel.jobUrlEntered = true;
            this.trackEvent('job_url_entered', { url_length: fieldValue.length });
        } else if (fieldId === 'resumeFileInput' && field.files?.length > 0) {
            this.conversionFunnel.resumeUploaded = true;
            const file = field.files[0];
            this.trackEvent('resume_uploaded', {
                file_size: file.size,
                file_type: file.type,
                file_name_length: file.name.length
            });
        } else if (fieldId === 'modelProvider' && fieldValue) {
            this.conversionFunnel.modelSelected = true;
            this.trackEvent('ai_model_selected', { model: fieldValue });
        }

        // Track field completion
        this.trackEvent('form_field_change', {
            field: fieldId,
            field_type: field.type,
            has_value: !!fieldValue,
            value_length: fieldValue?.length || 0
        });
    }

    setupPerformanceMonitoring() {
        // Track resource loading performance
        if ('PerformanceObserver' in window) {
            const observer = new PerformanceObserver((list) => {
                for (const entry of list.getEntries()) {
                    if (entry.entryType === 'resource') {
                        // Track slow resources
                        if (entry.duration > 1000) {
                            this.trackEvent('slow_resource_load', {
                                resource: entry.name,
                                duration: entry.duration,
                                type: entry.initiatorType
                            });
                        }
                    } else if (entry.entryType === 'measure') {
                        this.trackEvent('custom_performance_measure', {
                            name: entry.name,
                            duration: entry.duration
                        });
                    }
                }
            });
            
            observer.observe({ entryTypes: ['resource', 'measure'] });
        }

        // Track JavaScript errors
        window.addEventListener('error', (e) => {
            this.performanceMetrics.errorCount++;
            this.trackEvent('javascript_error', {
                message: e.message,
                filename: e.filename,
                line: e.lineno,
                column: e.colno,
                stack: e.error?.stack?.substring(0, 500) || null
            });
        });

        // Track memory usage (if available)
        if ('memory' in performance) {
            const trackMemory = () => {
                this.trackEvent('memory_usage', {
                    used: performance.memory.usedJSHeapSize,
                    total: performance.memory.totalJSHeapSize,
                    limit: performance.memory.jsHeapSizeLimit
                });
            };

            // Track memory every 30 seconds
            setInterval(trackMemory, 30000);
        }
    }

    setupConversionTracking() {
        // Track results section visibility
        const observeResults = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting && !this.conversionFunnel.resultViewed) {
                    this.conversionFunnel.resultViewed = true;
                    this.trackEvent('result_viewed', {
                        time_to_result: Date.now() - this.sessionStartTime
                    });
                }
            });
        });

        // Wait for results section to be added to DOM
        const checkForResults = () => {
            const resultsSection = document.getElementById('resultsSection');
            if (resultsSection) {
                observeResults.observe(resultsSection);
            } else {
                setTimeout(checkForResults, 1000);
            }
        };
        checkForResults();

        // Track result actions
        document.addEventListener('click', (e) => {
            const target = e.target;
            if (target.matches('#copyBtn, #downloadBtn, #editBtn, #regenerateBtn')) {
                if (!this.conversionFunnel.resultActioned) {
                    this.conversionFunnel.resultActioned = true;
                }
                
                this.trackEvent('result_action', {
                    action: target.id.replace('Btn', ''),
                    time_to_action: Date.now() - this.sessionStartTime
                });
            }
        });
    }

    setupErrorTracking() {
        // Track API errors
        this.core.on('api-error', (event) => {
            this.performanceMetrics.errorCount++;
            this.trackEvent('api_error', {
                endpoint: event.detail.endpoint || 'unknown',
                status: event.detail.status || 'unknown',
                error_message: event.detail.message || 'unknown'
            });
        });

        // Track network errors
        window.addEventListener('offline', () => {
            this.trackEvent('network_status', { online: false });
        });

        window.addEventListener('online', () => {
            this.trackEvent('network_status', { online: true });
        });

        // Track unhandled promise rejections
        window.addEventListener('unhandledrejection', (e) => {
            this.performanceMetrics.errorCount++;
            this.trackEvent('unhandled_promise_rejection', {
                reason: e.reason?.toString() || 'unknown',
                stack: e.reason?.stack?.substring(0, 500) || null
            });
        });
    }

    startSessionTracking() {
        // Track session duration every 30 seconds
        this.sessionTimer = setInterval(() => {
            const sessionDuration = Date.now() - this.sessionStartTime;
            this.userBehaviorPatterns.timeOnPage = sessionDuration;
            
            this.trackEvent('session_heartbeat', {
                duration: sessionDuration,
                interactions_count: this.interactions.length,
                errors_count: this.performanceMetrics.errorCount
            });
        }, 30000);

        // Track idle time
        let lastActivity = Date.now();
        const updateActivity = () => {
            lastActivity = Date.now();
        };

        ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'].forEach(event => {
            document.addEventListener(event, updateActivity, { passive: true });
        });

        setInterval(() => {
            const idleTime = Date.now() - lastActivity;
            if (idleTime > 60000) { // 1 minute idle
                this.trackEvent('user_idle', {
                    idle_duration: idleTime,
                    session_duration: Date.now() - this.sessionStartTime
                });
            }
        }, 60000);
    }

    trackEvent(eventName, eventData = {}) {
        const event = {
            event: eventName,
            session_id: this.sessionId,
            timestamp: new Date().toISOString(),
            page_url: window.location.href,
            user_agent: navigator.userAgent,
            viewport: `${window.innerWidth}x${window.innerHeight}`,
            ...eventData
        };

        // Add to local queue
        this.interactions.push(event);

        // Send to core analytics system
        this.core.logMetric(eventName, 1, eventData);

        console.log('Analytics Event:', eventName, eventData);
    }

    getTrialStage() {
        // Determine current trial stage based on conversion funnel
        if (!this.conversionFunnel.formStart) return 'landing';
        if (!this.conversionFunnel.jobUrlEntered) return 'form_start';
        if (!this.conversionFunnel.resumeUploaded) return 'job_url_entered';
        if (!this.conversionFunnel.modelSelected) return 'resume_uploaded';
        if (!this.conversionFunnel.formSubmitted) return 'model_selected';
        if (!this.conversionFunnel.resultViewed) return 'form_submitted';
        if (!this.conversionFunnel.resultActioned) return 'result_viewed';
        return 'completed';
    }

    getConversionRate() {
        const steps = Object.values(this.conversionFunnel);
        const completedSteps = steps.filter(step => step).length;
        return completedSteps / steps.length;
    }

    getEngagementScore() {
        const timeOnPage = this.userBehaviorPatterns.timeOnPage;
        const interactions = this.interactions.length;
        const scrollDepth = this.userBehaviorPatterns.scrollDepth;
        const helpUsage = this.performanceMetrics.helpUsage;
        
        // Calculate engagement score (0-100)
        let score = 0;
        
        // Time on page (max 30 points)
        score += Math.min(timeOnPage / 60000 * 10, 30); // 3 minutes = max points
        
        // Interactions (max 25 points)
        score += Math.min(interactions * 2, 25);
        
        // Scroll depth (max 25 points)
        score += Math.min(scrollDepth / 4, 25);
        
        // Help usage indicates engagement (max 20 points)
        score += Math.min(helpUsage * 5, 20);
        
        return Math.round(score);
    }

    getPerformanceReport() {
        return {
            session: {
                id: this.sessionId,
                duration: Date.now() - this.sessionStartTime,
                interactions: this.interactions.length
            },
            conversion: {
                funnel: this.conversionFunnel,
                rate: this.getConversionRate()
            },
            performance: this.performanceMetrics,
            behavior: {
                ...this.userBehaviorPatterns,
                engagement_score: this.getEngagementScore()
            }
        };
    }

    // A/B Testing Support
    getTestVariant(testName) {
        // Simple hash-based variant assignment
        const hash = this.simpleHash(this.sessionId + testName);
        return hash % 2 === 0 ? 'A' : 'B';
    }

    trackTestExposure(testName, variant, feature) {
        this.trackEvent('ab_test_exposure', {
            test_name: testName,
            variant: variant,
            feature: feature
        });
    }

    simpleHash(str) {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash; // Convert to 32-bit integer
        }
        return Math.abs(hash);
    }

    async flushAnalytics() {
        if (this.interactions.length === 0) return;

        try {
            // In a real implementation, send to analytics service
            const analyticsData = {
                session: this.getPerformanceReport(),
                events: this.interactions.slice(-100), // Last 100 events
                timestamp: new Date().toISOString()
            };

            console.log('Flushing analytics data:', analyticsData);
            
            // Send to backend analytics endpoint
            // await fetch('/api/analytics', {
            //     method: 'POST',
            //     headers: { 'Content-Type': 'application/json' },
            //     body: JSON.stringify(analyticsData)
            // });

            // Clear local data
            this.interactions = [];
            
        } catch (error) {
            console.error('Failed to flush analytics:', error);
        }
    }

    // Public methods for external use
    trackCustomEvent(eventName, eventData) {
        this.trackEvent(`custom_${eventName}`, eventData);
    }

    setUserProperty(property, value) {
        this.trackEvent('user_property_set', {
            property: property,
            value: value
        });
    }

    trackFeatureUsage(feature, details = {}) {
        this.trackEvent('feature_usage', {
            feature: feature,
            ...details
        });
    }

    destroy() {
        if (this.sessionTimer) {
            clearInterval(this.sessionTimer);
        }
        
        this.flushAnalytics();
        this.initialized = false;
    }
}

// Export for dynamic loading
export { UserAnalyticsModule };
export const init = async () => {
    const userAnalytics = new UserAnalyticsModule();
    await userAnalytics.init();
    
    // Make available globally for external usage
    window.HuskyApplyAnalytics = userAnalytics;
    
    return userAnalytics;
};