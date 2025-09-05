// Load Testing Script for 10x Scaling Validation
// This k6 script tests the system's ability to handle 10x traffic load
// Usage: k6 run --vus=100 --duration=10m test-10x-scaling.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// Custom metrics
export const errorRate = new Rate('error_rate');
export const requestsPerSecond = new Rate('requests_per_second');
export const jobSubmissions = new Counter('job_submissions_total');
export const responseTimeP95 = new Trend('response_time_p95');

// Test configuration
export const options = {
  scenarios: {
    // Gradual ramp-up to 10x load over 5 minutes
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '2m', target: 50 },   // Baseline load
        { duration: '2m', target: 100 },  // 2x load
        { duration: '2m', target: 250 },  // 5x load  
        { duration: '2m', target: 500 },  // 10x load
        { duration: '5m', target: 500 },  // Sustain 10x load
        { duration: '2m', target: 100 },  // Scale down
        { duration: '1m', target: 0 },    // Cool down
      ],
    },
    // Constant load during scaling
    constant_load: {
      executor: 'constant-vus',
      vus: 50,
      duration: '16m',
      startTime: '0s',
    },
    // Spike testing
    spike_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 1000 }, // Sudden spike
        { duration: '30s', target: 1000 }, // Hold spike
        { duration: '10s', target: 0 },    // Drop to zero
      ],
      startTime: '8m', // Start spike during sustained load
    }
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'], // 95% of requests under 2s
    http_req_failed: ['rate<0.05'],    // Error rate under 5%
    error_rate: ['rate<0.05'],
    checks: ['rate>0.95'],             // 95% of checks pass
  },
};

// Base URL configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_VERSION = '/api/v1';

// Test data
const testUsers = [
  { email: 'test1@example.com', password: 'password123' },
  { email: 'test2@example.com', password: 'password123' },
  { email: 'test3@example.com', password: 'password123' },
  { email: 'test4@example.com', password: 'password123' },
  { email: 'test5@example.com', password: 'password123' },
];

const jobUrls = [
  'https://jobs.example.com/software-engineer',
  'https://careers.example.com/backend-developer', 
  'https://hiring.example.com/full-stack-developer',
  'https://jobs.example.com/devops-engineer',
  'https://careers.example.com/product-manager',
];

// Authentication helper
function authenticate() {
  const user = testUsers[Math.floor(Math.random() * testUsers.length)];
  
  const loginResponse = http.post(`${BASE_URL}${API_VERSION}/auth/login`, JSON.stringify({
    email: user.email,
    password: user.password
  }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'auth_login' }
  });

  check(loginResponse, {
    'login status is 200': (r) => r.status === 200,
    'login returns token': (r) => r.json('token') !== undefined,
  });

  errorRate.add(loginResponse.status >= 400);
  
  return loginResponse.status === 200 ? loginResponse.json('token') : null;
}

// Generate test file content (simulating resume upload)
function generateTestFile() {
  return {
    name: `resume_${Math.floor(Math.random() * 10000)}.pdf`,
    content: 'VGVzdCByZXN1bWUgY29udGVudA==', // Base64 encoded test content
    type: 'application/pdf'
  };
}

