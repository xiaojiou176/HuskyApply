/**
 * Performance Monitoring Utility
 * Tracks bundle sizes, loading times, and user experience metrics
 */

export class PerformanceMonitor {
    constructor() {
        this.metrics = new Map();
        this.bundleSizes = new Map();
        this.startTime = performance.now();
        this.observers = new Map();
        
        this.init();
    }
    
    init() {
        this.setupObservers();
        this.trackBundleSizes();
        this.trackNavigationTiming();
        this.trackResourceTiming();
    }
    
    // ======================================
    // CORE WEB VITALS TRACKING
    // ======================================
    
    setupObservers() {
        if (!('PerformanceObserver' in window)) {
            console.warn('PerformanceObserver not supported');
            return;
        }
        
        // Largest Contentful Paint (LCP)
        this.observeMetric('largest-contentful-paint', (entries) => {
            const lcp = entries[entries.length - 1];
            this.recordMetric('lcp', lcp.startTime, {
                element: lcp.element?.tagName,
                url: lcp.url
            });
        });
        
        // First Input Delay (FID)
        this.observeMetric('first-input', (entries) => {
            const fid = entries[0];
            const delay = fid.processingStart - fid.startTime;
            this.recordMetric('fid', delay, {
                eventType: fid.name,
                target: fid.target?.tagName
            });
        });
        
        // Cumulative Layout Shift (CLS)
        let clsValue = 0;
        this.observeMetric('layout-shift', (entries) => {
            for (const entry of entries) {
                if (!entry.hadRecentInput) {
                    clsValue += entry.value;
                }
            }
            this.recordMetric('cls', clsValue);
        });
        
        // Time to First Byte (TTFB)
        this.observeMetric('navigation', (entries) => {
            const navigation = entries[0];
            const ttfb = navigation.responseStart - navigation.fetchStart;
            this.recordMetric('ttfb', ttfb);
        });
        
        // Long Tasks (blocking main thread)
        this.observeMetric('longtask', (entries) => {
            entries.forEach(entry => {
                this.recordMetric('long-task', entry.duration, {
                    startTime: entry.startTime,
                    attribution: entry.attribution?.[0]?.name
                });
            });
        });
    }
    
    observeMetric(type, callback) {
        try {
            const observer = new PerformanceObserver((list) => {
                callback(list.getEntries());
            });
            
            observer.observe({ entryTypes: [type] });
            this.observers.set(type, observer);
        } catch (error) {
            console.warn(`Cannot observe ${type}:`, error);
        }
    }
    
    // ======================================
    // BUNDLE SIZE TRACKING
    // ======================================
    
    trackBundleSizes() {
        // Track critical CSS
        this.measureResourceSize('/styles/critical.css', 'critical-css');
        
        // Track non-critical CSS
        this.measureResourceSize('/styles/non-critical.css', 'non-critical-css');
        
        // Track core JavaScript
        this.measureResourceSize('/core/app-core.js', 'app-core');
        this.measureResourceSize('/app-optimized.js', 'app-optimized');
        
        // Track modules as they load
        this.trackModuleLoading();
    }
    
    async measureResourceSize(url, name) {
        try {
            const response = await fetch(url, { method: 'HEAD' });
            const size = response.headers.get('Content-Length');
            
            if (size) {
                this.bundleSizes.set(name, {
                    url,
                    size: parseInt(size),
                    compressed: response.headers.get('Content-Encoding') !== null
                });
            }
        } catch (error) {
            console.warn(`Cannot measure size of ${url}:`, error);
        }
    }
    
    trackModuleLoading() {
        // Override dynamic import to track module sizes
        const originalImport = window.__esModule_import || ((...args) => import(...args));
        
        window.__esModule_import = async (...args) => {
            const startTime = performance.now();
            const moduleUrl = args[0];
            
            try {
                const module = await originalImport(...args);
                const loadTime = performance.now() - startTime;
                
                this.recordMetric('module-load', loadTime, {
                    url: moduleUrl,
                    timestamp: Date.now()
                });
                
                return module;
            } catch (error) {
                this.recordMetric('module-error', 0, {
                    url: moduleUrl,
                    error: error.message
                });
                throw error;
            }
        };
    }
    
    // ======================================
    // NAVIGATION & RESOURCE TIMING
    // ======================================
    
