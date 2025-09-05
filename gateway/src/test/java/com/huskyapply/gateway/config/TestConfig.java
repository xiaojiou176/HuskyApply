package com.huskyapply.gateway.config;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

  @MockBean private RabbitTemplate rabbitTemplate;

  @Bean
  @Primary
  public RabbitTemplate testRabbitTemplate() {
    return rabbitTemplate;
  }
}
