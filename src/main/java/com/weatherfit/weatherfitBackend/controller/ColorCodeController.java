package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.ColorCode;
import com.weatherfit.weatherfitBackend.domain.repository.ColorCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/colors")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ColorCodeController {

    private final ColorCodeRepository colorCodeRepository;

    @GetMapping
    public List<ColorCode> getAll() {
        return colorCodeRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ColorCode> getById(@PathVariable Long id) {
        return colorCodeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ColorCode create(@RequestBody ColorCode colorCode) {
        return colorCodeRepository.save(colorCode);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ColorCode> update(@PathVariable Long id, @RequestBody ColorCode colorCode) {
        if (!colorCodeRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(colorCodeRepository.save(colorCode));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!colorCodeRepository.existsById(id)) return ResponseEntity.notFound().build();
        colorCodeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
