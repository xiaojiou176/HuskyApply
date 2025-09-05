# HuskyApply Development Guide

## Overview

This guide provides comprehensive information for developers working on HuskyApply, including setup instructions, coding standards, architecture patterns, and contribution guidelines.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Development Environment Setup](#development-environment-setup)
3. [Project Structure](#project-structure)
4. [Coding Standards](#coding-standards)
5. [Architecture Guidelines](#architecture-guidelines)
6. [Testing Strategy](#testing-strategy)
7. [Database Development](#database-development)
8. [API Development](#api-development)
9. [Frontend Development](#frontend-development)
10. [CI/CD Pipeline](#cicd-pipeline)
11. [Security Guidelines](#security-guidelines)
12. [Performance Guidelines](#performance-guidelines)
13. [Contribution Guidelines](#contribution-guidelines)
14. [Code Review Process](#code-review-process)

## Getting Started

### Prerequisites

**Required Software:**
- Java 17+ JDK (OpenJDK or Oracle)
- Python 3.11+
- Node.js 18+
- Docker & Docker Compose
- Git

**Recommended Tools:**
- Maven 3.9+ (for Java builds)
- UV package manager (for Python)
- Postman (for API testing)
- DBeaver (for database management)
- IDE: IntelliJ IDEA (recommended) or VS Code
- Kubernetes CLI (kubectl) for deployment testing

### Quick Setup (5 minutes)

**1. Clone and Setup**
```bash
# Clone repository
git clone https://github.com/your-org/huskyapply.git
cd huskyapply

# Set up environment
cp infra/.env.example infra/.env
# Edit infra/.env with your API keys (OpenAI, Anthropic, etc.)
```

**2. Start Infrastructure Services**
```bash
# Start PostgreSQL, RabbitMQ, and Redis
cd infra
docker-compose up -d postgres-db rabbitmq-server redis
```

**3. Start All Services (in separate terminals)**
```bash
# Terminal 1: Start Gateway service
cd gateway
mvn spring-boot:run

# Terminal 2: Start Brain service
cd brain
uv sync
uv run python main.py

# Terminal 3: Start Frontend
cd frontend
npm install && npm start
```

### Verify Your Setup

```bash
# Check services are running
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8000/healthz         # Brain
curl http://localhost:3000                 # Frontend

# Submit a test job (requires JWT token)
curl -X POST http://localhost:8080/api/v1/applications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"jdUrl": "https://example.com/job-posting"}'
```

### Environment Configuration

```bash
# Generate secure secrets for .env file
openssl rand -base64 32  # For JWT_SECRET_KEY
openssl rand -base64 24  # For INTERNAL_API_KEY

# Set up Git hooks
git config core.hooksPath .githooks
chmod +x .githooks/*

# Install pre-commit hooks
pip install pre-commit
pre-commit install
```

### Next Steps for New Developers

1. **Architecture Overview**: Read [Architecture Documentation](architecture.md) to understand system design
2. **API Integration**: Check [API Documentation](API_Documentation.md) for endpoint details  
3. **Explore Documentation**: Review the comprehensive project documentation
4. **Review Project History**: See [Project History](project-history/Project_History.md) for development evolution

## Development Environment Setup

### Local Development with Docker

```bash
# Start infrastructure services
cd infra
docker-compose up -d postgres-db rabbitmq-server redis

# Verify services are running
docker-compose ps
```

### Java Gateway Service

```bash
cd gateway

# Install dependencies
mvn clean install

# Run tests
mvn test

# Start development server
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Alternative: Run with hot reload
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

### Python Brain Service

```bash
cd brain

# Install UV package manager
curl -LsSf https://astral.sh/uv/install.sh | sh

# Create virtual environment and install dependencies
uv sync --extra test --extra dev

# Run tests
uv run pytest

# Start development server
uv run python main.py

# Alternative: Run with auto-reload
uv run python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Frontend Development

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm start

# Run with live reload
npm run dev
```

### Full Stack Development

```bash
# Start all services
./scripts/dev-start.sh

# Stop all services
./scripts/dev-stop.sh

# Reset development environment
./scripts/dev-reset.sh
```

## Project Structure

### Repository Layout

```
huskyapply/
├── gateway/                 # Java Spring Boot service
│   ├── src/main/java/      # Java source code
│   ├── src/main/resources/ # Configuration and migrations
│   ├── src/test/java/      # Test code
│   ├── Dockerfile          # Container build
│   └── pom.xml             # Maven configuration
├── brain/                  # Python FastAPI service
│   ├── main.py             # Application entry point
│   ├── ai_chain.py         # AI processing logic
│   ├── tests/              # Test files
│   ├── Dockerfile          # Container build
│   └── pyproject.toml      # Python configuration
├── frontend/               # Vanilla JavaScript frontend
│   ├── index.html          # Main page
│   ├── app.js              # Core application logic
│   ├── styles.css          # Styling
│   └── assets/             # Static assets
├── k8s/                    # Kubernetes manifests
├── infra/                  # Infrastructure as code
├── scripts/                # Deployment and utility scripts
├── docs/                   # Documentation
├── .github/                # GitHub Actions workflows
└── README.md               # Project overview
```

### Gateway Service Structure

```
gateway/src/main/java/com/huskyapply/gateway/
├── controller/             # REST API endpoints
│   ├── AuthController.java
│   ├── JobController.java
│   ├── DashboardController.java
│   └── ...
├── service/                # Business logic layer
│   ├── AuthService.java
│   ├── JobService.java
│   └── ...
├── repository/             # Data access layer
│   ├── UserRepository.java
│   ├── JobRepository.java
│   └── ...
├── model/                  # JPA entities
│   ├── User.java
│   ├── Job.java
│   └── ...
├── dto/                    # Data transfer objects
│   ├── request/
│   └── response/
├── config/                 # Configuration classes
│   ├── SecurityConfig.java
│   ├── DatabaseConfig.java
│   └── ...
├── security/               # Security components
└── exception/              # Exception handling
```

## Coding Standards

### Java Conventions

**Package Naming:**
```java
// Follow reverse domain notation
com.huskyapply.gateway.controller
com.huskyapply.gateway.service
com.huskyapply.gateway.model
```

**Class Naming:**
```java
// Controllers: [Resource]Controller
@RestController
public class JobController {
    
    // Services: [Resource]Service
    @Autowired
    private JobService jobService;
    
    // DTOs: [Resource][Request|Response]DTO
    public ResponseEntity<JobResponseDTO> createJob(
        @RequestBody JobRequestDTO request
    ) {
        // Method naming: verb + object
        Job job = jobService.createJob(request);
        return ResponseEntity.ok(JobResponseDTO.from(job));
    }
}
```

**Annotation Standards:**
```java
@RestController
@RequestMapping("/api/v1/jobs")
@Validated
@Slf4j
public class JobController {
    
    @Autowired
    private final JobService jobService;
    
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<JobResponseDTO> createJob(
        @Valid @RequestBody JobRequestDTO request,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        log.info("Creating job for user: {}", user.getId());
        // Implementation
    }
}
```

### Python Conventions

**Follow PEP 8 standards:**
```python
# File naming: snake_case
# ai_chain.py, message_handler.py

# Class naming: PascalCase
class AIContentGenerator:
    """AI content generation service."""
    
    def __init__(self, model_provider: str):
        self.model_provider = model_provider
        self.logger = logging.getLogger(__name__)
    
    # Method naming: snake_case
    async def generate_cover_letter(
        self, 
        job_description: str, 
        user_resume: Optional[str] = None
    ) -> CoverLetterResult:
        """Generate personalized cover letter."""
        try:
            # Implementation
            pass
        except Exception as e:
            self.logger.error(f"Failed to generate content: {e}")
            raise ProcessingException(f"Content generation failed") from e
```

**Type Hints:**
```python
from typing import Dict, List, Optional, Union
from pydantic import BaseModel

class JobAnalysisRequest(BaseModel):
    job_url: str
    user_id: str
    resume_content: Optional[str] = None
    preferred_model: str = "gpt-3.5-turbo"

async def analyze_job_description(
    request: JobAnalysisRequest
) -> Dict[str, Union[str, List[str]]]:
    """Analyze job description and extract requirements."""
    return {
        "skills": ["Python", "FastAPI", "PostgreSQL"],
        "experience_level": "senior",
        "industry": "technology"
    }
```

### JavaScript Conventions

**ES6+ Standards:**
```javascript
// Use const/let, avoid var
const API_BASE_URL = 'https://api.huskyapply.com';
let currentJobId = null;

// Arrow functions for callbacks
const handleJobSubmission = async (jobData) => {
    try {
        const response = await fetch(`${API_BASE_URL}/applications`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${authToken}`
            },
            body: JSON.stringify(jobData)
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        return await response.json();
    } catch (error) {
        console.error('Job submission failed:', error);
        throw error;
    }
};

// Class-based components
class JobManager {
    constructor(apiClient) {
        this.apiClient = apiClient;
        this.jobs = new Map();
    }
    
    async submitJob(jobData) {
        const job = await this.apiClient.createJob(jobData);
        this.jobs.set(job.id, job);
        return job;
    }
}
```

### Documentation Standards

**Javadoc:**
```java
/**
 * Service for managing job application processing and AI content generation.
 * 
 * <p>This service coordinates between job submission, AI processing, and result storage.
 * It handles the complete lifecycle of a job application from initial submission
 * through AI-powered content generation to final result delivery.</p>
 * 
 * @author Yifeng Yu
 * @version 1.0
 * @since 2024-01-15
 */
@Service
public class JobService {
    
    /**
     * Submits a new job application for AI processing.
     * 
     * @param request the job submission request containing URL and user details
     * @param user the authenticated user submitting the job
     * @return the created job with assigned ID and initial status
     * @throws ValidationException if the job request is invalid
     * @throws QuotaExceededException if user has exceeded their monthly limit
     */
    public Job submitJob(JobRequestDTO request, UserPrincipal user) 
        throws ValidationException, QuotaExceededException {
        // Implementation
    }
}
```

**Python Docstrings:**
```python
class AIContentGenerator:
    """AI-powered content generation service.
    
    This class handles the generation of personalized cover letters using
    various AI models (OpenAI, Anthropic). It implements a multi-stage
    processing pipeline with job analysis, content generation, and quality
    assurance.
    
    Attributes:
        model_provider: The AI model provider to use ('openai', 'anthropic')
        max_retries: Maximum number of retry attempts for failed generations
    """
    
    async def generate_cover_letter(
        self,
        job_description: str,
        user_resume: Optional[str] = None,
        template: Optional[str] = None
    ) -> CoverLetterResult:
        """Generate a personalized cover letter for a job application.
        
        Args:
            job_description: The job posting text or URL
            user_resume: Optional resume content for personalization
            template: Optional base template to customize
            
        Returns:
            CoverLetterResult: Contains generated text, metadata, and quality scores
            
        Raises:
            AIProviderException: When AI service fails or returns invalid response
            ValidationException: When input parameters are invalid
            ProcessingException: When content generation pipeline fails
        """
        pass
```

## Architecture Guidelines

### Service Communication

**Synchronous Communication (HTTP):**
```java
// Use for immediate responses and queries
@RestController
public class JobController {
    
    @GetMapping("/{id}")
    public ResponseEntity<JobResponseDTO> getJob(@PathVariable String id) {
        Job job = jobService.findById(id);
        return ResponseEntity.ok(JobResponseDTO.from(job));
    }
}
```

**Asynchronous Communication (RabbitMQ):**
```java
// Use for long-running processes
@Component
public class JobMessagePublisher {
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    public void publishJobForProcessing(Job job) {
        JobProcessingMessage message = JobProcessingMessage.builder()
            .jobId(job.getId())
            .jdUrl(job.getJdUrl())
            .resumeUri(job.getResumeUri())
            .userId(job.getUser().getId())
            .build();
            
        rabbitTemplate.convertAndSend(
            "huskyapply.direct", 
            "job.process", 
            message
        );
    }
}
```

### Error Handling

**Global Exception Handler:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.builder()
                .error("VALIDATION_ERROR")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .build());
    }
    
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuotaExceeded(QuotaExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
            .body(ErrorResponse.builder()
                .error("QUOTA_EXCEEDED")
                .message("Monthly application limit reached")
                .timestamp(Instant.now())
                .build());
    }
}
```

**Python Error Handling:**
```python
from fastapi import HTTPException
from fastapi.responses import JSONResponse

class AIProviderException(Exception):
    """Exception raised when AI provider fails."""
    def __init__(self, message: str, provider: str):
        self.message = message
        self.provider = provider
        super().__init__(self.message)

@app.exception_handler(AIProviderException)
async def ai_provider_exception_handler(request, exc):
    return JSONResponse(
        status_code=502,
        content={
            "error": "AI_PROVIDER_ERROR",
            "message": f"AI service failed: {exc.message}",
            "provider": exc.provider
        }
    )
```

### Configuration Management

**Spring Boot Configuration:**
```java
@ConfigurationProperties(prefix = "huskyapply")
@Data
@Component
public class HuskyApplyProperties {
    
    private Ai ai = new Ai();
    private Storage storage = new Storage();
    private Queue queue = new Queue();
    
    @Data
    public static class Ai {
        private String openaiApiKey;
        private String anthropicApiKey;
        private String defaultModel = "gpt-3.5-turbo";
        private int timeoutSeconds = 120;
    }
    
    @Data
    public static class Storage {
        private String s3Bucket;
        private String s3Region;
        private long maxFileSize = 10 * 1024 * 1024; // 10MB
    }
    
    @Data
    public static class Queue {
        private String jobProcessingQueue = "job.processing.queue";
        private int maxRetries = 3;
    }
}
```

**Python Configuration:**
```python
from pydantic import BaseSettings, Field
from typing import Optional

class Settings(BaseSettings):
    """Application settings loaded from environment variables."""
    
    # Database
    database_url: str = Field(..., env="DATABASE_URL")
    
    # AI Services
    openai_api_key: str = Field(..., env="OPENAI_API_KEY")
    anthropic_api_key: Optional[str] = Field(None, env="ANTHROPIC_API_KEY")
    default_ai_model: str = Field("gpt-3.5-turbo", env="DEFAULT_AI_MODEL")
    
    # RabbitMQ
    rabbitmq_host: str = Field("localhost", env="RABBITMQ_HOST")
    rabbitmq_port: int = Field(5672, env="RABBITMQ_PORT")
    
    # Application
    log_level: str = Field("INFO", env="LOG_LEVEL")
    max_processing_time: int = Field(300, env="MAX_PROCESSING_TIME")
    
    class Config:
        env_file = ".env"
        case_sensitive = False

settings = Settings()
```

## Testing Strategy

### Unit Testing

**Java with JUnit 5 and Mockito:**
```java
@ExtendWith(MockitoExtension.class)
class JobServiceTest {
    
    @Mock
    private JobRepository jobRepository;
    
    @Mock
    private UserService userService;
    
    @Mock
    private RabbitTemplate rabbitTemplate;
    
    @InjectMocks
    private JobService jobService;
    
    @Test
    void shouldCreateJobSuccessfully() {
        // Given
        JobRequestDTO request = JobRequestDTO.builder()
            .jdUrl("https://example.com/job")
            .build();
        
        UserPrincipal user = UserPrincipal.builder()
            .id("user-123")
            .email("test@example.com")
            .build();
        
        when(jobRepository.save(any(Job.class)))
            .thenAnswer(invocation -> {
                Job job = invocation.getArgument(0);
                job.setId("job-123");
                return job;
            });
        
        // When
        Job result = jobService.submitJob(request, user);
        
        // Then
        assertThat(result.getId()).isEqualTo("job-123");
        assertThat(result.getJdUrl()).isEqualTo(request.getJdUrl());
        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
        
        verify(rabbitTemplate).convertAndSend(
            eq("huskyapply.direct"),
            eq("job.process"),
            any(JobProcessingMessage.class)
        );
    }
    
    @Test
    void shouldThrowQuotaExceededException() {
        // Given
        UserPrincipal user = UserPrincipal.builder()
            .id("user-123")
            .monthlyQuota(5)
            .currentUsage(5)
            .build();
        
        // When & Then
        assertThrows(QuotaExceededException.class, () -> {
            jobService.submitJob(JobRequestDTO.builder().build(), user);
        });
    }
}
```

**Python with pytest and pytest-asyncio:**
```python
import pytest
from unittest.mock import AsyncMock, MagicMock
from ai_chain import AIContentGenerator, AIProviderException

@pytest.fixture
def ai_generator():
    return AIContentGenerator(model_provider="openai")

@pytest.fixture
def mock_openai_client():
    return AsyncMock()

@pytest.mark.asyncio
async def test_generate_cover_letter_success(ai_generator, mock_openai_client):
    # Given
    job_description = "Software Engineer position at Tech Corp"
    expected_content = "Dear Hiring Manager, I am excited to apply..."
    
    mock_openai_client.chat.completions.create.return_value = MagicMock(
        choices=[MagicMock(message=MagicMock(content=expected_content))]
    )
    ai_generator._get_client = MagicMock(return_value=mock_openai_client)
    
    # When
    result = await ai_generator.generate_cover_letter(job_description)
    
    # Then
    assert result.generated_text == expected_content
    assert result.word_count > 0
    assert result.confidence_score == "HIGH"

@pytest.mark.asyncio
async def test_generate_cover_letter_ai_failure(ai_generator, mock_openai_client):
    # Given
    job_description = "Test job description"
    mock_openai_client.chat.completions.create.side_effect = Exception("API Error")
    ai_generator._get_client = MagicMock(return_value=mock_openai_client)
    
    # When & Then
    with pytest.raises(AIProviderException) as exc_info:
        await ai_generator.generate_cover_letter(job_description)
    
    assert "API Error" in str(exc_info.value)
```

### Integration Testing

**Spring Boot Integration Tests:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.rabbitmq.host=localhost",
    "spring.rabbitmq.port=5672"
})
class JobControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private JobRepository jobRepository;
    
    @MockBean
    private RabbitTemplate rabbitTemplate;
    
    @Test
    void shouldSubmitJobSuccessfully() {
        // Given
        JobRequestDTO request = JobRequestDTO.builder()
            .jdUrl("https://example.com/job")
            .build();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateValidJwtToken());
        HttpEntity<JobRequestDTO> entity = new HttpEntity<>(request, headers);
        
        // When
        ResponseEntity<JobResponseDTO> response = restTemplate.postForEntity(
            "/api/v1/applications", 
            entity, 
            JobResponseDTO.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getJobId()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
        
        // Verify database persistence
        Optional<Job> savedJob = jobRepository.findById(response.getBody().getJobId());
        assertThat(savedJob).isPresent();
        assertThat(savedJob.get().getJdUrl()).isEqualTo(request.getJdUrl());
    }
}
```

### End-to-End Testing

**Frontend E2E Tests with Cypress:**
```javascript
describe('Job Application Flow', () => {
    beforeEach(() => {
        cy.login('test@example.com', 'password123');
        cy.visit('/dashboard');
    });
    
    it('should submit job application and show progress', () => {
        // Submit job
        cy.get('[data-cy=new-application-btn]').click();
        cy.get('[data-cy=job-url-input]')
            .type('https://example.com/software-engineer-job');
        cy.get('[data-cy=submit-job-btn]').click();
        
        // Verify submission
        cy.get('[data-cy=success-message]')
            .should('contain', 'Job submitted successfully');
        
        // Monitor progress
        cy.get('[data-cy=progress-bar]').should('be.visible');
        cy.get('[data-cy=progress-text]')
            .should('contain', 'Analyzing job requirements');
        
        // Wait for completion (mock SSE events)
        cy.wait(3000);
        cy.get('[data-cy=completion-message]')
            .should('contain', 'Cover letter generated');
        
        // View results
        cy.get('[data-cy=view-result-btn]').click();
        cy.get('[data-cy=generated-content]').should('not.be.empty');
        cy.get('[data-cy=copy-btn]').should('be.visible');
    });
});
```

## Database Development

### Migration Guidelines

**Flyway Migration Naming:**
```
V001__Create_initial_schema.sql
V002__Add_user_authentication.sql
V003__Add_job_processing.sql
V004__Add_subscription_system.sql
```

**Migration Best Practices:**
```sql
-- V005__Add_user_preferences.sql

-- Always use IF NOT EXISTS for safety
CREATE TABLE IF NOT EXISTS user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ai_model_preference VARCHAR(50) DEFAULT 'gpt-3.5-turbo',
    notification_enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_preferences_user_id 
ON user_preferences(user_id);

-- Add constraints
ALTER TABLE user_preferences 
ADD CONSTRAINT chk_ai_model_valid 
CHECK (ai_model_preference IN ('gpt-3.5-turbo', 'gpt-4o', 'claude-3-sonnet'));

-- Update trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_preferences_updated_at
    BEFORE UPDATE ON user_preferences
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();
```

### JPA Entity Guidelines

```java
@Entity
@Table(name = "user_preferences", indexes = {
    @Index(name = "idx_user_preferences_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_model_preference")
    @Builder.Default
    private AIModel aiModelPreference = AIModel.GPT_3_5_TURBO;
    
    @Column(name = "notification_enabled")
    @Builder.Default
    private Boolean notificationEnabled = true;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

## API Development

### RESTful API Design

**Resource Naming:**
```java
// Good: Plural nouns for collections
GET    /api/v1/applications
POST   /api/v1/applications
GET    /api/v1/applications/{id}
PUT    /api/v1/applications/{id}
DELETE /api/v1/applications/{id}

// Good: Nested resources
GET    /api/v1/applications/{id}/artifacts
POST   /api/v1/applications/{id}/artifacts

// Good: Action endpoints (when needed)
POST   /api/v1/applications/{id}/regenerate
POST   /api/v1/applications/{id}/cancel
```

**HTTP Status Codes:**
```java
@PostMapping("/applications")
public ResponseEntity<JobResponseDTO> createApplication(
    @Valid @RequestBody JobRequestDTO request
) {
    Job job = jobService.createJob(request);
    
    return ResponseEntity
        .status(HttpStatus.ACCEPTED)  // 202 for async processing
        .location(URI.create("/api/v1/applications/" + job.getId()))
        .body(JobResponseDTO.from(job));
}

@GetMapping("/applications/{id}")
public ResponseEntity<JobResponseDTO> getApplication(@PathVariable String id) {
    Optional<Job> job = jobService.findById(id);
    
    return job.map(j -> ResponseEntity.ok(JobResponseDTO.from(j)))
              .orElse(ResponseEntity.notFound().build());  // 404
}
```

### API Versioning

```java
@RestController
@RequestMapping("/api/v1")  // Version in URL path
public class JobControllerV1 {
    // V1 implementation
}

@RestController
@RequestMapping("/api/v2")
public class JobControllerV2 {
    // V2 implementation with breaking changes
}

// Alternative: Header-based versioning
@GetMapping(value = "/applications", headers = "API-Version=1")
public ResponseEntity<List<JobResponseDTO>> getApplicationsV1() {
    // V1 implementation
}
```

### OpenAPI Documentation

```java
@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Job Applications", description = "AI-powered job application processing")
public class JobController {
    
    @PostMapping
    @Operation(
        summary = "Submit job application",
        description = "Submit a job description URL for AI-powered cover letter generation"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "202",
            description = "Job submitted successfully",
            content = @Content(schema = @Schema(implementation = JobResponseDTO.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded"
        )
    })
    public ResponseEntity<JobResponseDTO> submitJob(
        @Parameter(description = "Job submission request") 
        @Valid @RequestBody JobRequestDTO request,
        @Parameter(hidden = true) 
        @AuthenticationPrincipal UserPrincipal user
    ) {
        // Implementation
    }
}
```

## Frontend Development

### Component Architecture

```javascript
// Base Component class
class Component {
    constructor(element) {
        this.element = element;
        this.state = {};
        this.init();
    }
    
    init() {
        this.setupEventListeners();
        this.render();
    }
    
    setState(newState) {
        this.state = { ...this.state, ...newState };
        this.render();
    }
    
    render() {
        // Override in subclasses
    }
    
    setupEventListeners() {
        // Override in subclasses
    }
}

// Job submission component
class JobSubmissionForm extends Component {
    constructor(element) {
        super(element);
        this.state = {
            jobUrl: '',
            isSubmitting: false,
            error: null
        };
    }
    
    setupEventListeners() {
        this.element.addEventListener('submit', this.handleSubmit.bind(this));
        
        const urlInput = this.element.querySelector('#job-url');
        urlInput.addEventListener('input', (e) => {
            this.setState({ jobUrl: e.target.value, error: null });
        });
    }
    
    async handleSubmit(e) {
        e.preventDefault();
        
        this.setState({ isSubmitting: true, error: null });
        
        try {
            const result = await APIClient.submitJob({
                jdUrl: this.state.jobUrl
            });
            
            // Navigate to progress page
            window.location.href = `/job/${result.jobId}`;
        } catch (error) {
            this.setState({ 
                error: error.message, 
                isSubmitting: false 
            });
        }
    }
    
    render() {
        const submitBtn = this.element.querySelector('#submit-btn');
        const errorDiv = this.element.querySelector('#error-message');
        
        submitBtn.disabled = this.state.isSubmitting;
        submitBtn.textContent = this.state.isSubmitting ? 'Processing...' : 'Submit Job';
        
        if (this.state.error) {
            errorDiv.textContent = this.state.error;
            errorDiv.style.display = 'block';
        } else {
            errorDiv.style.display = 'none';
        }
    }
}
```

### API Client

```javascript
class APIClient {
    constructor() {
        this.baseURL = process.env.API_BASE_URL || 'https://api.huskyapply.com';
        this.token = localStorage.getItem('auth_token');
    }
    
    async request(endpoint, options = {}) {
        const url = `${this.baseURL}${endpoint}`;
        
        const config = {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        };
        
        if (this.token) {
            config.headers.Authorization = `Bearer ${this.token}`;
        }
        
        try {
            const response = await fetch(url, config);
            
            if (!response.ok) {
                const error = await response.json();
                throw new APIError(error.message, response.status, error.error);
            }
            
            return await response.json();
        } catch (error) {
            if (error instanceof APIError) {
                throw error;
            }
            throw new APIError('Network error occurred', 0, 'NETWORK_ERROR');
        }
    }
    
    async submitJob(jobData) {
        return this.request('/api/v1/applications', {
            method: 'POST',
            body: JSON.stringify(jobData)
        });
    }
    
    async getJob(jobId) {
        return this.request(`/api/v1/applications/${jobId}`);
    }
    
    createSSEConnection(jobId) {
        const url = `${this.baseURL}/api/v1/applications/${jobId}/stream?token=${this.token}`;
        return new EventSource(url);
    }
}

class APIError extends Error {
    constructor(message, status, code) {
        super(message);
        this.name = 'APIError';
        this.status = status;
        this.code = code;
    }
}
```

## CI/CD Pipeline

### GitHub Actions Workflow

```yaml
# .github/workflows/ci.yml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  test-gateway:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_PASSWORD: test
          POSTGRES_DB: huskyapply_test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up Java 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Cache Maven dependencies
      uses: actions/cache@v4
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Run Gateway tests
      run: |
        cd gateway
        mvn clean test
        mvn jacoco:report
    
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v4
      with:
        file: gateway/target/site/jacoco/jacoco.xml

  test-brain:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up Python 3.11
      uses: actions/setup-python@v5
      with:
        python-version: '3.11'
    
    - name: Install UV
      run: curl -LsSf https://astral.sh/uv/install.sh | sh
    
    - name: Install dependencies
      run: |
        cd brain
        uv sync --extra test
    
    - name: Run Brain tests
      run: |
        cd brain
        uv run pytest --cov=. --cov-report=xml
    
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v4
      with:
        file: brain/coverage.xml

  build-images:
    needs: [test-gateway, test-brain]
    runs-on: ubuntu-latest
    if: github.event_name == 'push'
    
    permissions:
      contents: read
      packages: write
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Log in to Container Registry
      uses: docker/login-action@v3
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    
    - name: Build and push Gateway image
      uses: docker/build-push-action@v5
      with:
        context: ./gateway
        push: true
        tags: |
          ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/gateway:latest
          ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/gateway:${{ github.sha }}
    
    - name: Build and push Brain image
      uses: docker/build-push-action@v5
      with:
        context: ./brain
        push: true
        tags: |
          ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/brain:latest
          ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/brain:${{ github.sha }}

  deploy-staging:
    needs: build-images
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/develop'
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Deploy to staging
      run: |
        echo "Deploying to staging environment"
        # Add deployment script here
```

### Pre-commit Hooks

```bash
#!/bin/sh
# .githooks/pre-commit

set -e

echo "Running pre-commit checks..."

# Java code formatting
echo "Checking Java code formatting..."
cd gateway
mvn spotless:check
cd ..

# Python code formatting
echo "Checking Python code formatting..."
cd brain
uv run black --check .
uv run isort --check-only .
uv run flake8 .
cd ..

# Frontend linting
echo "Checking JavaScript code..."
cd frontend
npm run lint
cd ..

echo "All pre-commit checks passed!"
```

## Security Guidelines

### Authentication and Authorization

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/api/v1/internal/**").hasRole("SYSTEM")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter(), 
                UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler()))
            .build();
    }
}
```

### Input Validation

```java
@Data
@Builder
public class JobRequestDTO {
    
    @NotBlank(message = "Job URL is required")
    @URL(message = "Invalid URL format")
    @Size(max = 2000, message = "URL too long")
    private String jdUrl;
    
    @Size(max = 100, message = "Resume URI too long")
    private String resumeUri;
    
    @Pattern(
        regexp = "gpt-3.5-turbo|gpt-4o|claude-3-sonnet",
        message = "Invalid AI model"
    )
    private String preferredModel = "gpt-3.5-turbo";
}
```

### Sensitive Data Handling

```java
// Never log sensitive data
@Slf4j
public class PaymentService {
    
    public PaymentResult processPayment(PaymentRequest request) {
        // DON'T DO THIS:
        // log.info("Processing payment: {}", request);
        
        // DO THIS:
        log.info("Processing payment for user: {}, amount: {}", 
            request.getUserId(), request.getAmount());
        
        // Mask sensitive data in responses
        PaymentResult result = PaymentResult.builder()
            .transactionId(transactionId)
            .cardLastFour(request.getCardNumber().substring(
                request.getCardNumber().length() - 4))
            .build();
        
        return result;
    }
}
```

## Performance Guidelines

### Database Optimization

```java
// Use pagination for large result sets
public Page<Job> getUserJobs(String userId, Pageable pageable) {
    return jobRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
}

// Use projection for read-only data
public interface JobSummaryProjection {
    String getId();
    String getJdUrl();
    JobStatus getStatus();
    Instant getCreatedAt();
}

// Optimize N+1 queries with fetch joins
@Query("SELECT j FROM Job j LEFT JOIN FETCH j.artifacts WHERE j.user.id = :userId")
List<Job> findJobsWithArtifactsByUserId(@Param("userId") String userId);
```

### Caching Strategy

```java
@Service
@CacheConfig(cacheNames = "dashboard")
public class DashboardService {
    
    @Cacheable(key = "#userId + '-stats-' + #date")
    public DashboardStats getUserStats(String userId, LocalDate date) {
        // Expensive computation
        return computeUserStats(userId, date);
    }
    
    @CacheEvict(key = "#userId + '-stats-*'", condition = "#result.success")
    public JobResult processJob(String userId, Job job) {
        // Processing logic
        return result;
    }
}
```

### Async Processing

```java
@Service
public class NotificationService {
    
    @Async("taskExecutor")
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3)
    public CompletableFuture<Void> sendNotification(
        String userId, 
        NotificationMessage message
    ) {
        // Send notification asynchronously
        emailService.sendEmail(userId, message);
        return CompletableFuture.completedFuture(null);
    }
}
```

## Contribution Guidelines

### Getting Started

1. **Fork and Clone:**
   ```bash
   git fork https://github.com/your-org/huskyapply.git
   git clone https://github.com/your-username/huskyapply.git
   cd huskyapply
   git remote add upstream https://github.com/your-org/huskyapply.git
   ```

2. **Create Feature Branch:**
   ```bash
   git checkout -b feature/add-template-management
   git checkout -b bugfix/fix-sse-connection-drops
   git checkout -b docs/update-api-documentation
   ```

3. **Make Changes:**
   - Follow coding standards
   - Add appropriate tests
   - Update documentation
   - Follow commit message conventions

### Commit Message Convention

```bash
# Format: type(scope): description

# Examples:
feat(gateway): add template management endpoints
fix(brain): resolve AI provider timeout issues
docs(api): update authentication documentation
test(frontend): add e2e tests for job submission
refactor(gateway): extract payment logic to service
perf(brain): optimize AI processing pipeline
chore(deps): update Spring Boot to 3.2.10
```

### Pull Request Process

1. **Before Creating PR:**
   ```bash
   # Update from upstream
   git fetch upstream
   git rebase upstream/main
   
   # Run tests
   ./scripts/test-all.sh
   
   # Check formatting
   ./scripts/check-format.sh
   ```

2. **PR Template:**
   ```markdown
   ## Description
   Brief description of changes made.
   
   ## Type of Change
   - [ ] Bug fix
   - [ ] New feature
   - [ ] Breaking change
   - [ ] Documentation update
   
   ## Testing
   - [ ] Unit tests pass
   - [ ] Integration tests pass
   - [ ] Manual testing completed
   
   ## Checklist
   - [ ] Code follows project style guidelines
   - [ ] Self-review completed
   - [ ] Documentation updated
   - [ ] No new warnings introduced
   ```

3. **Review Process:**
   - At least 2 approvals required
   - All CI checks must pass
   - No merge conflicts
   - Documentation updated if needed

### Issue Reporting

**Bug Report Template:**
```markdown
**Bug Description**
Clear description of the bug.

**Steps to Reproduce**
1. Go to '...'
2. Click on '...'
3. Scroll down to '...'
4. See error

**Expected Behavior**
What should happen.

**Screenshots**
If applicable, add screenshots.

**Environment**
- Browser: [e.g. Chrome 91]
- OS: [e.g. macOS 12.0]
- Version: [e.g. v1.0.0]
```

**Feature Request Template:**
```markdown
**Feature Description**
Clear description of the proposed feature.

**Problem Statement**
What problem does this solve?

**Proposed Solution**
Detailed description of the solution.

**Alternatives Considered**
Alternative solutions you've considered.

**Additional Context**
Screenshots, mockups, or other context.
```

## Code Review Process

### Review Checklist

**Functionality:**
- [ ] Code works as intended
- [ ] Edge cases are handled
- [ ] Error handling is appropriate
- [ ] Business logic is correct

**Code Quality:**
- [ ] Code is readable and well-structured
- [ ] Functions are appropriately sized
- [ ] Variable and function names are clear
- [ ] No code duplication

**Testing:**
- [ ] Appropriate test coverage
- [ ] Tests are meaningful and well-written
- [ ] All tests pass
- [ ] No flaky tests introduced

**Security:**
- [ ] Input validation is present
- [ ] No sensitive data in logs
- [ ] Authentication/authorization correct
- [ ] No SQL injection vulnerabilities

**Performance:**
- [ ] No unnecessary database calls
- [ ] Appropriate caching used
- [ ] No memory leaks
- [ ] Efficient algorithms used

### Review Comments

**Good Review Comments:**
```
# Constructive feedback
"Consider using a Map instead of multiple if-else statements for better maintainability."

"This method is doing too much. Consider breaking it into smaller, focused methods."

"Great error handling! Consider adding a specific exception type for this case."

# Questions for clarification
"Why did you choose this approach over using the existing UserService method?"

"Could this cause a race condition if multiple requests come in simultaneously?"
```

**Poor Review Comments:**
```
# Avoid these
"This is wrong."
"Use better variable names."
"I don't like this approach."
```

---

**Development Guide Version**: 1.0  
**Last Updated**: September 1, 2024  
**Maintainer**: Yifeng Yu  
**Questions?**: Contact [GitHub Discussions](https://github.com/xiaojiou176/HuskyApply/discussions)