package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousPendingAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnonymousPendingActionRepository extends JpaRepository<AnonymousPendingAction, Long> {
    List<AnonymousPendingAction> findByAnonymousId(String anonymousId);
    void deleteByAnonymousId(String anonymousId);
}
