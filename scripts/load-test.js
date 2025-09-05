/**
 * HuskyApply K6 Load Testing Suite
 * 
 * Comprehensive performance testing for all critical user journeys
 * Supports multiple test scenarios: smoke, load, stress, spike
 * 
 * Usage:
 *   k6 run scripts/load-test.js                    # Default load test
 *   k6 run -e SCENARIO=smoke scripts/load-test.js  # Smoke test
 *   k6 run -e SCENARIO=stress scripts/load-test.js # Stress test
 *   k6 run -e SCENARIO=spike scripts/load-test.js  # Spike test
 */

import http from 'k6/http';
import { check, group, sleep, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// Environment Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'load';

// Custom Metrics
const loginErrors = new Counter('login_errors_total');
const jobSubmissionErrors = new Counter('job_submission_errors_total');
const jobCompletionRate = new Rate('job_completion_rate');
const dashboardLoadTime = new Trend('dashboard_load_time');
const sseConnectionTime = new Trend('sse_connection_time');

// Test Data
const TEST_USERS = [
  { email: 'loadtest1@huskyapply.com', password: 'LoadTest123!' },
  { email: 'loadtest2@huskyapply.com', password: 'LoadTest123!' },
  { email: 'loadtest3@huskyapply.com', password: 'LoadTest123!' },
  { email: 'loadtest4@huskyapply.com', password: 'LoadTest123!' },
  { email: 'loadtest5@huskyapply.com', password: 'LoadTest123!' },
];

const JOB_URLS = [
  'https://example.com/jobs/software-engineer',
  'https://example.com/jobs/frontend-developer',  
  'https://example.com/jobs/backend-developer',
  'https://example.com/jobs/fullstack-engineer',
  'https://example.com/jobs/devops-engineer',
];

// Test Scenarios Configuration
const scenarios = {
  smoke: {
    executor: 'constant-vus',
    vus: 2,
    duration: '1m',
    tags: { test_type: 'smoke' },
  },
  load: {
    executor: 'ramping-vus',
    stages: [
      { duration: '2m', target: 10 },  // Ramp up
      { duration: '5m', target: 10 },  // Stay at 10 users
      { duration: '2m', target: 20 },  // Ramp to 20 users  
      { duration: '5m', target: 20 },  // Stay at 20 users
      { duration: '2m', target: 0 },   // Ramp down
    ],
    tags: { test_type: 'load' },
  },
  stress: {
    executor: 'ramping-vus',
    stages: [
      { duration: '2m', target: 10 },  // Ramp up to normal load
      { duration: '5m', target: 10 },  // Stay at normal load
      { duration: '2m', target: 20 },  // Ramp up to stress load  
      { duration: '5m', target: 20 },  // Stay at stress load
      { duration: '2m', target: 30 },  // Ramp up to breaking point
      { duration: '5m', target: 30 },  // Stay at breaking point
      { duration: '2m', target: 40 },  // Beyond normal capacity
      { duration: '5m', target: 40 },  // Stay at high stress
      { duration: '10m', target: 0 },  // Gradual ramp down
    ],
    tags: { test_type: 'stress' },
  },
  spike: {
    executor: 'ramping-vus',
    stages: [
      { duration: '1m', target: 10 },  // Normal load
      { duration: '1m', target: 50 },  // Sudden spike
      { duration: '30s', target: 50 }, // Maintain spike
      { duration: '1m', target: 10 },  // Return to normal
      { duration: '1m', target: 100 }, // Bigger spike
      { duration: '30s', target: 100 },// Maintain bigger spike
      { duration: '2m', target: 0 },   // Ramp down
    ],
    tags: { test_type: 'spike' },
  },
};

// Export test configuration
export const options = {
  scenarios: {
    [SCENARIO]: scenarios[SCENARIO] || scenarios.load
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'], // 95% of requests under 2s
    http_req_failed: ['rate<0.05'],    // Error rate under 5%
    login_errors_total: ['count<10'],   // Less than 10 login errors
    job_submission_errors_total: ['count<5'], // Less than 5 submission errors
    job_completion_rate: ['rate>0.9'],  // 90%+ job completion rate
    dashboard_load_time: ['p(95)<1000'], // Dashboard loads under 1s
  },
  tags: {
    environment: 'load-test',
    version: '2.0.0',
  },
};

// Utility Functions
function getRandomUser() {
  return TEST_USERS[Math.floor(Math.random() * TEST_USERS.length)];
}

function getRandomJobUrl() {
  return JOB_URLS[Math.floor(Math.random() * JOB_URLS.length)];
}

function createAuthHeaders(token) {
  return {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

function handleHttpError(response, operation) {
  if (!check(response, {
    [`${operation} status is 2xx`]: (r) => r.status >= 200 && r.status < 300,
  })) {
    console.error(`${operation} failed:`, response.status, response.body);
    return false;
  }
  return true;
}

// Authentication Helper
function authenticateUser(user) {
  const loginResponse = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: user.email,
    password: user.password
  }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { operation: 'login' },
  });

  if (!handleHttpError(loginResponse, 'Login')) {
    loginErrors.add(1);
    return null;
  }

  const loginData = loginResponse.json();
  return loginData.token;
}

