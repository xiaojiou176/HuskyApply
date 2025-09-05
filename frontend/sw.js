/**
 * HuskyApply Service Worker
 * Provides offline functionality, caching, and performance optimization
 */

const CACHE_NAME = 'huskyapply-v2.1.0';
const STATIC_CACHE_NAME = 'huskyapply-static-v2.1';
const DYNAMIC_CACHE_NAME = 'huskyapply-dynamic-v2.1';
const MODULE_CACHE_NAME = 'huskyapply-modules-v2.1';
const IMAGES_CACHE_NAME = 'huskyapply-images-v2.1';
const PWA_CACHE_NAME = 'huskyapply-pwa-v2.1';

// Critical resources to cache immediately
const CRITICAL_ASSETS = [
    '/',
    '/index.html',
    '/index-split.html',
    '/login.html',
    '/styles/critical.css',
    '/core/app-core.js',
    '/app-optimized.js'
];

// Secondary resources to cache after critical loading
const SECONDARY_ASSETS = [
    '/styles/non-critical.css',
    '/styles/components.css',
    '/dashboard.html',
    '/templates.html',
    '/auth.js',
    '/templates.js',
    '/config/config.production.js'
];

// Module resources for lazy loading
const MODULE_ASSETS = [
    '/core/modules/auth.js',
    '/core/modules/form.js',
    '/core/modules/processing.js',
    '/core/modules/notifications.js',
    '/core/modules/ui.js',
    '/core/modules/dashboard.js',
    '/core/modules/templates.js'
];

// External resources
const EXTERNAL_ASSETS = [
    'https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap',
    'https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600&display=swap'
];

// API endpoints to cache (with limited duration)
const CACHE_API_PATTERNS = [
    /\/api\/v1\/dashboard\/stats/,
    /\/api\/v1\/templates/,
    /\/api\/v1\/subscriptions\/plans/
];

// Resources that should always be fetched from network
const NETWORK_ONLY_PATTERNS = [
    /\/api\/v1\/applications/,
    /\/api\/v1\/uploads/,
    /\/api\/v1\/auth/,
    /\/api\/v1\/internal/
];

// Maximum age for different types of cache (in milliseconds)
const CACHE_EXPIRY = {
    static: 7 * 24 * 60 * 60 * 1000, // 7 days
    api: 5 * 60 * 1000, // 5 minutes
    dynamic: 24 * 60 * 60 * 1000 // 1 day
};

/**
 * Install event - cache critical assets first, then secondary
 */
self.addEventListener('install', event => {
    console.log('SW: Installing service worker v2.0.0...');
    
    event.waitUntil(
        Promise.all([
            // Phase 1: Cache critical assets immediately
            caches.open(STATIC_CACHE_NAME).then(async cache => {
                console.log('SW: Caching critical assets...');
                try {
                    await cache.addAll(CRITICAL_ASSETS);
                    console.log('SW: Critical assets cached successfully');
                } catch (error) {
                    console.warn('SW: Some critical assets failed to cache:', error);
                    // Cache individually to avoid failing the entire set
                    for (const asset of CRITICAL_ASSETS) {
                        try {
                            await cache.add(asset);
                        } catch (err) {
                            console.warn(`SW: Failed to cache ${asset}:`, err);
                        }
                    }
                }
            }),
            
            // Phase 2: Cache modules separately
            caches.open(MODULE_CACHE_NAME).then(async cache => {
                console.log('SW: Pre-caching modules...');
                for (const module of MODULE_ASSETS) {
                    try {
                        await cache.add(module);
                    } catch (err) {
                        console.warn(`SW: Failed to cache module ${module}:`, err);
                    }
                }
            }),
            
            // Skip waiting to activate immediately
            self.skipWaiting()
        ]).then(() => {
            // Phase 3: Cache secondary assets in the background
            self.postMessage({
                type: 'CACHE_SECONDARY_ASSETS',
                assets: [...SECONDARY_ASSETS, ...EXTERNAL_ASSETS]
            });
        })
    );
});

