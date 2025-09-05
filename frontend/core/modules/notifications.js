/**
 * Notifications Module
 * Handles toast notifications, alerts, and user feedback
 */

export default class NotificationModule {
    constructor() {
        this.initialized = false;
        this.toastContainer = null;
        this.toastQueue = [];
        this.maxToasts = 5;
        this.defaultDuration = 5000;
    }
    
    async init() {
        if (this.initialized) return;
        
        console.log('🔔 Initializing Notification Module...');
        
        // Create toast container
        this.createToastContainer();
        
        // Setup global notification listeners
        this.setupGlobalListeners();
        
        this.initialized = true;
        console.log('✅ Notification Module Ready');
    }
    
    createToastContainer() {
        this.toastContainer = document.getElementById('toastContainer');
        if (!this.toastContainer) {
            this.toastContainer = document.createElement('div');
            this.toastContainer.id = 'toastContainer';
            this.toastContainer.className = 'toast-container';
            this.toastContainer.setAttribute('aria-live', 'polite');
            this.toastContainer.setAttribute('aria-atomic', 'false');
            document.body.appendChild(this.toastContainer);
        }
    }
    
    setupGlobalListeners() {
        // Listen for custom notification events
        document.addEventListener('huskyapply:notify', (event) => {
            const { message, type, duration, actions } = event.detail;
            this.showToast(message, type, duration, actions);
        });
        
        // Listen for browser notifications
        document.addEventListener('huskyapply:browser-notify', (event) => {
            const { title, body, icon, tag } = event.detail;
            this.showBrowserNotification(title, body, icon, tag);
        });
    }
    
    showToast(message, type = 'info', duration = this.defaultDuration, actions = []) {
        // Limit number of toasts
        if (this.toastContainer.children.length >= this.maxToasts) {
            this.removeOldestToast();
        }
        
        const toast = this.createToastElement(message, type, actions);
        
        // Add to container with animation
        this.toastContainer.appendChild(toast);
        
        // Force reflow for animation
        toast.offsetHeight;
        toast.classList.add('show');
        
        // Auto remove after duration
        if (duration > 0) {
            setTimeout(() => this.removeToast(toast), duration);
        }
        
        // Track in queue
        this.toastQueue.push({
            element: toast,
            timestamp: Date.now(),
            type,
            message
        });
        
        return toast;
    }
    
    createToastElement(message, type, actions = []) {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
        
        const toastContent = document.createElement('div');
        toastContent.className = 'toast-content';
        
        // Icon
        const icon = document.createElement('span');
        icon.className = 'toast-icon';
        icon.textContent = this.getToastIcon(type);
        toastContent.appendChild(icon);
        
        // Message
        const messageElement = document.createElement('span');
        messageElement.className = 'toast-message';
        messageElement.textContent = message;
        toastContent.appendChild(messageElement);
        
        // Actions
        if (actions.length > 0) {
            const actionsContainer = document.createElement('div');
            actionsContainer.className = 'toast-actions';
            
            actions.forEach(action => {
                const button = document.createElement('button');
                button.className = `toast-action ${action.type || 'secondary'}`;
                button.textContent = action.label;
                button.addEventListener('click', () => {
                    if (action.handler) action.handler();
                    this.removeToast(toast);
                });
                actionsContainer.appendChild(button);
            });
            
            toastContent.appendChild(actionsContainer);
        }
        
        // Close button
        const closeButton = document.createElement('button');
        closeButton.className = 'toast-close';
        closeButton.setAttribute('aria-label', 'Close notification');
        closeButton.innerHTML = '&times;';
        closeButton.addEventListener('click', () => this.removeToast(toast));
        toastContent.appendChild(closeButton);
        
        toast.appendChild(toastContent);
        
        return toast;
    }
    
    removeToast(toast) {
        if (toast && toast.parentNode) {
            toast.classList.add('hide');
            setTimeout(() => {
                if (toast.parentNode) {
                    toast.parentNode.removeChild(toast);
                }
                // Remove from queue
                this.toastQueue = this.toastQueue.filter(item => item.element !== toast);
            }, 300);
        }
    }
    