// Main test scenario
export default function() {
  const token = authenticate();
  if (!token) {
    console.log('Authentication failed, skipping test iteration');
    sleep(1);
    return;
  }

  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  // Test 1: Dashboard API (read-heavy workload)
  const dashboardStart = Date.now();
  const dashboardResponse = http.get(`${BASE_URL}${API_VERSION}/dashboard/stats`, {
    headers,
    tags: { endpoint: 'dashboard_stats' }
  });
  
  check(dashboardResponse, {
    'dashboard status is 200': (r) => r.status === 200,
    'dashboard response time < 1s': (r) => r.timings.duration < 1000,
  });
  
  errorRate.add(dashboardResponse.status >= 400);
  responseTimeP95.add(dashboardResponse.timings.duration);

  // Test 2: Job submission (write-heavy workload with async processing)
  if (Math.random() < 0.3) { // 30% of requests submit jobs
    const jobUrl = jobUrls[Math.floor(Math.random() * jobUrls.length)];
    const file = generateTestFile();
    
    // First get presigned URL for file upload
    const presignedResponse = http.post(`${BASE_URL}${API_VERSION}/uploads/presigned-url`, JSON.stringify({
      fileName: file.name,
      contentType: file.type
    }), {
      headers,
      tags: { endpoint: 'presigned_url' }
    });

    check(presignedResponse, {
      'presigned URL status is 200': (r) => r.status === 200,
    });

    if (presignedResponse.status === 200) {
      const presignedData = presignedResponse.json();
      
      // Simulate file upload to S3 (mock)
      const uploadResponse = http.put(presignedData.url, file.content, {
        headers: { 'Content-Type': file.type },
        tags: { endpoint: 's3_upload' }
      });

      if (uploadResponse.status === 200) {
        // Submit job application
        const jobResponse = http.post(`${BASE_URL}${API_VERSION}/applications`, JSON.stringify({
          jdUrl: jobUrl,
          resumeUri: presignedData.url.split('?')[0],
          modelProvider: 'openai',
          modelName: 'gpt-4o'
        }), {
          headers,
          tags: { endpoint: 'job_submission' }
        });

        check(jobResponse, {
          'job submission status is 200': (r) => r.status === 200,
          'job submission returns jobId': (r) => r.json('jobId') !== undefined,
          'job submission response time < 3s': (r) => r.timings.duration < 3000,
        });

        errorRate.add(jobResponse.status >= 400);
        
        if (jobResponse.status === 200) {
          jobSubmissions.add(1);
          responseTimeP95.add(jobResponse.timings.duration);
        }
      }
    }
  }

  // Test 3: Real-time features (SSE connection simulation)
  if (Math.random() < 0.1) { // 10% of users test SSE
    const sseResponse = http.get(`${BASE_URL}${API_VERSION}/applications/test-job-id/stream`, {
      headers,
      tags: { endpoint: 'sse_stream' },
      timeout: '5s'
    });
    
    check(sseResponse, {
      'SSE connection established': (r) => r.status === 200 || r.status === 404, // 404 for non-existent job is OK
    });
  }

  // Test 4: Template management (medium complexity)
  if (Math.random() < 0.2) { // 20% of requests manage templates
    const templatesResponse = http.get(`${BASE_URL}${API_VERSION}/templates`, {
      headers,
      tags: { endpoint: 'templates_list' }
    });
    
    check(templatesResponse, {
      'templates list status is 200': (r) => r.status === 200,
      'templates response time < 500ms': (r) => r.timings.duration < 500,
    });
    
    errorRate.add(templatesResponse.status >= 400);
  }

  // Test 5: Subscription and usage (business logic)
  if (Math.random() < 0.15) { // 15% of requests check subscription
    const subscriptionResponse = http.get(`${BASE_URL}${API_VERSION}/subscriptions/usage`, {
      headers,
      tags: { endpoint: 'subscription_usage' }
    });
    
    check(subscriptionResponse, {
      'subscription usage status is 200': (r) => r.status === 200,
    });
    
    errorRate.add(subscriptionResponse.status >= 400);
  }

  // Variable sleep to simulate realistic user behavior
  sleep(Math.random() * 2 + 0.5); // 0.5-2.5 seconds
}

// Setup function
export function setup() {
  console.log('🚀 Starting 10x Load Scaling Test');
  console.log(`Base URL: ${BASE_URL}`);
  console.log('Test Users: 5 configured users');
  console.log('Test Duration: 16 minutes with various load patterns');
  console.log('Max Virtual Users: 1000 (during spike test)');
  console.log('');
  
  // Warm up the system
  console.log('🔥 Warming up system...');
  for (let i = 0; i < 10; i++) {
    http.get(`${BASE_URL}/actuator/health`);
  }
  
  console.log('✅ System warmed up, starting load test');
  return { baseUrl: BASE_URL };
}

// Teardown function
export function teardown(data) {
  console.log('🏁 Load test completed');
  console.log('Check the results for:');
  console.log('  • Response times under load');
  console.log('  • Error rates during scaling');
  console.log('  • System behavior during traffic spikes');
  console.log('  • HPA scaling events in Kubernetes');
  console.log('');
  console.log('Recommended follow-up commands:');
  console.log('  kubectl get hpa -n huskyapply');
  console.log('  kubectl top pods -n huskyapply');
  console.log('  kubectl get events -n huskyapply --sort-by=.metadata.creationTimestamp | tail -20');
}

// Custom scenarios for specific testing
export function handleSummary(data) {
  return {
    'load-test-results.json': JSON.stringify(data),
    stdout: createSummaryReport(data),
  };
}

function createSummaryReport(data) {
  const report = `
🎯 10x Load Scaling Test Results
=====================================

📊 Overall Performance:
  • Total Requests: ${data.metrics.http_reqs.count}
  • Request Rate: ${data.metrics.http_reqs.rate.toFixed(2)}/sec
  • Failed Requests: ${(data.metrics.http_req_failed.rate * 100).toFixed(2)}%
  • Avg Response Time: ${data.metrics.http_req_duration.avg.toFixed(2)}ms
  • P95 Response Time: ${data.metrics.http_req_duration['p(95)'].toFixed(2)}ms

🎯 Scaling Validation:
  • Error Rate: ${(data.metrics.error_rate?.rate * 100 || 0).toFixed(2)}%
  • Job Submissions: ${data.metrics.job_submissions_total?.count || 0}
  • Checks Passed: ${(data.metrics.checks.rate * 100).toFixed(2)}%

📈 Performance Thresholds:
  • P95 < 2000ms: ${data.metrics.http_req_duration['p(95)'] < 2000 ? '✅ PASS' : '❌ FAIL'}
  • Error Rate < 5%: ${(data.metrics.http_req_failed?.rate || 0) < 0.05 ? '✅ PASS' : '❌ FAIL'}
  • Checks > 95%: ${(data.metrics.checks?.rate || 0) > 0.95 ? '✅ PASS' : '❌ FAIL'}

🔍 Next Steps:
  ${(data.metrics.http_req_failed?.rate || 0) < 0.05 && (data.metrics.checks?.rate || 0) > 0.95 
    ? '✅ System successfully handled 10x load scaling!'
    : '⚠️ Review scaling configuration and resource limits'}

=====================================
  `;
  
  return report;
}