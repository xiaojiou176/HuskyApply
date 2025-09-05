package com.huskyapply.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huskyapply.gateway.dto.JobCreationRequest;
import com.huskyapply.gateway.model.Job;
import com.huskyapply.gateway.repository.JobRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class JobControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JobRepository jobRepository;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private RabbitTemplate rabbitTemplate;

  @Test
  public void whenPostValidApplication_thenReturns202AcceptedAndVerifiesInteractions()
      throws Exception {
    // Arrange
    JobCreationRequest request = new JobCreationRequest();
    request.setJdUrl("https://example.com/job-description");
    request.setResumeUri("https://example.com/resume.pdf");

    // Act
    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        // Assert
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").isNotEmpty());

    // Verify database interaction
    List<Job> jobs = jobRepository.findAll();
    assertEquals(1, jobs.size());
    assertEquals("PENDING", jobs.get(0).getStatus());

    // Verify RabbitMQ interaction
    Mockito.verify(rabbitTemplate, Mockito.times(1))
        .convertAndSend(
            Mockito.eq("jobs.exchange"),
            Mockito.eq("jobs.queue"),
            Mockito.any(Object.class),
            Mockito.any(MessagePostProcessor.class));
  }
}
