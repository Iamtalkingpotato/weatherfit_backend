package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.Purchase;
import com.weatherfit.weatherfitBackend.domain.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/purchases")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PurchaseController {

    private final PurchaseRepository purchaseRepository;

    @GetMapping
    public List<Purchase> getAll() {
        return purchaseRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Purchase> getById(@PathVariable Long id) {
        return purchaseRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public List<Purchase> getByCustomer(@PathVariable Long customerId) {
        return purchaseRepository.findByCustomerId(customerId);
    }

    @PostMapping
    public Purchase create(@RequestBody Purchase purchase) {
        return purchaseRepository.save(purchase);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Purchase> update(@PathVariable Long id, @RequestBody Purchase purchase) {
        if (!purchaseRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(purchaseRepository.save(purchase));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!purchaseRepository.existsById(id)) return ResponseEntity.notFound().build();
        purchaseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
