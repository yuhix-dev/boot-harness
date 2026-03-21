package com.bootharness.auth;

import com.bootharness.auth.OauthIdentity.OauthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthIdentityRepository extends JpaRepository<OauthIdentity, UUID> {

  Optional<OauthIdentity> findByProviderAndProviderId(OauthProvider provider, String providerId);
}