    removeOldestToast() {
        if (this.toastQueue.length > 0) {
            const oldest = this.toastQueue[0];
            this.removeToast(oldest.element);
        }
    }
    
    clearAllToasts() {
        this.toastQueue.forEach(item => this.removeToast(item.element));
        this.toastQueue = [];
    }
    
    getToastIcon(type) {
        const icons = {
            success: '✅',
            error: '❌',
            warning: '⚠️',
            info: 'ℹ️',
            loading: '⏳'
        };
        return icons[type] || icons.info;
    }
    
    // Convenience methods for different notification types
    showSuccess(message, duration, actions) {
        return this.showToast(message, 'success', duration, actions);
    }
    
    showError(message, duration, actions) {
        return this.showToast(message, 'error', duration, actions);
    }
    
    showWarning(message, duration, actions) {
        return this.showToast(message, 'warning', duration, actions);
    }
    
    showInfo(message, duration, actions) {
        return this.showToast(message, 'info', duration, actions);
    }
    
    showLoading(message) {
        return this.showToast(message, 'loading', 0); // 0 duration = persistent
    }
    
    // Browser notifications (requires permission)
    async showBrowserNotification(title, body, icon, tag) {
        if (!('Notification' in window)) {
            console.warn('Browser notifications not supported');
            return;
        }
        
        if (Notification.permission === 'default') {
            await this.requestNotificationPermission();
        }
        
        if (Notification.permission === 'granted') {
            const notification = new Notification(title, {
                body,
                icon: icon || '/favicon.ico',
                tag: tag || 'huskyapply',
                badge: '/icon-72x72.png',
                requireInteraction: false,
                silent: false
            });
            
            // Auto close after 10 seconds
            setTimeout(() => notification.close(), 10000);
            
            // Handle click
            notification.onclick = () => {
                window.focus();
                notification.close();
            };
            
            return notification;
        }
    }
    
    async requestNotificationPermission() {
        try {
            const permission = await Notification.requestPermission();
            return permission === 'granted';
        } catch (error) {
            console.error('Error requesting notification permission:', error);
            return false;
        }
    }
    
