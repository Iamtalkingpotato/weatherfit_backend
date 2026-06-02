package com.weatherfit.weatherfitBackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.weatherfit.weatherfitBackend.domain.repository.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final CustomerRepository  customerRepository;
    private final PurchaseRepository  purchaseRepository;
    private final TemperatureFeedbackRepository feedbackRepository;

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> result = new HashMap<>();

        result.put("totalCustomers", customerRepository.count());
        result.put("totalPurchases", purchaseRepository.count());
        result.put("totalRevenue",   purchaseRepository.sumTotalRevenue());
        result.put("totalWishlists", 0);
        result.put("totalFeedbacks", feedbackRepository.count());

        Map<String, Long> feedbackDist = new HashMap<>();
        feedbackRepository.findAll().forEach(f -> {
            String key = f.getFeedback();
            if (key != null) {
                feedbackDist.put(key, feedbackDist.getOrDefault(key, 0L) + 1);
            }
        });
        result.put("feedbackDistribution", feedbackDist);

        return result;
    }
}