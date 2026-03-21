package com.bootharness.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

  private static final Logger log = LoggerFactory.getLogger("http.outbound");

  @Bean
  public RestClient restClient() {
    return RestClient.builder().requestInterceptor(loggingInterceptor()).build();
  }

  private ClientHttpRequestInterceptor loggingInterceptor() {
    return (request, body, execution) -> {
      long start = System.currentTimeMillis();
      ClientHttpResponse response = execution.execute(request, body);
      log.info(
          "method={} url={} status={} duration={}ms",
          request.getMethod(),
          request.getURI(),
          response.getStatusCode().value(),
          System.currentTimeMillis() - start);
      return response;
    };
  }
}
