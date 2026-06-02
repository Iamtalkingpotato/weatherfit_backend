package com.weatherfit.weatherfitBackend.domain.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Override
    @EntityGraph(attributePaths = "region")
    List<Customer> findAll();

    boolean existsByEmail(String email);

    @Query("SELECT c FROM Customer c WHERE c.emailConsent = true")
    List<Customer> findAllEmailConsentTrue();
}
