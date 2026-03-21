package com.bootharness.email;

import com.bootharness.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

@Repository
@RequiredArgsConstructor
public class ResendEmailRepository implements EmailRepository {

  private static final Logger log = LoggerFactory.getLogger(ResendEmailRepository.class);
  private static final String API_URL = "https://api.resend.com/emails";

  private final RestClient restClient = RestClient.create();
  private final AppProperties appProperties;

  @Override
  public void send(String to, String subject, String htmlBody) {
    restClient
        .post()
        .uri(API_URL)
        .header("Authorization", "Bearer " + appProperties.email().resendApiKey())
        .body(
            new ResendRequest(
                appProperties.email().fromAddress(), new String[] {to}, subject, htmlBody))
        .retrieve()
        .toBodilessEntity();

    log.info("email sent to={} subject={}", to, subject);
  }

  private record ResendRequest(String from, String[] to, String subject, String html) {}
}