    trackNavigationTiming() {
        window.addEventListener('load', () => {
            setTimeout(() => {
                const navigation = performance.getEntriesByType('navigation')[0];
                if (!navigation) return;
                
                // Key timing metrics
                this.recordMetric('dom-content-loaded', navigation.domContentLoadedEventEnd - navigation.fetchStart);
                this.recordMetric('load-complete', navigation.loadEventEnd - navigation.fetchStart);
                this.recordMetric('dns-lookup', navigation.domainLookupEnd - navigation.domainLookupStart);
                this.recordMetric('tcp-connect', navigation.connectEnd - navigation.connectStart);
                this.recordMetric('request-response', navigation.responseEnd - navigation.requestStart);
                this.recordMetric('dom-processing', navigation.domComplete - navigation.responseEnd);
                
                // Connection info
                if (navigator.connection) {
                    this.recordMetric('connection-type', 0, {
                        effectiveType: navigator.connection.effectiveType,
                        downlink: navigator.connection.downlink,
                        rtt: navigator.connection.rtt
                    });
                }
            }, 0);
        });
    }
    
    trackResourceTiming() {
        // Track critical resources
        const criticalResources = [
            'critical.css',
            'app-core.js',
            'app-optimized.js'
        ];
        
        setTimeout(() => {
            const resources = performance.getEntriesByType('resource');
            
            resources.forEach(resource => {
                const isCritical = criticalResources.some(name => 
                    resource.name.includes(name)
                );
                
                if (isCritical) {
                    this.recordMetric('critical-resource-load', resource.duration, {
                        name: resource.name,
                        size: resource.transferSize,
                        cached: resource.transferSize === 0
                    });
                }
            });
        }, 1000);
    }
    
    // ======================================
    // CODE SPLITTING METRICS
    // ======================================
    
    trackCodeSplitting() {
        const splitPoints = new Map();
        let totalBundleSize = 0;
        let loadedBundleSize = 0;
        
        // Track module loading
        if (window.HuskyApplyCore?.ModuleLoader) {
            const originalLoadModule = window.HuskyApplyCore.ModuleLoader.loadModule;
            
            window.HuskyApplyCore.ModuleLoader.loadModule = async function(moduleName, options) {
                const startTime = performance.now();
                
                try {
                    const module = await originalLoadModule.call(this, moduleName, options);
                    const loadTime = performance.now() - startTime;
                    
                    // Record split point metrics
                    splitPoints.set(moduleName, {
                        loadTime,
                        timestamp: Date.now(),
                        options
                    });
                    
                    console.log(`📊 Module ${moduleName} loaded in ${loadTime.toFixed(2)}ms`);
                    
                    return module;
                } catch (error) {
                    console.error(`❌ Module ${moduleName} failed to load:`, error);
                    throw error;
                }
            };
        }
        
        return {
            getSplitPointMetrics: () => Object.fromEntries(splitPoints),
            getBundleUtilization: () => ({
                total: totalBundleSize,
                loaded: loadedBundleSize,
                utilization: totalBundleSize > 0 ? loadedBundleSize / totalBundleSize : 0
            })
        };
    }
    
    // ======================================
    // USER EXPERIENCE METRICS
    // ======================================
    
    trackUserExperience() {
        // Time to Interactive (approximate)
        let tti = null;
        
        // Measure when main thread becomes idle
        const checkMainThreadIdle = () => {
            const now = performance.now();
            if (!tti) {
                // Simple heuristic: 5 seconds after load with no long tasks
                setTimeout(() => {
                    tti = performance.now();
                    this.recordMetric('tti', tti);
                }, 5000);
            }
        };
        
        if (document.readyState === 'complete') {
            checkMainThreadIdle();
        } else {
            window.addEventListener('load', checkMainThreadIdle);
        }
        
        // Track user interactions
        let firstInteraction = null;
        const interactionEvents = ['click', 'keydown', 'scroll', 'touch'];
        
        const trackFirstInteraction = (event) => {
            if (!firstInteraction) {
                firstInteraction = performance.now();
                this.recordMetric('first-interaction', firstInteraction, {
                    type: event.type,
                    target: event.target?.tagName
                });
                
                // Remove listeners after first interaction
                interactionEvents.forEach(eventType => {
                    document.removeEventListener(eventType, trackFirstInteraction);
                });
            }
        };
        
        interactionEvents.forEach(eventType => {
            document.addEventListener(eventType, trackFirstInteraction, { once: true });
        });
    }
    
    // ======================================
    // UTILITY METHODS
    // ======================================
    
    recordMetric(name, value, metadata = {}) {
        const metric = {
            name,
            value,
            timestamp: Date.now(),
            metadata
        };
        
        this.metrics.set(name, metric);
        
        // Log significant metrics
        if (['lcp', 'fid', 'cls', 'ttfb'].includes(name)) {
            console.log(`📈 ${name.toUpperCase()}: ${value.toFixed(2)}${name === 'cls' ? '' : 'ms'}`);
        }
        
        // Store in session storage for debugging
        this.persistMetrics();
    }
    
