// Authentication logic for HuskyApply login and registration

// Get references to forms and error message elements
const registerForm = document.getElementById('registerForm');
const loginForm = document.getElementById('loginForm');

// Utility function to show error messages
function showError(errorElementId, message) {
    const errorElement = document.getElementById(errorElementId);
    errorElement.textContent = message;
    errorElement.style.display = 'block';
}

// Utility function to hide error messages
function hideError(errorElementId) {
    const errorElement = document.getElementById(errorElementId);
    errorElement.style.display = 'none';
}

// Utility function to handle successful authentication
function handleAuthSuccess(accessToken) {
    // Store JWT token in localStorage
    localStorage.setItem('jwtToken', accessToken);
    
    // Redirect to main application
    window.location.href = 'index.html';
}

// Registration form submit event listener
registerForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    
    // Hide any previous error messages
    hideError('registerError');
    
    try {
        // Get form data
        const email = document.getElementById('registerEmail').value.trim();
        const password = document.getElementById('registerPassword').value;
        
        // Validate inputs
        if (!email || !password) {
            showError('registerError', 'Email and password are required');
            return;
        }
        
        // Make POST request to registration endpoint
        const response = await fetch('/api/v1/auth/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                email: email,
                password: password
            })
        });
        
        // Handle response based on status code
        if (response.status === 201) {
            // Registration successful
            const data = await response.json();
            const accessToken = data.accessToken;
            
            if (accessToken) {
                handleAuthSuccess(accessToken);
            } else {
                showError('registerError', 'Registration successful but no access token received');
            }
        } else if (response.status === 409) {
            // Email already exists
            showError('registerError', 'Email already exists');
        } else {
            // Other errors
            const errorData = await response.json().catch(() => null);
            const errorMessage = errorData?.message || `Registration failed with status: ${response.status}`;
            showError('registerError', errorMessage);
        }
        
    } catch (error) {
        // Network or other errors
        showError('registerError', `Network error: ${error.message}`);
    }
});

// Login form submit event listener
loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    
    // Hide any previous error messages
    hideError('loginError');
    
    try {
        // Get form data
        const email = document.getElementById('loginEmail').value.trim();
        const password = document.getElementById('loginPassword').value;
        
        // Validate inputs
        if (!email || !password) {
            showError('loginError', 'Email and password are required');
            return;
        }
        
        // Make POST request to login endpoint
        const response = await fetch('/api/v1/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                email: email,
                password: password
            })
        });
        
        // Handle response based on status code
        if (response.status === 200) {
            // Login successful
            const data = await response.json();
            const accessToken = data.accessToken;
            
            if (accessToken) {
                handleAuthSuccess(accessToken);
            } else {
                showError('loginError', 'Login successful but no access token received');
            }
        } else if (response.status === 401) {
            // Invalid credentials
            showError('loginError', 'Invalid email or password');
        } else {
            // Other errors
            const errorData = await response.json().catch(() => null);
            const errorMessage = errorData?.message || `Login failed with status: ${response.status}`;
            showError('loginError', errorMessage);
        }
        
    } catch (error) {
        // Network or other errors
        showError('loginError', `Network error: ${error.message}`);
    }
});

// Check if user is already authenticated on page load
document.addEventListener('DOMContentLoaded', () => {
    const existingToken = localStorage.getItem('jwtToken');
    if (existingToken) {
        // User already has a token, redirect to main app
        window.location.href = 'index.html';
    }
});