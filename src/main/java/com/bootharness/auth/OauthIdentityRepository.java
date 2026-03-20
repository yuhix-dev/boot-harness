package com.bootharness.auth;

import com.bootharness.auth.OauthIdentity.OauthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OauthIdentityRepository extends JpaRepository<OauthIdentity, UUID> {

  @Query(
      "SELECT oi FROM OauthIdentity oi JOIN FETCH oi.user"
          + " WHERE oi.provider = :provider AND oi.providerId = :providerId")
  Optional<OauthIdentity> findByProviderAndProviderId(
      @Param("provider") OauthProvider provider, @Param("providerId") String providerId);
}
