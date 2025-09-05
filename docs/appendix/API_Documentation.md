# HuskyApply API Documentation

## Overview

HuskyApply provides a comprehensive RESTful API for AI-powered job application processing. The API is built with Spring Boot and follows OpenAPI 3.0 specifications.

**Base URL**: `https://api.huskyapply.com`  
**API Version**: `v1`  
**Authentication**: JWT Bearer tokens  
**Content-Type**: `application/json`

## Authentication

### Overview
HuskyApply uses JWT (JSON Web Tokens) for API authentication. All protected endpoints require a valid JWT token in the Authorization header.

### Authentication Flow

#### 1. User Registration
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response (201 Created):**
```json
{
  "message": "User registered successfully",
  "userId": "123e4567-e89b-12d3-a456-426614174000"
}
```

**Error Responses:**
- `400 Bad Request`: Invalid email format or weak password
- `409 Conflict`: Email already registered

#### 2. User Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "email": "user@example.com",
    "subscriptionPlan": "Free"
  }
}
```

**Error Responses:**
- `400 Bad Request`: Missing email or password
- `401 Unauthorized`: Invalid credentials

#### 3. Using JWT Tokens
Include the JWT token in the Authorization header for all protected endpoints:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Core API Endpoints

### Job Application Processing

#### Submit Job Application
Generate AI-powered cover letters from job descriptions.

```http
POST /api/v1/applications
Authorization: Bearer {token}
Content-Type: application/json

{
  "jdUrl": "https://example.com/job-posting",
  "resumeUri": "s3://bucket/resume.pdf",
  "preferredModel": "gpt-4o"  // optional
}
```

**Response (202 Accepted):**
```json
{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "PENDING",
  "streamUrl": "/api/v1/applications/123e4567-e89b-12d3-a456-426614174000/stream",
  "estimatedCompletionTime": 120
}
```

**Error Responses:**
- `400 Bad Request`: Invalid URL format or missing required fields
- `401 Unauthorized`: Missing or invalid JWT token
- `429 Too Many Requests`: Rate limit exceeded
- `402 Payment Required`: Subscription quota exceeded

#### Get Job Status
Retrieve current status and details of a job application.

```http
GET /api/v1/applications/{jobId}
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "COMPLETED",
  "jdUrl": "https://example.com/job-posting",
  "resumeUri": "s3://bucket/resume.pdf",
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": "2024-01-15T10:32:30Z",
  "processingTimeSeconds": 150
}
```

**Status Values:**
- `PENDING`: Job submitted, waiting for processing
- `PROCESSING`: AI analysis in progress
- `COMPLETED`: Processing finished successfully
- `FAILED`: Processing failed with errors

#### Get Generated Content
Retrieve the AI-generated cover letter and extracted insights.

```http
GET /api/v1/applications/{jobId}/artifact
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "generatedText": "Dear Hiring Manager,\n\nI am writing to express my strong interest...",
  "wordCount": 287,
  "extractedSkills": [
    "Python",
    "Machine Learning",
    "REST APIs",
    "PostgreSQL"
  ],
  "confidenceScore": "HIGH",
  "generatedAt": "2024-01-15T10:32:30Z"
}
```

**Error Responses:**
- `404 Not Found`: Job not found or content not yet generated
- `401 Unauthorized`: Not authorized to access this job

#### Real-time Status Updates (Server-Sent Events)
Receive real-time updates on job processing status.

```http
GET /api/v1/applications/{jobId}/stream
Authorization: Bearer {token}
Accept: text/event-stream
```

**Event Stream Format:**
```
data: {"event": "processing_started", "status": "PROCESSING", "progress": 10, "timestamp": "2024-01-15T10:30:15Z"}

data: {"event": "analysis_complete", "status": "PROCESSING", "progress": 60, "timestamp": "2024-01-15T10:31:45Z"}

data: {"event": "content_generated", "status": "PROCESSING", "progress": 90, "timestamp": "2024-01-15T10:32:15Z"}

