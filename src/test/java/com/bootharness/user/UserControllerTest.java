package com.bootharness.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.bootharness.auth.JwtService;
import com.bootharness.user.dto.UserMeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserControllerTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired TestRestTemplate restTemplate;
  @Autowired UserRepository userRepository;
  @Autowired JwtService jwtService;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  @Test
  void me_returnsProfileForAuthenticatedUser() {
    User user = userRepository.save(User.createLocal("user@example.com", "encoded", "Alice"));
    String token = jwtService.generateAccessToken(user.getId(), user.getEmail());

    var response =
        restTemplate.exchange(
            "/api/v1/users/me",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders(token)),
            UserMeResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().id()).isEqualTo(user.getId());
    assertThat(response.getBody().email()).isEqualTo("user@example.com");
    assertThat(response.getBody().name()).isEqualTo("Alice");
    assertThat(response.getBody().role()).isEqualTo(User.Role.USER.name());
    assertThat(response.getBody().createdAt()).isNotNull();
    assertThat(response.getBody().updatedAt()).isNotNull();
  }

  @Test
  void me_withoutAuth_returns401() {
    var response =
        restTemplate.exchange("/api/v1/users/me", HttpMethod.GET, HttpEntity.EMPTY, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private HttpHeaders bearerHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return headers;
  }
}