/**
 * Activate event - clean up old caches
 */
self.addEventListener('activate', event => {
    console.log('SW: Activating service worker...');
    
    event.waitUntil(
        Promise.all([
            // Clean up old caches
            caches.keys().then(cacheNames => {
                return Promise.all(
                    cacheNames.map(cacheName => {
                        if (cacheName !== STATIC_CACHE_NAME && 
                            cacheName !== DYNAMIC_CACHE_NAME &&
                            cacheName.startsWith('huskyapply-')) {
                            console.log('SW: Deleting old cache:', cacheName);
                            return caches.delete(cacheName);
                        }
                    })
                );
            }),
            // Claim all clients
            self.clients.claim()
        ])
    );
});

/**
 * Fetch event - handle network requests
 */
self.addEventListener('fetch', event => {
    const { request } = event;
    const { url, method } = request;
    
    // Only handle GET requests
    if (method !== 'GET') {
        return;
    }
    
    // Handle different types of requests
    if (isStaticAsset(url)) {
        event.respondWith(handleStaticAsset(request));
    } else if (isApiRequest(url)) {
        event.respondWith(handleApiRequest(request));
    } else if (isNetworkOnlyRequest(url)) {
        event.respondWith(handleNetworkOnlyRequest(request));
    } else {
        event.respondWith(handleDynamicRequest(request));
    }
});

/**
 * Message event - handle messages from main thread
 */
self.addEventListener('message', event => {
    const { type, payload } = event.data;
    
    switch (type) {
        case 'CACHE_JOB_RESULT':
            cacheJobResult(payload);
            break;
        case 'CLEAR_CACHE':
            clearCache(payload?.cacheType);
            break;
        case 'UPDATE_CACHE':
            updateCache();
            break;
        case 'GET_CACHE_INFO':
            getCacheInfo().then(info => {
                event.ports[0].postMessage(info);
            });
            break;
        default:
            console.log('SW: Unknown message type:', type);
    }
});

/**
 * Background sync for offline job submissions
 */
self.addEventListener('sync', event => {
    if (event.tag === 'job-submission') {
        event.waitUntil(handleOfflineJobSubmission());
    }
});

/**
 * Push event for notifications
 */
self.addEventListener('push', event => {
    if (event.data) {
        const data = event.data.json();
        event.waitUntil(showNotification(data));
    }
});

/**
 * Notification click handler
 */
self.addEventListener('notificationclick', event => {
    event.notification.close();
    
    event.waitUntil(
        clients.openWindow(event.notification.data?.url || '/')
    );
});

// ======================================
// HELPER FUNCTIONS
// ======================================

/**
 * Check if URL is a static asset
 */
function isStaticAsset(url) {
    return STATIC_ASSETS.some(asset => url.includes(asset)) ||
           url.includes('.css') ||
           url.includes('.js') ||
           url.includes('.woff') ||
           url.includes('.woff2') ||
           url.includes('fonts.googleapis.com');
}

/**
 * Check if URL is an API request
 */
function isApiRequest(url) {
    return url.includes('/api/v1/') &&
           !isNetworkOnlyRequest(url);
}

/**
 * Check if URL should always be fetched from network
 */
function isNetworkOnlyRequest(url) {
    return NETWORK_ONLY_PATTERNS.some(pattern => pattern.test(url));
}

/**
 * Check if cached response is still fresh
 */
function isCacheFresh(cachedResponse, maxAge) {
    if (!cachedResponse) return false;
    
    const cachedDate = cachedResponse.headers.get('sw-cache-date');
    if (!cachedDate) return false;
    
    const age = Date.now() - parseInt(cachedDate);
    return age < maxAge;
}

/**
 * Add cache metadata to response
 */
function addCacheMetadata(response) {
    const responseClone = response.clone();
    const headers = new Headers(responseClone.headers);
    headers.set('sw-cache-date', Date.now().toString());
    
    return new Response(responseClone.body, {
        status: responseClone.status,
        statusText: responseClone.statusText,
        headers: headers
    });
}