data: {"event": "completed", "status": "COMPLETED", "progress": 100, "timestamp": "2024-01-15T10:32:30Z"}
```

**Event Types:**
- `processing_started`: AI analysis has begun
- `analysis_complete`: Job description analysis finished
- `content_generated`: Cover letter generation completed
- `completed`: All processing finished successfully
- `failed`: Processing failed with error

### Dashboard API

#### Get User Statistics
Retrieve user dashboard statistics and metrics.

```http
GET /api/v1/dashboard/stats
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "totalJobs": 45,
  "completedJobs": 42,
  "failedJobs": 3,
  "averageProcessingTime": 125,
  "currentMonthUsage": {
    "jobsProcessed": 12,
    "quotaLimit": 50,
    "quotaRemaining": 38
  },
  "recentActivity": {
    "last7Days": 8,
    "last30Days": 25
  },
  "topSkills": [
    "Python",
    "JavaScript",
    "AWS",
    "Machine Learning"
  ]
}
```

#### Get User Jobs
Retrieve paginated list of user's job applications with filtering.

```http
GET /api/v1/dashboard/jobs?page=0&size=20&status=COMPLETED&sort=createdAt,desc
Authorization: Bearer {token}
```

**Query Parameters:**
- `page`: Page number (0-based)
- `size`: Number of items per page (max 100)
- `status`: Filter by status (PENDING, PROCESSING, COMPLETED, FAILED)
- `sort`: Sort criteria (createdAt,asc/desc, status,asc/desc)

**Response (200 OK):**
```json
{
  "content": [
    {
      "jobId": "123e4567-e89b-12d3-a456-426614174000",
      "status": "COMPLETED",
      "createdAt": "2024-01-15T10:30:00Z",
      "completedAt": "2024-01-15T10:32:30Z",
      "jobTitle": "Senior Software Engineer",
      "companyName": "Tech Corp",
      "wordCount": 287
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "orderBy": "createdAt",
      "direction": "desc"
    }
  },
  "totalElements": 45,
  "totalPages": 3,
  "last": false,
  "first": true
}
```

#### Get Recent Activity
Retrieve user's recent activity timeline.

```http
GET /api/v1/dashboard/recent-activity?limit=10
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "activities": [
    {
      "id": "act-123",
      "type": "JOB_COMPLETED",
      "message": "Cover letter generated for Senior Developer position",
      "timestamp": "2024-01-15T10:32:30Z",
      "metadata": {
        "jobId": "123e4567-e89b-12d3-a456-426614174000",
        "companyName": "Tech Corp"
      }
    },
    {
      "id": "act-124",
      "type": "JOB_SUBMITTED",
      "message": "New job application submitted",
      "timestamp": "2024-01-15T10:30:00Z",
      "metadata": {
        "jobId": "123e4567-e89b-12d3-a456-426614174000"
      }
    }
  ]
}
```

### Template Management

#### List User Templates
Retrieve user's saved cover letter templates.

```http
GET /api/v1/templates?category=software-engineering
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "templates": [
    {
      "id": "tpl-123",
      "name": "Software Engineer Template",
      "category": "software-engineering",
      "content": "Dear Hiring Manager,\n\nI am excited to apply for the {{position}} role at {{company}}...",
      "createdAt": "2024-01-10T15:20:00Z",
      "updatedAt": "2024-01-12T09:15:00Z",
      "usageCount": 5
    }
  ]
}
```

#### Create Template
Create a new cover letter template.

```http
POST /api/v1/templates
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Marketing Manager Template",
  "category": "marketing",
  "content": "Dear {{hiring_manager}},\n\nI am writing to express my interest in the {{position}} role..."
}
```

**Response (201 Created):**
```json
{
  "id": "tpl-456",
  "name": "Marketing Manager Template",
  "category": "marketing",
  "content": "Dear {{hiring_manager}},\n\nI am writing to express my interest in the {{position}} role...",
  "createdAt": "2024-01-15T11:00:00Z",
  "updatedAt": "2024-01-15T11:00:00Z",
  "usageCount": 0
}
```

#### Update Template
Update an existing template.

```http
PUT /api/v1/templates/{templateId}
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Updated Marketing Template",
  "category": "marketing",
  "content": "Updated template content..."
}
```

#### Delete Template
Delete a template.

```http
DELETE /api/v1/templates/{templateId}
Authorization: Bearer {token}
```

**Response (204 No Content)**

### Batch Processing

#### Submit Batch Job
Process multiple job applications in parallel.

```http
POST /api/v1/batch-jobs
Authorization: Bearer {token}
Content-Type: application/json