// Main Test Function
export default function() {
  const user = getRandomUser();
  
  group('Authentication Flow', () => {
    // Health check first
    group('Health Check', () => {
      const healthResponse = http.get(`${BASE_URL}/actuator/health`, {
        tags: { operation: 'health_check' },
      });
      
      check(healthResponse, {
        'health check status is UP': (r) => r.json('status') === 'UP',
        'health check response time < 500ms': (r) => r.timings.duration < 500,
      });
    });

    // User authentication
    const token = authenticateUser(user);
    if (!token) {
      fail('Authentication failed - aborting test');
      return;
    }

    group('Dashboard Operations', () => {
      const dashboardStart = Date.now();
      
      // Get dashboard stats
      const statsResponse = http.get(`${BASE_URL}/api/v1/dashboard/stats`, {
        headers: createAuthHeaders(token),
        tags: { operation: 'dashboard_stats' },
      });
      
      if (handleHttpError(statsResponse, 'Dashboard Stats')) {
        const dashboardTime = Date.now() - dashboardStart;
        dashboardLoadTime.add(dashboardTime);
        
        check(statsResponse, {
          'dashboard has required fields': (r) => {
            const data = r.json();
            return data.totalJobs !== undefined && 
                   data.successRate !== undefined &&
                   data.averageProcessingTime !== undefined;
          },
        });
      }

      // Get recent jobs
      const jobsResponse = http.get(`${BASE_URL}/api/v1/dashboard/jobs?page=0&size=10`, {
        headers: createAuthHeaders(token),
        tags: { operation: 'dashboard_jobs' },
      });
      
      handleHttpError(jobsResponse, 'Dashboard Jobs');

      // Get recent activity  
      const activityResponse = http.get(`${BASE_URL}/api/v1/dashboard/recent-activity`, {
        headers: createAuthHeaders(token),
        tags: { operation: 'recent_activity' },
      });
      
      handleHttpError(activityResponse, 'Recent Activity');
    });

    group('Job Application Workflow', () => {
      // Submit job application
      const jobData = {
        jobUrl: getRandomJobUrl(),
        resumeUri: `s3://huskyapply-test/resume-${user.email}.pdf`,
        preferences: {
          tone: 'professional',
          length: 'medium',
          aiModel: 'gpt-4o'
        }
      };

      const submitResponse = http.post(`${BASE_URL}/api/v1/applications`, 
        JSON.stringify(jobData), {
          headers: createAuthHeaders(token),
          tags: { operation: 'job_submission' },
        });

      if (!handleHttpError(submitResponse, 'Job Submission')) {
        jobSubmissionErrors.add(1);
        return;
      }

      const jobResult = submitResponse.json();
      const jobId = jobResult.jobId || jobResult.id;

      if (!jobId) {
        console.error('No job ID returned from submission');
        jobSubmissionErrors.add(1);
        return;
      }

      // Check job status
      sleep(1); // Brief wait for job to be processed
      
      const statusResponse = http.get(`${BASE_URL}/api/v1/applications/${jobId}`, {
        headers: createAuthHeaders(token),
        tags: { operation: 'job_status' },
      });

      if (handleHttpError(statusResponse, 'Job Status')) {
        const statusData = statusResponse.json();
        jobCompletionRate.add(statusData.status === 'COMPLETED' ? 1 : 0);
        
        check(statusResponse, {
          'job has valid status': (r) => {
            const data = r.json();
            return ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'].includes(data.status);
          },
        });

        // If job is completed, try to get artifact
        if (statusData.status === 'COMPLETED') {
          const artifactResponse = http.get(`${BASE_URL}/api/v1/applications/${jobId}/artifact`, {
            headers: createAuthHeaders(token),
            tags: { operation: 'job_artifact' },
          });

          if (handleHttpError(artifactResponse, 'Job Artifact')) {
            check(artifactResponse, {
              'artifact has generated content': (r) => {
                const data = r.json();
                return data.generatedText && data.generatedText.length > 0;
              },
              'artifact has word count': (r) => {
                const data = r.json();
                return data.wordCount && data.wordCount > 0;
              },
            });
          }
        }
      }
    });

    group('Template Operations', () => {
      // Get templates
      const templatesResponse = http.get(`${BASE_URL}/api/v1/templates`, {
        headers: createAuthHeaders(token),
        tags: { operation: 'get_templates' },
      });

      handleHttpError(templatesResponse, 'Get Templates');

      // Create a test template
      const templateData = {
        name: `Load Test Template ${Date.now()}`,
        category: 'test',
        content: 'Test template content for load testing'
      };

      const createTemplateResponse = http.post(`${BASE_URL}/api/v1/templates`, 
        JSON.stringify(templateData), {
          headers: createAuthHeaders(token),
          tags: { operation: 'create_template' },
        });

      if (handleHttpError(createTemplateResponse, 'Create Template')) {
        const templateResult = createTemplateResponse.json();
        const templateId = templateResult.id;

        // Clean up - delete the test template
        if (templateId) {
          http.del(`${BASE_URL}/api/v1/templates/${templateId}`, {
            headers: createAuthHeaders(token),
            tags: { operation: 'delete_template' },
          });
        }
      }
    });

    group('Subscription Information', () => {
      // Get subscription plans (public endpoint)
      const plansResponse = http.get(`${BASE_URL}/api/v1/subscriptions/plans`, {
        tags: { operation: 'subscription_plans' },
      });

      check(plansResponse, {
        'subscription plans available': (r) => r.status === 200,
        'plans have pricing info': (r) => {
          const plans = r.json();
          return Array.isArray(plans) && plans.length > 0;
        },
      });

      // Get user usage stats
      const usageResponse = http.get(`${BASE_URL}/api/v1/subscriptions/usage`, {
        headers: createAuthHeaders(token),
        tags: { operation: 'usage_stats' },
      });

      handleHttpError(usageResponse, 'Usage Stats');
    });
  });

  // Random sleep between iterations (1-3 seconds)
  sleep(Math.random() * 2 + 1);
}

// Lifecycle Functions
export function setup() {
  console.log(`🚀 Starting ${SCENARIO.toUpperCase()} test against ${BASE_URL}`);
  
  // Verify API is accessible
  const healthResponse = http.get(`${BASE_URL}/actuator/health`);
  if (healthResponse.status !== 200) {
    throw new Error(`API health check failed: ${healthResponse.status}`);
  }
  
  console.log('✅ API health check passed');
  return { startTime: Date.now() };
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`🏁 Test completed in ${duration.toFixed(2)} seconds`);
}

// Custom HTML Report Generation
export function handleSummary(data) {
  const reportPath = `reports/load-test-${SCENARIO}-${Date.now()}.html`;
  
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    [reportPath]: htmlReport(data),
    'reports/load-test-summary.json': JSON.stringify(data),
  };
}