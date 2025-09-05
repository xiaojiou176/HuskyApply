# Frontend Configuration

## Environment Variables

This directory contains configuration files that reference environment variables for security.

### Required Environment Variables

Set these in your deployment environment or HTML template:

```javascript
// In your HTML template or deployment script:
window.SENTRY_DSN = 'your-sentry-dsn-here';
window.APP_VERSION = '2.0.0';
window.BUILD_TIME = '2024-09-05T20:00:00Z';
window.GIT_COMMIT = 'abc123';
```

### No Secrets in Source Code

- All API keys and sensitive values are referenced via `window.*` variables
- No hardcoded secrets or credentials
- Configuration is safe for public repositories

### Usage

The configuration is automatically loaded and validated when the page loads.