{
  "jobUrls": [
    "https://example.com/job1",
    "https://example.com/job2",
    "https://example.com/job3"
  ],
  "resumeUri": "s3://bucket/resume.pdf",
  "preferredModel": "gpt-4o",
  "templateId": "tpl-123"  // optional
}
```

**Response (202 Accepted):**
```json
{
  "batchJobId": "batch-123",
  "status": "PENDING",
  "totalJobs": 3,
  "completedJobs": 0,
  "failedJobs": 0,
  "progress": 0,
  "streamUrl": "/api/v1/batch-jobs/batch-123/stream",
  "estimatedCompletionTime": 360
}
```

#### Get Batch Job Status
Check the status of a batch processing job.

```http
GET /api/v1/batch-jobs/{batchJobId}
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "batchJobId": "batch-123",
  "status": "IN_PROGRESS",
  "totalJobs": 3,
  "completedJobs": 2,
  "failedJobs": 0,
  "progress": 67,
  "createdAt": "2024-01-15T11:00:00Z",
  "startedAt": "2024-01-15T11:00:30Z",
  "estimatedCompletionTime": "2024-01-15T11:06:00Z",
  "individualJobs": [
    {
      "jobId": "job-1",
      "status": "COMPLETED",
      "jdUrl": "https://example.com/job1"
    },
    {
      "jobId": "job-2", 
      "status": "COMPLETED",
      "jdUrl": "https://example.com/job2"
    },
    {
      "jobId": "job-3",
      "status": "PROCESSING",
      "jdUrl": "https://example.com/job3"
    }
  ]
}
```

#### Batch Job Real-time Updates
Monitor batch job progress via Server-Sent Events.

```http
GET /api/v1/batch-jobs/{batchJobId}/stream
Authorization: Bearer {token}
Accept: text/event-stream
```

### Subscription Management

#### List Subscription Plans
Get available subscription plans and pricing.

```http
GET /api/v1/subscriptions/plans
```

**Response (200 OK):**
```json
{
  "plans": [
    {
      "id": "plan-free",
      "name": "Free",
      "priceMonthly": 0.00,
      "maxJobsPerMonth": 5,
      "maxTemplates": 2,
      "aiModelAccess": ["gpt-3.5-turbo"],
      "features": [
        "5 AI-generated cover letters per month",
        "2 saved templates",
        "Basic AI model"
      ]
    },
    {
      "id": "plan-pro",
      "name": "Pro",
      "priceMonthly": 29.99,
      "maxJobsPerMonth": 100,
      "maxTemplates": 20,
      "aiModelAccess": ["gpt-3.5-turbo", "gpt-4o"],
      "features": [
        "100 AI-generated cover letters per month",
        "20 saved templates",
        "Premium AI models",
        "Batch processing",
        "Priority support"
      ]
    },
    {
      "id": "plan-team",
      "name": "Team",
      "priceMonthly": 99.99,
      "maxJobsPerMonth": 500,
      "maxTemplates": 100,
      "aiModelAccess": ["gpt-3.5-turbo", "gpt-4o", "claude-3-sonnet"],
      "features": [
        "500 AI-generated cover letters per month",
        "100 saved templates",
        "All AI models",
        "Advanced batch processing",
        "Team collaboration",
        "Analytics dashboard"
      ]
    }
  ]
}
```

#### Subscribe to Plan
Subscribe to a new plan or upgrade existing subscription.

```http
POST /api/v1/subscriptions/subscribe
Authorization: Bearer {token}
Content-Type: application/json

{
  "planId": "plan-pro",
  "paymentMethodId": "pm_1234567890"  // Stripe payment method ID
}
```

**Response (200 OK):**
```json
{
  "subscriptionId": "sub-123",
  "status": "active",
  "currentPlan": {
    "id": "plan-pro",
    "name": "Pro",
    "priceMonthly": 29.99
  },
  "nextBillingDate": "2024-02-15T00:00:00Z",
  "cancelAtPeriodEnd": false
}
```

#### Get Usage Statistics
Check current subscription usage and limits.

```http
GET /api/v1/subscriptions/usage
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "currentPlan": {
    "id": "plan-pro",
    "name": "Pro",
    "maxJobsPerMonth": 100,
    "maxTemplates": 20
  },
  "currentUsage": {
    "jobsThisMonth": 45,
    "templatesCreated": 12,
    "percentageUsed": 45
  },
  "billingPeriod": {
    "start": "2024-01-01T00:00:00Z",
    "end": "2024-01-31T23:59:59Z",
    "daysRemaining": 16
  },
  "quotaWarnings": []  // Array of quota warnings if approaching limits
}
```

### File Upload

#### Generate Pre-signed URL
Get a pre-signed URL for secure file uploads to S3.

```http
POST /api/v1/uploads/presigned-url
Authorization: Bearer {token}
Content-Type: application/json

