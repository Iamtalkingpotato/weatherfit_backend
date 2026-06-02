package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.WardrobeItem;
import com.weatherfit.weatherfitBackend.domain.repository.WardrobeItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/wardrobes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WardrobeController {

    private final WardrobeItemRepository wardrobeItemRepository;

    @GetMapping
    public List<WardrobeItem> getAll() {
        return wardrobeItemRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WardrobeItem> getById(@PathVariable Long id) {
        return wardrobeItemRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public List<WardrobeItem> getByCustomer(@PathVariable Long customerId) {
        return wardrobeItemRepository.findByCustomerId(customerId);
    }

    @PostMapping
    public WardrobeItem create(@RequestBody WardrobeItem item) {
        return wardrobeItemRepository.save(item);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WardrobeItem> update(@PathVariable Long id, @RequestBody WardrobeItem item) {
        if (!wardrobeItemRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(wardrobeItemRepository.save(item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!wardrobeItemRepository.existsById(id)) return ResponseEntity.notFound().build();
        wardrobeItemRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