/**
 * Handle static asset requests (cache first strategy)
 */
async function handleStaticAsset(request) {
    try {
        // Try cache first
        const cache = await caches.open(STATIC_CACHE_NAME);
        const cachedResponse = await cache.match(request);
        
        if (cachedResponse && isCacheFresh(cachedResponse, CACHE_EXPIRY.static)) {
            return cachedResponse;
        }
        
        // Fetch from network and update cache
        try {
            const networkResponse = await fetch(request);
            if (networkResponse.ok) {
                const responseWithMetadata = addCacheMetadata(networkResponse);
                await cache.put(request, responseWithMetadata.clone());
                return responseWithMetadata;
            }
        } catch (error) {
            console.log('SW: Network failed for static asset, using cache:', error);
        }
        
        // Return cached version even if stale
        return cachedResponse || new Response('Asset not available offline', { status: 503 });
        
    } catch (error) {
        console.error('SW: Error handling static asset:', error);
        return new Response('Service Worker error', { status: 500 });
    }
}

/**
 * Handle API requests (network first with cache fallback)
 */
async function handleApiRequest(request) {
    try {
        // Try network first
        try {
            const networkResponse = await fetch(request);
            
            if (networkResponse.ok) {
                // Cache successful responses
                const cache = await caches.open(DYNAMIC_CACHE_NAME);
                const responseWithMetadata = addCacheMetadata(networkResponse);
                await cache.put(request, responseWithMetadata.clone());
                return responseWithMetadata;
            }
        } catch (networkError) {
            console.log('SW: Network failed for API request:', networkError.message);
        }
        
        // Fall back to cache
        const cache = await caches.open(DYNAMIC_CACHE_NAME);
        const cachedResponse = await cache.match(request);
        
        if (cachedResponse) {
            // Add offline indicator header
            const headers = new Headers(cachedResponse.headers);
            headers.set('sw-offline', 'true');
            
            return new Response(cachedResponse.body, {
                status: cachedResponse.status,
                statusText: cachedResponse.statusText,
                headers: headers
            });
        }
        
        return new Response('API not available offline', { 
            status: 503,
            headers: { 'Content-Type': 'application/json' }
        });
        
    } catch (error) {
        console.error('SW: Error handling API request:', error);
        return new Response('Service Worker error', { status: 500 });
    }
}

/**
 * Handle network-only requests
 */
async function handleNetworkOnlyRequest(request) {
    try {
        return await fetch(request);
    } catch (error) {
        console.log('SW: Network-only request failed:', error.message);
        return new Response('Request failed - network required', { status: 503 });
    }
}

/**
 * Handle dynamic requests (cache first, then network)
 */
async function handleDynamicRequest(request) {
    try {
        // Check cache first
        const cache = await caches.open(DYNAMIC_CACHE_NAME);
        const cachedResponse = await cache.match(request);
        
        if (cachedResponse && isCacheFresh(cachedResponse, CACHE_EXPIRY.dynamic)) {
            return cachedResponse;
        }
        
        // Try network
        try {
            const networkResponse = await fetch(request);
            if (networkResponse.ok) {
                const responseWithMetadata = addCacheMetadata(networkResponse);
                await cache.put(request, responseWithMetadata.clone());
                return responseWithMetadata;
            }
        } catch (error) {
            console.log('SW: Network failed for dynamic request:', error.message);
        }
        
        // Return stale cache or 503
        return cachedResponse || new Response('Page not available offline', { status: 503 });
        
    } catch (error) {
        console.error('SW: Error handling dynamic request:', error);
        return new Response('Service Worker error', { status: 500 });
    }
}

/**
 * Cache job result for offline viewing
 */
async function cacheJobResult(payload) {
    try {
        const { jobId, content, metadata } = payload;
        const cache = await caches.open(DYNAMIC_CACHE_NAME);
        
        const response = new Response(JSON.stringify({ content, metadata }), {
            headers: {
                'Content-Type': 'application/json',
                'sw-cache-date': Date.now().toString()
            }
        });
        
        await cache.put(`/api/v1/applications/${jobId}/artifact`, response);
        console.log('SW: Cached job result for offline viewing:', jobId);
        
    } catch (error) {
        console.error('SW: Error caching job result:', error);
    }
}

