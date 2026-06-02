package com.weatherfit.weatherfitBackend.scheduler;

import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerRepository;
import com.weatherfit.weatherfitBackend.domain.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipScheduler {

    private final CustomerRepository customerRepository;
    private final PurchaseRepository purchaseRepository;

    @Scheduled(cron = "0 0 0 1 * *")
    public void updateMembershipLevels() {
        log.info("멤버십 등급 갱신 시작");

        LocalDateTime end = LocalDateTime.now()
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime start = end.minusMonths(4);

        List<Customer> customers = customerRepository.findAll();
        for (Customer customer : customers) {
            int totalAmount = purchaseRepository
                .findByCustomerIdAndStatusAndPurchasedAtBetween(
                    customer.getId(), "PURCHASED", start, end
                )
                .stream()
                .mapToInt(p -> p.getPrice() != null ? p.getPrice() : 0)
                .sum();

            String level;
            if (totalAmount >= 500000)      level = "VIP";
            else if (totalAmount >= 300000) level = "GOLD";
            else if (totalAmount >= 100000) level = "SILVER";
            else                            level = "BASIC";

            customer.setMembershipLevel(level);
            customerRepository.save(customer);
        }

        log.info("멤버십 등급 갱신 완료: {}명 처리", customers.size());
    }
}
