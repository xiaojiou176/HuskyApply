/**
 * Dashboard Module - User dashboard and analytics
 * 
 * This module handles the user dashboard functionality, job statistics,
 * and user analytics. It's loaded on-demand when the user navigates to dashboard.
 */

class DashboardModule {
    constructor() {
        this.core = window.HuskyApply;
        this.initialized = false;
        this.refreshInterval = null;
        this.charts = new Map();
    }

    async init() {
        if (this.initialized) return;
        
        console.log('Initializing Dashboard module...');
        
        await this.loadDependencies();
        this.setupEventListeners();
        this.initialized = true;
        
        console.log('Dashboard module initialized');
    }

    async loadDependencies() {
        // Load Chart.js dynamically for dashboard charts
        if (!window.Chart) {
            try {
                await this.loadScript('https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.js');
                console.log('Chart.js loaded successfully');
            } catch (error) {
                console.warn('Failed to load Chart.js:', error);
            }
        }
    }

    loadScript(src) {
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = src;
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }

    setupEventListeners() {
        // Dashboard navigation
        this.core.on('navigate-dashboard', (event) => {
            this.loadDashboard(event.detail.section);
        });

        // Auto-refresh dashboard data
        this.core.on('dashboard-refresh', () => {
            this.refreshDashboardData();
        });
    }

    async loadDashboard(section = 'overview') {
        const startTime = performance.now();
        
        try {
            // Create dashboard container if it doesn't exist
            let dashboardContainer = document.getElementById('dashboard-container');
            if (!dashboardContainer) {
                dashboardContainer = this.createDashboardContainer();
            }

            // Load dashboard data
            const [statsData, jobsData, activityData] = await Promise.all([
                this.loadDashboardStats(),
                this.loadUserJobs(),
                this.loadRecentActivity()
            ]);

            // Render dashboard sections
            this.renderDashboard(dashboardContainer, {
                stats: statsData,
                jobs: jobsData,
                activity: activityData
            }, section);

            // Start auto-refresh if not already running
            this.startAutoRefresh();

            const loadTime = performance.now() - startTime;
            this.core.logMetric('dashboard_load_time', loadTime);

        } catch (error) {
            console.error('Failed to load dashboard:', error);
            this.core.logError('dashboard_load_error', error.message);
            this.showErrorMessage('Failed to load dashboard data');
        }
    }

    createDashboardContainer() {
        const container = document.createElement('div');
        container.id = 'dashboard-container';
        container.className = 'dashboard-container';
        
        // Find main content area and append
        const mainContent = document.querySelector('.main-content') || document.body;
        mainContent.appendChild(container);
        
        return container;
    }

    async loadDashboardStats() {
        return await this.core.apiCall('/dashboard/stats');
    }

    async loadUserJobs(page = 0, size = 10) {
        return await this.core.apiCall(`/dashboard/jobs?page=${page}&size=${size}`);
    }

    async loadRecentActivity() {
        return await this.core.apiCall('/dashboard/recent-activity');
    }

    renderDashboard(container, data, section) {
        container.innerHTML = this.getDashboardHTML(data, section);
        
        // Initialize interactive components
        this.initializeCharts(data.stats);
        this.initializeJobsTable(data.jobs);
        this.initializeActivityFeed(data.activity);
    }