{
  "fileName": "resume.pdf",
  "contentType": "application/pdf",
  "fileSize": 1024000
}
```

**Response (200 OK):**
```json
{
  "presignedUrl": "https://s3.amazonaws.com/bucket/uploads/user123/resume.pdf?X-Amz-Algorithm=...",
  "fileUri": "s3://huskyapply-uploads/user123/resume.pdf",
  "expiresIn": 3600,
  "uploadFields": {
    "key": "uploads/user123/resume.pdf",
    "Content-Type": "application/pdf",
    "x-amz-meta-user-id": "123e4567-e89b-12d3-a456-426614174000"
  }
}
```

**File Upload Process:**
1. Get pre-signed URL from API
2. Upload file directly to S3 using returned URL
3. Use the `fileUri` in subsequent API calls

## Rate Limiting

The API implements rate limiting to ensure fair usage and system stability.

### Rate Limit Headers
All API responses include rate limiting headers:

```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642248000
X-RateLimit-Retry-After: 3600
```

### Rate Limits by Endpoint

| Endpoint Category | Rate Limit | Window |
|------------------|------------|--------|
| Authentication | 10 requests | 1 minute |
| Job Processing | 50 requests | 1 hour |
| Dashboard | 200 requests | 1 hour |
| Templates | 100 requests | 1 hour |
| File Upload | 20 requests | 1 hour |

### Rate Limit Exceeded Response
```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1642248000
X-RateLimit-Retry-After: 3600

{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests. Please try again later.",
  "retryAfter": 3600
}
```

## Error Handling

### Error Response Format
All error responses follow a consistent format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error description",
  "details": {
    "field": "specific field that caused the error",
    "code": "FIELD_VALIDATION_ERROR"
  },
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/v1/applications"
}
```

### Common Error Codes

#### Authentication Errors (401)
- `INVALID_TOKEN`: JWT token is malformed or expired
- `TOKEN_EXPIRED`: JWT token has expired
- `UNAUTHORIZED`: Missing or invalid authentication

#### Authorization Errors (403)
- `ACCESS_DENIED`: User doesn't have permission for this resource
- `SUBSCRIPTION_REQUIRED`: Feature requires paid subscription

#### Validation Errors (400)
- `INVALID_REQUEST`: Request body is malformed
- `MISSING_REQUIRED_FIELD`: Required field is missing
- `INVALID_EMAIL_FORMAT`: Email format is invalid
- `INVALID_URL_FORMAT`: URL format is invalid
- `FILE_TOO_LARGE`: Uploaded file exceeds size limit

#### Resource Errors (404)
- `JOB_NOT_FOUND`: Requested job doesn't exist
- `TEMPLATE_NOT_FOUND`: Requested template doesn't exist
- `USER_NOT_FOUND`: User account not found

#### Service Errors (500)
- `INTERNAL_SERVER_ERROR`: Unexpected server error
- `AI_SERVICE_UNAVAILABLE`: AI processing service is down
- `DATABASE_ERROR`: Database operation failed

#### External Service Errors (502/503)
- `AI_PROVIDER_ERROR`: Error from AI service (OpenAI/Anthropic)
- `STORAGE_SERVICE_ERROR`: Error accessing file storage
- `PAYMENT_SERVICE_ERROR`: Error processing payment

## Webhook Events

HuskyApply can send webhook notifications for important events.

### Webhook Configuration
Configure webhook endpoints in your account settings:

```http
POST /api/v1/webhooks
Authorization: Bearer {token}
Content-Type: application/json

{
  "url": "https://your-app.com/webhooks/huskyapply",
  "events": ["job.completed", "job.failed", "subscription.updated"],
  "secret": "your-webhook-secret"
}
```

### Webhook Payload Format
```json
{
  "id": "evt_123456789",
  "type": "job.completed",
  "created": 1642248000,
  "data": {
    "object": {
      "jobId": "123e4567-e89b-12d3-a456-426614174000",
      "status": "COMPLETED",
      "userId": "user-123",
      "completedAt": "2024-01-15T10:32:30Z"
    }
  }
}
```

### Event Types
- `job.completed`: Job processing completed successfully
- `job.failed`: Job processing failed
- `subscription.updated`: User subscription changed
- `subscription.cancelled`: User cancelled subscription
- `quota.warning`: User approaching quota limits

