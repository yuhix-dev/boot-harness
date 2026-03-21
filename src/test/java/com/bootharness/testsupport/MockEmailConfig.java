package com.bootharness.testsupport;

import com.bootharness.email.EmailRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MockEmailConfig {

  @Bean
  @Primary
  public EmailRepository emailRepository() {
    return Mockito.mock(EmailRepository.class);
  }
}