    persistMetrics() {
        try {
            const metricsData = {
                metrics: Object.fromEntries(this.metrics),
                bundleSizes: Object.fromEntries(this.bundleSizes),
                timestamp: Date.now()
            };
            
            sessionStorage.setItem('huskyapply_performance', JSON.stringify(metricsData));
        } catch (error) {
            // Storage quota exceeded or other error
        }
    }
    
    // ======================================
    // REPORTING & ANALYSIS
    // ======================================
    
    getReport() {
        const report = {
            overview: this.getOverview(),
            coreWebVitals: this.getCoreWebVitals(),
            bundleAnalysis: this.getBundleAnalysis(),
            loadingPerformance: this.getLoadingPerformance(),
            splitPointAnalysis: this.getSplitPointAnalysis()
        };
        
        return report;
    }
    
    getOverview() {
        const totalTime = performance.now() - this.startTime;
        const criticalMetrics = ['lcp', 'fid', 'cls', 'ttfb'];
        
        return {
            sessionDuration: totalTime,
            metricsCollected: this.metrics.size,
            criticalMetrics: criticalMetrics.reduce((acc, metric) => {
                if (this.metrics.has(metric)) {
                    acc[metric] = this.metrics.get(metric).value;
                }
                return acc;
            }, {}),
            timestamp: new Date().toISOString()
        };
    }
    
    getCoreWebVitals() {
        const thresholds = {
            lcp: { good: 2500, poor: 4000 },
            fid: { good: 100, poor: 300 },
            cls: { good: 0.1, poor: 0.25 },
            ttfb: { good: 200, poor: 600 }
        };
        
        return Object.keys(thresholds).reduce((acc, metric) => {
            if (this.metrics.has(metric)) {
                const value = this.metrics.get(metric).value;
                const threshold = thresholds[metric];
                let score = 'good';
                
                if (value > threshold.poor) {
                    score = 'poor';
                } else if (value > threshold.good) {
                    score = 'needs-improvement';
                }
                
                acc[metric] = { value, score, threshold };
            }
            return acc;
        }, {});
    }
    
    getBundleAnalysis() {
        const bundles = Object.fromEntries(this.bundleSizes);
        const totalSize = Object.values(bundles).reduce((sum, bundle) => sum + bundle.size, 0);
        
        return {
            bundles,
            totalSize,
            totalSizeFormatted: this.formatBytes(totalSize),
            compression: Object.values(bundles).filter(b => b.compressed).length / Object.values(bundles).length
        };
    }
    
    getLoadingPerformance() {
        const loadingMetrics = [
            'dom-content-loaded',
            'load-complete',
            'tti',
            'first-interaction'
        ];
        
        return loadingMetrics.reduce((acc, metric) => {
            if (this.metrics.has(metric)) {
                acc[metric] = this.metrics.get(metric).value;
            }
            return acc;
        }, {});
    }
    
    getSplitPointAnalysis() {
        const moduleMetrics = Array.from(this.metrics.values())
            .filter(m => m.name === 'module-load')
            .map(m => ({
                url: m.metadata.url,
                loadTime: m.value,
                timestamp: m.metadata.timestamp
            }));
        
        return {
            modulesLoaded: moduleMetrics.length,
            averageLoadTime: moduleMetrics.reduce((sum, m) => sum + m.loadTime, 0) / moduleMetrics.length || 0,
            modules: moduleMetrics
        };
    }
    
    formatBytes(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
    
    // ======================================
    // EXPORT & SHARING
    // ======================================
    
    exportReport() {
        const report = this.getReport();
        const blob = new Blob([JSON.stringify(report, null, 2)], {
            type: 'application/json'
        });
        
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `huskyapply-performance-${Date.now()}.json`;
        link.click();
        
        URL.revokeObjectURL(url);
    }
    
    logReport() {
        console.group('🚀 HuskyApply Performance Report');
        
        const report = this.getReport();
        
        console.log('📊 Overview:', report.overview);
        console.log('⚡ Core Web Vitals:', report.coreWebVitals);
        console.log('📦 Bundle Analysis:', report.bundleAnalysis);
        console.log('🔄 Loading Performance:', report.loadingPerformance);
        console.log('📂 Code Splitting:', report.splitPointAnalysis);
        
        console.groupEnd();
        
        return report;
    }
    
    // ======================================
    // CLEANUP
    // ======================================
    
    destroy() {
        this.observers.forEach(observer => observer.disconnect());
        this.observers.clear();
        this.metrics.clear();
        this.bundleSizes.clear();
    }
}

// Global instance
export const performanceMonitor = new PerformanceMonitor();

// Export for debugging
window.HuskyApplyPerformance = performanceMonitor;