package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.CustomerCoupon;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@RequestMapping("/api/customer-coupons")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CustomerCouponController {

    private final CustomerCouponRepository customerCouponRepository;

    @GetMapping
    public List<CustomerCoupon> getAll() {
        return customerCouponRepository.findAll(Sort.by(Sort.Direction.DESC, "issuedAt"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerCoupon> getById(@PathVariable Long id) {
        return customerCouponRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public List<CustomerCoupon> getByCustomer(@PathVariable Long customerId) {
        return customerCouponRepository.findByCustomerId(customerId);
    }

    @PostMapping
    public CustomerCoupon create(@RequestBody CustomerCoupon customerCoupon) {
        CustomerCoupon newCustomerCoupon = CustomerCoupon.builder()
                .customerId(customerCoupon.getCustomerId())
                .couponId(customerCoupon.getCouponId())
                .usedAt(customerCoupon.getUsedAt())
                .expiredAt(customerCoupon.getExpiredAt())
                .status(customerCoupon.getStatus())
                .orderId(customerCoupon.getOrderId())
                .build();
        return customerCouponRepository.save(newCustomerCoupon);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerCoupon> update(@PathVariable Long id, @RequestBody CustomerCoupon customerCoupon) {
        if (!customerCouponRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(customerCouponRepository.save(customerCoupon));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!customerCouponRepository.existsById(id)) return ResponseEntity.notFound().build();
        customerCouponRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