## Code Examples

### JavaScript/Node.js Example
```javascript
const axios = require('axios');

const huskyApply = axios.create({
  baseURL: 'https://api.huskyapply.com',
  headers: {
    'Authorization': `Bearer ${process.env.HUSKYAPPLY_TOKEN}`,
    'Content-Type': 'application/json'
  }
});

// Submit job application
async function submitJob(jdUrl, resumeUri) {
  try {
    const response = await huskyApply.post('/api/v1/applications', {
      jdUrl: jdUrl,
      resumeUri: resumeUri
    });
    
    console.log('Job submitted:', response.data.jobId);
    return response.data.jobId;
  } catch (error) {
    console.error('Error submitting job:', error.response.data);
    throw error;
  }
}

// Monitor job progress with SSE
function monitorJob(jobId) {
  const EventSource = require('eventsource');
  const eventSource = new EventSource(
    `https://api.huskyapply.com/api/v1/applications/${jobId}/stream`,
    {
      headers: {
        'Authorization': `Bearer ${process.env.HUSKYAPPLY_TOKEN}`
      }
    }
  );

  eventSource.onmessage = function(event) {
    const data = JSON.parse(event.data);
    console.log(`Job ${jobId} progress: ${data.progress}% - ${data.event}`);
    
    if (data.event === 'completed') {
      eventSource.close();
      getJobResult(jobId);
    }
  };

  eventSource.onerror = function(error) {
    console.error('SSE error:', error);
    eventSource.close();
  };
}

// Get final result
async function getJobResult(jobId) {
  try {
    const response = await huskyApply.get(`/api/v1/applications/${jobId}/artifact`);
    console.log('Generated cover letter:', response.data.generatedText);
    return response.data;
  } catch (error) {
    console.error('Error getting result:', error.response.data);
    throw error;
  }
}
```

### Python Example
```python
import requests
import json
from sseclient import SSEClient

class HuskyApplyClient:
    def __init__(self, token):
        self.base_url = "https://api.huskyapply.com"
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }
    
    def submit_job(self, jd_url, resume_uri):
        """Submit a job application for processing"""
        response = requests.post(
            f"{self.base_url}/api/v1/applications",
            headers=self.headers,
            json={
                "jdUrl": jd_url,
                "resumeUri": resume_uri
            }
        )
        response.raise_for_status()
        return response.json()
    
    def monitor_job(self, job_id):
        """Monitor job progress via SSE"""
        url = f"{self.base_url}/api/v1/applications/{job_id}/stream"
        messages = SSEClient(url, headers=self.headers)
        
        for msg in messages:
            if msg.data:
                data = json.loads(msg.data)
                print(f"Progress: {data['progress']}% - {data['event']}")
                
                if data['event'] == 'completed':
                    return self.get_job_result(job_id)
                elif data['event'] == 'failed':
                    raise Exception("Job processing failed")
    
    def get_job_result(self, job_id):
        """Get the generated cover letter"""
        response = requests.get(
            f"{self.base_url}/api/v1/applications/{job_id}/artifact",
            headers=self.headers
        )
        response.raise_for_status()
        return response.json()
    
    def get_dashboard_stats(self):
        """Get user dashboard statistics"""
        response = requests.get(
            f"{self.base_url}/api/v1/dashboard/stats",
            headers=self.headers
        )
        response.raise_for_status()
        return response.json()

# Usage example
client = HuskyApplyClient("your-jwt-token")

# Submit job
job_data = client.submit_job(
    "https://example.com/job-posting",
    "s3://bucket/resume.pdf"
)

# Monitor and get result
result = client.monitor_job(job_data['jobId'])
print("Generated cover letter:", result['generatedText'])
```

## Testing and Development

### Sandbox Environment
Use the sandbox environment for testing and development:

**Sandbox Base URL**: `https://api-sandbox.huskyapply.com`

### Test Data
The sandbox includes test data for development:

- Test job URLs that return predictable results
- Mock AI responses for consistent testing
- Webhook endpoint testing tools

### Postman Collection
Import the provided Postman collection for easy API testing:

```bash
curl -o huskyapply-api.postman_collection.json \
  https://docs.huskyapply.com/postman/collection.json
```

---

**API Version**: v1.0  
**Last Updated**: September 1, 2024  
**Support**: [GitHub Issues](https://github.com/xiaojiou176/HuskyApply/issues)