/**
 * Clear cache by type
 */
async function clearCache(cacheType) {
    try {
        if (cacheType) {
            const cacheName = cacheType === 'static' ? STATIC_CACHE_NAME : DYNAMIC_CACHE_NAME;
            await caches.delete(cacheName);
            console.log('SW: Cleared cache:', cacheName);
        } else {
            // Clear all caches
            const cacheNames = await caches.keys();
            await Promise.all(
                cacheNames.map(name => caches.delete(name))
            );
            console.log('SW: Cleared all caches');
        }
    } catch (error) {
        console.error('SW: Error clearing cache:', error);
    }
}

/**
 * Update cache with fresh content
 */
async function updateCache() {
    try {
        const cache = await caches.open(STATIC_CACHE_NAME);
        
        // Update static assets
        const updatePromises = STATIC_ASSETS.map(async asset => {
            try {
                const response = await fetch(asset);
                if (response.ok) {
                    await cache.put(asset, addCacheMetadata(response));
                }
            } catch (error) {
                console.log('SW: Failed to update cached asset:', asset, error.message);
            }
        });
        
        await Promise.allSettled(updatePromises);
        console.log('SW: Cache update completed');
        
    } catch (error) {
        console.error('SW: Error updating cache:', error);
    }
}

/**
 * Get cache information
 */
async function getCacheInfo() {
    try {
        const cacheNames = await caches.keys();
        const info = {
            caches: [],
            totalSize: 0,
            itemCount: 0
        };
        
        for (const cacheName of cacheNames) {
            const cache = await caches.open(cacheName);
            const keys = await cache.keys();
            
            let cacheSize = 0;
            for (const request of keys) {
                const response = await cache.match(request);
                if (response) {
                    const text = await response.text();
                    cacheSize += text.length;
                }
            }
            
            info.caches.push({
                name: cacheName,
                itemCount: keys.length,
                size: cacheSize
            });
            
            info.totalSize += cacheSize;
            info.itemCount += keys.length;
        }
        
        return info;
        
    } catch (error) {
        console.error('SW: Error getting cache info:', error);
        return { error: error.message };
    }
}

/**
 * Handle offline job submissions
 */