    // Alert dialog replacement
    showAlert(title, message, type = 'info', buttons = []) {
        return new Promise((resolve) => {
            const overlay = document.createElement('div');
            overlay.className = 'alert-overlay';
            
            const dialog = document.createElement('div');
            dialog.className = `alert-dialog alert-${type}`;
            dialog.setAttribute('role', 'dialog');
            dialog.setAttribute('aria-modal', 'true');
            
            // Title
            if (title) {
                const titleElement = document.createElement('h3');
                titleElement.className = 'alert-title';
                titleElement.textContent = title;
                dialog.appendChild(titleElement);
            }
            
            // Message
            const messageElement = document.createElement('div');
            messageElement.className = 'alert-message';
            messageElement.textContent = message;
            dialog.appendChild(messageElement);
            
            // Buttons
            const buttonContainer = document.createElement('div');
            buttonContainer.className = 'alert-buttons';
            
            if (buttons.length === 0) {
                buttons = [{ label: 'OK', value: true, primary: true }];
            }
            
            buttons.forEach((button, index) => {
                const btn = document.createElement('button');
                btn.className = `alert-button ${button.primary ? 'primary' : 'secondary'}`;
                btn.textContent = button.label;
                btn.addEventListener('click', () => {
                    document.body.removeChild(overlay);
                    resolve(button.value);
                });
                
                // Focus first button
                if (index === 0) {
                    setTimeout(() => btn.focus(), 100);
                }
                
                buttonContainer.appendChild(btn);
            });
            
            dialog.appendChild(buttonContainer);
            overlay.appendChild(dialog);
            document.body.appendChild(overlay);
            
            // Close on escape
            const handleKeydown = (e) => {
                if (e.key === 'Escape') {
                    document.removeEventListener('keydown', handleKeydown);
                    document.body.removeChild(overlay);
                    resolve(false);
                }
            };
            document.addEventListener('keydown', handleKeydown);
            
            // Close on overlay click
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    document.body.removeChild(overlay);
                    resolve(false);
                }
            });
        });
    }
    
    // Confirm dialog replacement
    showConfirm(message, title = 'Confirm') {
        return this.showAlert(title, message, 'warning', [
            { label: 'Cancel', value: false },
            { label: 'Confirm', value: true, primary: true }
        ]);
    }
    
    // Progress notification
    showProgress(message, progress = 0) {
        const toast = this.createProgressToast(message, progress);
        this.toastContainer.appendChild(toast);
        
        return {
            update: (newProgress, newMessage) => {
                const progressBar = toast.querySelector('.progress-bar');
                const messageElement = toast.querySelector('.toast-message');
                
                if (progressBar) {
                    progressBar.style.width = `${newProgress}%`;
                }
                
                if (newMessage && messageElement) {
                    messageElement.textContent = newMessage;
                }
            },
            complete: (finalMessage) => {
                const messageElement = toast.querySelector('.toast-message');
                const progressBar = toast.querySelector('.progress-bar');
                
                if (messageElement && finalMessage) {
                    messageElement.textContent = finalMessage;
                }
                
                if (progressBar) {
                    progressBar.style.width = '100%';
                    progressBar.classList.add('complete');
                }
                
                setTimeout(() => this.removeToast(toast), 2000);
            },
            remove: () => this.removeToast(toast)
        };
    }
    
    createProgressToast(message, progress) {
        const toast = document.createElement('div');
        toast.className = 'toast toast-progress';
        toast.setAttribute('role', 'status');
        
        const content = document.createElement('div');
        content.className = 'toast-content';
        
        const icon = document.createElement('span');
        icon.className = 'toast-icon';
        icon.textContent = '⏳';
        content.appendChild(icon);
        
        const messageElement = document.createElement('span');
        messageElement.className = 'toast-message';
        messageElement.textContent = message;
        content.appendChild(messageElement);
        
        const progressContainer = document.createElement('div');
        progressContainer.className = 'progress-container';
        
        const progressBar = document.createElement('div');
        progressBar.className = 'progress-bar';
        progressBar.style.width = `${progress}%`;
        progressContainer.appendChild(progressBar);
        
        content.appendChild(progressContainer);
        
        const closeButton = document.createElement('button');
        closeButton.className = 'toast-close';
        closeButton.setAttribute('aria-label', 'Close notification');
        closeButton.innerHTML = '&times;';
        closeButton.addEventListener('click', () => this.removeToast(toast));
        content.appendChild(closeButton);
        
        toast.appendChild(content);
        
        return toast;
    }
    
    // Job completion notification
    showJobComplete(jobId, success, message) {
        const title = success ? 'Job Complete!' : 'Job Failed';
        const toastType = success ? 'success' : 'error';
        const actions = success ? [
            {
                label: 'View Result',
                type: 'primary',
                handler: () => {
                    // Scroll to results or navigate to results page
                    const resultsSection = document.getElementById('resultsSection');
                    if (resultsSection) {
                        resultsSection.scrollIntoView({ behavior: 'smooth' });
                    }
                }
            }
        ] : [
            {
                label: 'Retry',
                type: 'primary',
                handler: () => {
                    // Scroll back to form
                    document.getElementById('application-form')?.scrollIntoView({ behavior: 'smooth' });
                }
            }
        ];
        
        this.showToast(message, toastType, 8000, actions);
        
        // Also show browser notification if user has granted permission
        if (Notification.permission === 'granted') {
            this.showBrowserNotification(title, message);
        }
    }
    
    // Network status notifications
    showOfflineNotification() {
        this.showWarning('You are offline. Some features may not be available.', 0);
    }
    
    showOnlineNotification() {
        this.showSuccess('Connection restored!', 3000);
    }
}

// Initialize network status monitoring
if (typeof window !== 'undefined') {
    window.addEventListener('online', () => {
        if (window.HuskyApplyCore?.modules?.has('notifications')) {
            const notificationModule = window.HuskyApplyCore.modules.get('notifications');
            notificationModule.showOnlineNotification();
        }
    });
    
    window.addEventListener('offline', () => {
        if (window.HuskyApplyCore?.modules?.has('notifications')) {
            const notificationModule = window.HuskyApplyCore.modules.get('notifications');
            notificationModule.showOfflineNotification();
        }
    });
}