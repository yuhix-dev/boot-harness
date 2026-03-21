package com.bootharness.auth;

import com.bootharness.auth.OauthIdentity.OauthProvider;
import com.bootharness.auth.event.UserRegisteredEvent;
import com.bootharness.user.User;
import com.bootharness.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles OAuth2 user provisioning: find-or-create User and OauthIdentity. */
@Service
@RequiredArgsConstructor
class OAuth2AuthService {

  private final UserRepository userRepository;
  private final OauthIdentityRepository oauthIdentityRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Returns the existing user linked to the given OAuth2 identity, or creates a new one.
   *
   * <p>If a user with the same email already exists (e.g. registered locally), the OAuth2 identity
   * is linked to that account.
   */
  @Transactional
  void findOrCreateUser(String email, String name, OauthProvider provider, String providerId) {
    boolean exists =
        oauthIdentityRepository.findByProviderAndProviderId(provider, providerId).isPresent();
    if (!exists) {
      provisionUser(email, name, provider, providerId);
    }
  }

  private void provisionUser(String email, String name, OauthProvider provider, String providerId) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseGet(
                () -> {
                  User newUser = User.createOAuth(email, name);
                  userRepository.save(newUser);
                  eventPublisher.publishEvent(new UserRegisteredEvent(newUser));
                  return newUser;
                });

    oauthIdentityRepository.save(OauthIdentity.create(user, provider, providerId));
  }
}
