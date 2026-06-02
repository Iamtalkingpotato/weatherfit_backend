package com.weatherfit.weatherfitBackend.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.weatherfit.weatherfitBackend.domain.entity.TemperatureFeedback;
import java.time.LocalDate;
import java.util.List;

public interface TemperatureFeedbackRepository extends JpaRepository<TemperatureFeedback, Long> {
    List<TemperatureFeedback> findByCustomerId(Long customerId);
    int countByCustomerId(Long customerId);
    int countByCustomerIdAndFeedback(Long customerId, String feedback);
    int countByCustomerIdAndFeedbackDateGreaterThanEqual(Long customerId, LocalDate from);
    int countByCustomerIdAndFeedbackAndFeedbackDateGreaterThanEqual(Long customerId, String feedback, LocalDate from);

    @Query("SELECT f.customerId, COUNT(f) FROM TemperatureFeedback f GROUP BY f.customerId")
    List<Object[]> countByCustomer();
}