    getDashboardHTML(data, section) {
        return `
            <div class="dashboard-header">
                <h1>Dashboard</h1>
                <div class="dashboard-nav">
                    <button class="nav-btn ${section === 'overview' ? 'active' : ''}" data-section="overview">
                        Overview
                    </button>
                    <button class="nav-btn ${section === 'jobs' ? 'active' : ''}" data-section="jobs">
                        Jobs
                    </button>
                    <button class="nav-btn ${section === 'analytics' ? 'active' : ''}" data-section="analytics">
                        Analytics
                    </button>
                </div>
                <button class="refresh-btn" id="dashboard-refresh">
                    🔄 Refresh
                </button>
            </div>

            <div class="dashboard-stats">
                <div class="stat-card">
                    <div class="stat-value">${data.stats.totalJobs || 0}</div>
                    <div class="stat-label">Total Jobs</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value">${data.stats.completedJobs || 0}</div>
                    <div class="stat-label">Completed</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value">${data.stats.pendingJobs || 0}</div>
                    <div class="stat-label">Pending</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value">${Math.round((data.stats.completedJobs || 0) / Math.max(data.stats.totalJobs, 1) * 100)}%</div>
                    <div class="stat-label">Success Rate</div>
                </div>
            </div>

            <div class="dashboard-content">
                <div class="dashboard-section ${section === 'overview' ? 'active' : 'hidden'}">
                    <div class="chart-container">
                        <canvas id="jobs-chart" width="400" height="200"></canvas>
                    </div>
                    <div class="recent-activity">
                        <h3>Recent Activity</h3>
                        <div id="activity-feed"></div>
                    </div>
                </div>

                <div class="dashboard-section ${section === 'jobs' ? 'active' : 'hidden'}">
                    <div class="jobs-section">
                        <h3>Your Jobs</h3>
                        <div id="jobs-table"></div>
                    </div>
                </div>

                <div class="dashboard-section ${section === 'analytics' ? 'active' : 'hidden'}">
                    <div class="analytics-section">
                        <h3>Performance Analytics</h3>
                        <div class="analytics-charts">
                            <canvas id="performance-chart" width="400" height="200"></canvas>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    initializeCharts(statsData) {
        if (!window.Chart) return;

        // Jobs status chart
        const jobsChart = document.getElementById('jobs-chart');
        if (jobsChart) {
            this.charts.set('jobs', new Chart(jobsChart, {
                type: 'doughnut',
                data: {
                    labels: ['Completed', 'Processing', 'Pending', 'Failed'],
                    datasets: [{
                        data: [
                            statsData.completedJobs || 0,
                            statsData.processingJobs || 0,
                            statsData.pendingJobs || 0,
                            statsData.failedJobs || 0
                        ],
                        backgroundColor: [
                            '#10B981', // Green for completed
                            '#3B82F6', // Blue for processing
                            '#F59E0B', // Yellow for pending
                            '#EF4444'  // Red for failed
                        ]
                    }]
                },
                options: {
                    responsive: true,
                    plugins: {
                        legend: {
                            position: 'bottom'
                        }
                    }
                }
            }));
        }

        // Performance chart
        const perfChart = document.getElementById('performance-chart');
        if (perfChart && statsData.performanceData) {
            this.charts.set('performance', new Chart(perfChart, {
                type: 'line',
                data: {
                    labels: statsData.performanceData.labels || [],
                    datasets: [{
                        label: 'Processing Time (seconds)',
                        data: statsData.performanceData.processingTimes || [],
                        borderColor: '#3B82F6',
                        backgroundColor: 'rgba(59, 130, 246, 0.1)',
                        fill: true
                    }]
                },
                options: {
                    responsive: true,
                    scales: {
                        y: {
                            beginAtZero: true
                        }
                    }
                }
            }));
        }
    }

    initializeJobsTable(jobsData) {
        const jobsTable = document.getElementById('jobs-table');
        if (!jobsTable || !jobsData) return;

        const tableHTML = `
            <table class="jobs-table">
                <thead>
                    <tr>
                        <th>Job ID</th>
                        <th>Status</th>
                        <th>Created</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    ${jobsData.jobs?.map(job => `
                        <tr>
                            <td><code>${job.id.substring(0, 8)}</code></td>
                            <td><span class="status-badge status-${job.status.toLowerCase()}">${job.status}</span></td>
                            <td>${new Date(job.createdAt).toLocaleString()}</td>
                            <td>
                                <button class="btn btn-sm" data-job-id="${job.id}" data-action="view">View</button>
                                ${job.status === 'COMPLETED' ? 
                                    `<button class="btn btn-sm btn-primary" data-job-id="${job.id}" data-action="download">Download</button>` : 
                                    ''
                                }
                            </td>
                        </tr>
                    `).join('') || '<tr><td colspan="4">No jobs found</td></tr>'}
                </tbody>
            </table>
        `;

        jobsTable.innerHTML = tableHTML;

        // Add event listeners for job actions
        jobsTable.addEventListener('click', async (e) => {
            const button = e.target.closest('button[data-job-id]');
            if (!button) return;

            const jobId = button.dataset.jobId;
            const action = button.dataset.action;

            if (action === 'view') {
                await this.viewJob(jobId);
            } else if (action === 'download') {
                await this.downloadJobResult(jobId);
            }
        });
    }

    initializeActivityFeed(activityData) {
        const activityFeed = document.getElementById('activity-feed');
        if (!activityFeed || !activityData) return;

        const activitiesHTML = activityData.activities?.map(activity => `
            <div class="activity-item">
                <div class="activity-icon">${this.getActivityIcon(activity.type)}</div>
                <div class="activity-content">
                    <div class="activity-message">${activity.message}</div>
                    <div class="activity-time">${this.formatRelativeTime(activity.timestamp)}</div>
                </div>
            </div>
        `).join('') || '<div class="no-activity">No recent activity</div>';

        activityFeed.innerHTML = activitiesHTML;
    }

    getActivityIcon(type) {
        const icons = {
            job_created: '🆕',
            job_completed: '✅',
            job_failed: '❌',
            template_created: '📄',
            subscription_updated: '💳'
        };
        return icons[type] || '📋';
    }

    formatRelativeTime(timestamp) {
        const now = new Date();
        const date = new Date(timestamp);
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        return `${diffDays}d ago`;
    }

    async viewJob(jobId) {
        try {
            // Load job viewer module dynamically
            const jobViewer = await this.core.loadModule('job-viewer');
            await jobViewer.showJob(jobId);
        } catch (error) {
            console.error('Failed to view job:', error);
            this.showErrorMessage('Failed to load job details');
        }
    }

    async downloadJobResult(jobId) {
        try {
            const response = await fetch(`${this.core.apiBase}/applications/${jobId}/artifact`, {
                headers: {
                    'Authorization': `Bearer ${this.core.token}`
                }
            });

            if (!response.ok) throw new Error('Download failed');

            const data = await response.json();
            this.downloadFile(data.generatedText, `cover-letter-${jobId.substring(0, 8)}.txt`);
            
        } catch (error) {
            console.error('Download failed:', error);
            this.showErrorMessage('Failed to download job result');
        }
    }

    downloadFile(content, filename) {
        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
    }

    showErrorMessage(message) {
        // Create temporary error notification
        const notification = document.createElement('div');
        notification.className = 'error-notification';
        notification.textContent = message;
        document.body.appendChild(notification);

        setTimeout(() => {
            notification.remove();
        }, 5000);
    }

    async refreshDashboardData() {
        if (!this.initialized) return;
        
        console.log('Refreshing dashboard data...');
        
        try {
            const refreshButton = document.getElementById('dashboard-refresh');
            if (refreshButton) {
                refreshButton.textContent = '🔄 Refreshing...';
                refreshButton.disabled = true;
            }

            await this.loadDashboard();

            if (refreshButton) {
                refreshButton.textContent = '🔄 Refresh';
                refreshButton.disabled = false;
            }

        } catch (error) {
            console.error('Dashboard refresh failed:', error);
            this.showErrorMessage('Failed to refresh dashboard');
        }
    }

    startAutoRefresh(intervalMs = 60000) {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
        }

        this.refreshInterval = setInterval(() => {
            this.refreshDashboardData();
        }, intervalMs);
    }

    destroy() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
        }

        // Destroy charts
        this.charts.forEach(chart => chart.destroy());
        this.charts.clear();

        // Remove dashboard container
        const container = document.getElementById('dashboard-container');
        if (container) {
            container.remove();
        }

        this.initialized = false;
    }
}

// Export for dynamic loading
export { DashboardModule };
export const init = async () => {
    const dashboard = new DashboardModule();
    await dashboard.init();
    return dashboard;
};