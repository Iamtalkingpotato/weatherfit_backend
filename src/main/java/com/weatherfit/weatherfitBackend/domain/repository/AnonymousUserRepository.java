package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;


public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, Long> {
    Optional<AnonymousUser> findByAnonymousId(String anonymousId);

    @Modifying
@Transactional
@Query(value = "UPDATE wardrobe_item SET customer_id = :customerId, anonymous_id = NULL WHERE anonymous_id = :anonymousId", nativeQuery = true)
void linkWardrobeItems(@Param("customerId") Long customerId, @Param("anonymousId") String anonymousId);

@Modifying
@Transactional
@Query(value = "UPDATE temperature_feedback SET customer_id = :customerId, anonymous_id = NULL WHERE anonymous_id = :anonymousId", nativeQuery = true)
void linkFeedbacks(@Param("customerId") Long customerId, @Param("anonymousId") String anonymousId);

@Modifying
@Transactional
@Query(value = "UPDATE purchase SET customer_id = :customerId, anonymous_id = NULL WHERE anonymous_id = :anonymousId", nativeQuery = true)
void linkPurchases(@Param("customerId") Long customerId, @Param("anonymousId") String anonymousId);
}