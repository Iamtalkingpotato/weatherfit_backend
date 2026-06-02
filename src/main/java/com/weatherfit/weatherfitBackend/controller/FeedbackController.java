package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.TemperatureFeedback;
import com.weatherfit.weatherfitBackend.domain.repository.TemperatureFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeedbackController {

    private final TemperatureFeedbackRepository temperatureFeedbackRepository;

    @GetMapping
    public List<TemperatureFeedback> getAll() {
        return temperatureFeedbackRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemperatureFeedback> getById(@PathVariable Long id) {
        return temperatureFeedbackRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public List<TemperatureFeedback> getByCustomer(@PathVariable Long customerId) {
        return temperatureFeedbackRepository.findByCustomerId(customerId);
    }

    @PostMapping
    public TemperatureFeedback create(@RequestBody TemperatureFeedback feedback) {
        return temperatureFeedbackRepository.save(feedback);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemperatureFeedback> update(@PathVariable Long id, @RequestBody TemperatureFeedback feedback) {
        if (!temperatureFeedbackRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(temperatureFeedbackRepository.save(feedback));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!temperatureFeedbackRepository.existsById(id)) return ResponseEntity.notFound().build();
        temperatureFeedbackRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
