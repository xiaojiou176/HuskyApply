/**
 * Authentication Module
 * Handles login, registration, and user management
 */

import { AppCore } from '../app-core.js';

export default class AuthModule {
    constructor() {
        this.initialized = false;
    }
    
    async init() {
        if (this.initialized) return;
        
        console.log('🔐 Initializing Auth Module...');
        
        // Setup form handlers
        this.setupLoginForm();
        this.setupRegistrationForm();
        this.setupLogoutHandlers();
        
        // Verify existing token if present
        if (AppCore.state.isAuthenticated) {
            await this.verifyToken();
        }
        
        this.initialized = true;
        console.log('✅ Auth Module Ready');
    }
    
    setupLoginForm() {
        const loginForm = document.getElementById('loginForm');
        if (!loginForm) return;
        
        loginForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            
            const email = document.getElementById('loginEmail')?.value.trim();
            const password = document.getElementById('loginPassword')?.value;
            
            if (!email || !password) {
                this.showError('loginError', 'Email and password are required');
                return;
            }
            
            try {
                this.setFormLoading(true);
                await this.login(email, password);
            } catch (error) {
                this.showError('loginError', error.message);
            } finally {
                this.setFormLoading(false);
            }
        });
    }
    
    setupRegistrationForm() {
        const registerForm = document.getElementById('registerForm');
        if (!registerForm) return;
        
        registerForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            
            const email = document.getElementById('registerEmail')?.value.trim();
            const password = document.getElementById('registerPassword')?.value;
            
            if (!email || !password) {
                this.showError('registerError', 'Email and password are required');
                return;
            }
            
            try {
                this.setFormLoading(true);
                await this.register(email, password);
            } catch (error) {
                this.showError('registerError', error.message);
            } finally {
                this.setFormLoading(false);
            }
        });
    }
    
    setupLogoutHandlers() {
        document.querySelectorAll('[data-action="logout"]').forEach(button => {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                this.logout();
            });
        });
    }
    
    async login(email, password) {
        const response = await fetch(`${AppCore.config.API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => null);
            throw new Error(errorData?.message || `Login failed: ${response.status}`);
        }
        
        const data = await response.json();
        if (!data.accessToken) {
            throw new Error('No access token received');
        }
        
        // Store token and update state
        localStorage.setItem('jwtToken', data.accessToken);
        AppCore.state.isAuthenticated = true;
        AppCore.state.user = data.user || { email };
        
        // Redirect to main app
        window.location.href = 'index.html';
    }
    
    async register(email, password) {
        const response = await fetch(`${AppCore.config.API_BASE}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => null);
            if (response.status === 409) {
                throw new Error('Email already exists');
            }
            throw new Error(errorData?.message || `Registration failed: ${response.status}`);
        }
        
        const data = await response.json();
        if (!data.accessToken) {
            throw new Error('Registration successful but no access token received');
        }
        
        // Store token and update state
        localStorage.setItem('jwtToken', data.accessToken);
        AppCore.state.isAuthenticated = true;
        AppCore.state.user = data.user || { email };
        
        // Redirect to main app
        window.location.href = 'index.html';
    }
    
    async verifyToken() {
        try {
            const token = localStorage.getItem('jwtToken');
            if (!token) return false;
            
            const response = await fetch(`${AppCore.config.API_BASE}/auth/verify`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            
            if (response.ok) {
                const userData = await response.json();
                AppCore.state.user = userData;
                this.updateUIForAuthenticatedUser();
                return true;
            } else {
                this.logout();
                return false;
            }
        } catch (error) {
            console.warn('Token verification failed:', error);
            return false;
        }
    }
    
    updateUIForAuthenticatedUser() {
        const loginBtn = document.getElementById('loginBtn');
        const dashboardBtn = document.getElementById('dashboardBtn');
        const userMenu = document.getElementById('userMenu');
        
        if (loginBtn) loginBtn.style.display = 'none';
        if (dashboardBtn) dashboardBtn.style.display = 'inline-flex';
        
        if (userMenu && AppCore.state.user) {
            userMenu.style.display = 'inline-flex';
            const avatar = userMenu.querySelector('.user-avatar');
            if (avatar) {
                avatar.textContent = AppCore.state.user.email?.[0]?.toUpperCase() || '👤';
            }
        }
    }
    
    logout() {
        localStorage.removeItem('jwtToken');
        AppCore.state.isAuthenticated = false;
        AppCore.state.user = null;
        
        // Clear any cached data
        localStorage.removeItem('huskyapply_form_data');
        sessionStorage.clear();
        
        window.location.href = 'login.html';
    }
    
    setFormLoading(isLoading) {
        const submitBtns = document.querySelectorAll('button[type="submit"]');
        submitBtns.forEach(btn => {
            btn.disabled = isLoading;
            const text = btn.querySelector('.btn-text') || btn;
            text.textContent = isLoading ? 'Processing...' : (btn.dataset.originalText || 'Submit');
            
            if (!btn.dataset.originalText) {
                btn.dataset.originalText = text.textContent;
            }
        });
    }
    
    showError(errorElementId, message) {
        const errorElement = document.getElementById(errorElementId);
        if (errorElement) {
            errorElement.textContent = message;
            errorElement.style.display = 'block';
            errorElement.setAttribute('role', 'alert');
        }
    }
    
    hideError(errorElementId) {
        const errorElement = document.getElementById(errorElementId);
        if (errorElement) {
            errorElement.style.display = 'none';
            errorElement.removeAttribute('role');
        }
    }
    
    // Check if user is already authenticated on page load
    checkExistingAuth() {
        const existingToken = localStorage.getItem('jwtToken');
        if (existingToken && window.location.pathname.includes('login.html')) {
            window.location.href = 'index.html';
        }
    }
}

// Auto-check auth on login pages
document.addEventListener('DOMContentLoaded', () => {
    if (window.location.pathname.includes('login.html')) {
        const authModule = new AuthModule();
        authModule.checkExistingAuth();
    }
});