async function handleOfflineJobSubmission() {
    try {
        // Get pending job submissions from IndexedDB
        const db = await openDB();
        const transaction = db.transaction(['jobs'], 'readonly');
        const store = transaction.objectStore('jobs');
        const pendingJobs = await store.getAll();
        
        for (const job of pendingJobs) {
            try {
                // Attempt to submit job
                const response = await fetch('/api/v1/applications', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${job.token}`
                    },
                    body: JSON.stringify(job.data)
                });
                
                if (response.ok) {
                    // Remove from pending list
                    const deleteTransaction = db.transaction(['jobs'], 'readwrite');
                    const deleteStore = deleteTransaction.objectStore('jobs');
                    await deleteStore.delete(job.id);
                    
                    console.log('SW: Successfully submitted offline job:', job.id);
                }
                
            } catch (error) {
                console.log('SW: Failed to submit offline job:', job.id, error.message);
            }
        }
        
    } catch (error) {
        console.error('SW: Error handling offline job submissions:', error);
    }
}

/**
 * Open IndexedDB for offline storage
 */
function openDB() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open('HuskyApplyDB', 1);
        
        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve(request.result);
        
        request.onupgradeneeded = (event) => {
            const db = event.target.result;
            
            if (!db.objectStoreNames.contains('jobs')) {
                const jobStore = db.createObjectStore('jobs', { keyPath: 'id' });
                jobStore.createIndex('timestamp', 'timestamp', { unique: false });
            }
        };
    });
}

/**
 * Show notification
 */
async function showNotification(data) {
    try {
        const { title, body, icon, badge, url, tag } = data;
        
        const options = {
            body,
            icon: icon || '/icon-192x192.png',
            badge: badge || '/icon-72x72.png',
            tag: tag || 'default',
            data: { url },
            requireInteraction: true,
            actions: [
                {
                    action: 'view',
                    title: 'View',
                    icon: '/icon-view.png'
                },
                {
                    action: 'dismiss',
                    title: 'Dismiss',
                    icon: '/icon-dismiss.png'
                }
            ]
        };
        
        await self.registration.showNotification(title, options);
        
    } catch (error) {
        console.error('SW: Error showing notification:', error);
    }
}

/**
 * Preload critical resources
 */
async function preloadCriticalResources() {
    try {
        const cache = await caches.open(STATIC_CACHE_NAME);
        const criticalResources = [
            '/',
            '/styles-enhanced.css',
            '/app-enhanced.js'
        ];
        
        const preloadPromises = criticalResources.map(resource =>
            cache.add(resource).catch(error =>
                console.log('SW: Failed to preload:', resource, error.message)
            )
        );
        
        await Promise.allSettled(preloadPromises);
        console.log('SW: Critical resources preloaded');
        
    } catch (error) {
        console.error('SW: Error preloading critical resources:', error);
    }
}

// Preload critical resources on install
self.addEventListener('install', event => {
    event.waitUntil(preloadCriticalResources());
});

// ======================================
// ADVANCED PWA FEATURES
// ======================================

/**
 * Background fetch for large downloads
 */
self.addEventListener('backgroundfetch', event => {
    if (event.tag === 'large-download') {
        event.waitUntil(handleLargeDownload(event));
    }
});

/**
 * Periodic background sync for data updates
 */
self.addEventListener('periodicsync', event => {
    if (event.tag === 'update-dashboard') {
        event.waitUntil(updateDashboardData());
    }
});

/**
 * Handle share target (PWA share API)
 */
self.addEventListener('fetch', event => {
    const url = new URL(event.request.url);
    
    if (url.pathname === '/share-target' && event.request.method === 'POST') {
        event.respondWith(handleShareTarget(event.request));
    }
});

/**
 * Handle large downloads in background
 */
async function handleLargeDownload(event) {
    try {
        // Process background fetch result
        const registration = event.registration;
        
        if (event.type === 'backgroundfetchsuccess') {
            // Download completed successfully
            const records = await registration.matchAll();
            
            for (const record of records) {
                const response = await record.responseReady;
                if (response.ok) {
                    // Store in cache for offline access
                    const cache = await caches.open(DYNAMIC_CACHE_NAME);
                    await cache.put(record.request, response.clone());
                }
            }
            
            // Show completion notification
            await self.registration.showNotification('Download Complete', {
                body: 'Your files are now available offline',
                icon: '/icons/icon-192x192.png',
                tag: 'download-complete'
            });
        }
        
    } catch (error) {
        console.error('SW: Error handling background download:', error);
    }
}

/**
 * Update dashboard data in background
 */
async function updateDashboardData() {
    try {
        // Fetch latest dashboard stats
        const response = await fetch('/api/v1/dashboard/stats');
        if (response.ok) {
            const cache = await caches.open(DYNAMIC_CACHE_NAME);
            await cache.put('/api/v1/dashboard/stats', response.clone());
            console.log('SW: Dashboard data updated in background');
        }
    } catch (error) {
        console.log('SW: Failed to update dashboard data:', error.message);
    }
}

/**
 * Handle incoming shared content (Web Share Target API)
 */
async function handleShareTarget(request) {
    try {
        const formData = await request.formData();
        const title = formData.get('title') || '';
        const text = formData.get('text') || '';
        const url = formData.get('url') || '';
        
        // Store shared content for processing
        const sharedContent = { title, text, url, timestamp: Date.now() };
        
        // Store in IndexedDB for later processing
        const db = await openDB();
        const transaction = db.transaction(['shared_content'], 'readwrite');
        const store = transaction.objectStore('shared_content');
        await store.add(sharedContent);
        
        // Return response that redirects to the app
        return Response.redirect('/?shared=true', 303);
        
    } catch (error) {
        console.error('SW: Error handling share target:', error);
        return Response.redirect('/', 303);
    }
}

/**
 * Smart caching based on usage patterns
 */
class SmartCache {
    static async shouldCache(request) {
        const url = new URL(request.url);
        
        // Cache based on content type and usage frequency
        const contentType = request.headers.get('content-type') || '';
        
        if (contentType.includes('image/')) {
            return true; // Always cache images
        }
        
        if (contentType.includes('application/json')) {
            // Cache API responses that are frequently accessed
            return await this.isFrequentlyAccessed(url.pathname);
        }
        
        return false;
    }
    
    static async isFrequentlyAccessed(path) {
        try {
            const db = await openDB();
            const transaction = db.transaction(['usage_stats'], 'readonly');
            const store = transaction.objectStore('usage_stats');
            const stats = await store.get(path);
            
            return stats && stats.count > 5; // Cache if accessed more than 5 times
        } catch (error) {
            return false;
        }
    }
    
    static async trackAccess(path) {
        try {
            const db = await openDB();
            const transaction = db.transaction(['usage_stats'], 'readwrite');
            const store = transaction.objectStore('usage_stats');
            
            const existing = await store.get(path);
            const stats = existing || { path, count: 0, lastAccess: Date.now() };
            
            stats.count++;
            stats.lastAccess = Date.now();
            
            await store.put(stats);
        } catch (error) {
            console.log('SW: Failed to track access:', error.message);
        }
    }
}

/**
 * Enhanced image caching with WebP support
 */
async function handleImageRequest(request) {
    const url = new URL(request.url);
    const cache = await caches.open(IMAGES_CACHE_NAME);
    
    // Check if browser supports WebP
    const supportsWebP = request.headers.get('accept')?.includes('image/webp');
    
    if (supportsWebP && !url.pathname.endsWith('.webp')) {
        // Try to get WebP version first
        const webpUrl = url.pathname.replace(/\.(jpg|jpeg|png)$/, '.webp');
        const webpRequest = new Request(webpUrl, request);
        
        try {
            const webpResponse = await fetch(webpRequest);
            if (webpResponse.ok) {
                await cache.put(webpRequest, webpResponse.clone());
                return webpResponse;
            }
        } catch (error) {
            // Fallback to original format
        }
    }
    
    // Standard image caching
    const cachedResponse = await cache.match(request);
    if (cachedResponse) {
        return cachedResponse;
    }
    
    try {
        const networkResponse = await fetch(request);
        if (networkResponse.ok) {
            await cache.put(request, networkResponse.clone());
            return networkResponse;
        }
    } catch (error) {
        // Return placeholder image for failed requests
        return new Response(createPlaceholderSVG(), {
            headers: { 'Content-Type': 'image/svg+xml' }
        });
    }
}

/**
 * Create placeholder SVG for failed image loads
 */
function createPlaceholderSVG() {
    return `
        <svg width="200" height="150" xmlns="http://www.w3.org/2000/svg">
            <rect width="100%" height="100%" fill="#f3f4f6"/>
            <text x="50%" y="50%" font-family="Arial, sans-serif" font-size="14" 
                  text-anchor="middle" dy=".3em" fill="#6b7280">
                Image unavailable offline
            </text>
        </svg>
    `;
}

/**
 * Progressive loading strategy
 */
class ProgressiveLoader {
    static async loadPage(request) {
        const cache = await caches.open(PWA_CACHE_NAME);
        
        // Return shell immediately
        const shellResponse = await cache.match('/app-shell.html');
        if (shellResponse) {
            // Load content progressively in background
            this.loadContentInBackground(request, cache);
            return shellResponse;
        }
        
        // Fallback to network
        return fetch(request);
    }
    
    static async loadContentInBackground(request, cache) {
        try {
            const response = await fetch(request);
            if (response.ok) {
                await cache.put(request, response.clone());
                
                // Notify main thread about content update
                const clients = await self.clients.matchAll();
                clients.forEach(client => {
                    client.postMessage({
                        type: 'CONTENT_UPDATED',
                        url: request.url
                    });
                });
            }
        } catch (error) {
            console.log('SW: Failed to load content in background:', error.message);
        }
    }
}

/**
 * Enhanced offline detector
 */
class OfflineDetector {
    static isOnline() {
        return navigator.onLine;
    }
    
    static async getConnectionInfo() {
        const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
        
        return {
            online: navigator.onLine,
            effectiveType: connection?.effectiveType || 'unknown',
            downlink: connection?.downlink || 0,
            rtt: connection?.rtt || 0,
            saveData: connection?.saveData || false
        };
    }
    
    static shouldUseCache() {
        const connection = navigator.connection;
        return !navigator.onLine || 
               (connection && (connection.saveData || connection.effectiveType === 'slow-2g'));
    }
}

/**
 * Performance monitoring
 */
class PerformanceMonitor {
    static async logCachePerformance(request, cacheHit, responseTime) {
        try {
            const db = await openDB();
            const transaction = db.transaction(['performance'], 'readwrite');
            const store = transaction.objectStore('performance');
            
            await store.add({
                url: request.url,
                cacheHit,
                responseTime,
                timestamp: Date.now(),
                userAgent: navigator.userAgent
            });
            
        } catch (error) {
            // Ignore storage errors for performance data
        }
    }
    
    static async getPerformanceStats() {
        try {
            const db = await openDB();
            const transaction = db.transaction(['performance'], 'readonly');
            const store = transaction.objectStore('performance');
            const allRecords = await store.getAll();
            
            const stats = {
                totalRequests: allRecords.length,
                cacheHitRate: 0,
                averageResponseTime: 0
            };
            
            if (allRecords.length > 0) {
                const cacheHits = allRecords.filter(r => r.cacheHit).length;
                stats.cacheHitRate = (cacheHits / allRecords.length) * 100;
                
                const totalTime = allRecords.reduce((sum, r) => sum + r.responseTime, 0);
                stats.averageResponseTime = totalTime / allRecords.length;
            }
            
            return stats;
        } catch (error) {
            return { error: error.message };
        }
    }
}

/**
 * Enhanced database initialization
 */
function openDB() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open('HuskyApplyDB', 2);
        
        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve(request.result);
        
        request.onupgradeneeded = (event) => {
            const db = event.target.result;
            
            // Jobs store
            if (!db.objectStoreNames.contains('jobs')) {
                const jobStore = db.createObjectStore('jobs', { keyPath: 'id' });
                jobStore.createIndex('timestamp', 'timestamp', { unique: false });
                jobStore.createIndex('status', 'status', { unique: false });
            }
            
            // Shared content store
            if (!db.objectStoreNames.contains('shared_content')) {
                const shareStore = db.createObjectStore('shared_content', { 
                    keyPath: 'id', 
                    autoIncrement: true 
                });
                shareStore.createIndex('timestamp', 'timestamp', { unique: false });
            }
            
            // Usage statistics
            if (!db.objectStoreNames.contains('usage_stats')) {
                const usageStore = db.createObjectStore('usage_stats', { keyPath: 'path' });
                usageStore.createIndex('count', 'count', { unique: false });
                usageStore.createIndex('lastAccess', 'lastAccess', { unique: false });
            }
            
            // Performance monitoring
            if (!db.objectStoreNames.contains('performance')) {
                const perfStore = db.createObjectStore('performance', { 
                    keyPath: 'id', 
                    autoIncrement: true 
                });
                perfStore.createIndex('timestamp', 'timestamp', { unique: false });
                perfStore.createIndex('url', 'url', { unique: false });
            }
            
            // Settings store
            if (!db.objectStoreNames.contains('settings')) {
                const settingsStore = db.createObjectStore('settings', { keyPath: 'key' });
            }
        };
    });
}

console.log('SW: Enhanced Service Worker v2.1.0 loaded with PWA features');