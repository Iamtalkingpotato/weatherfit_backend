package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.Region;
import com.weatherfit.weatherfitBackend.domain.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RegionController {

    private final RegionRepository regionRepository;

    @GetMapping
    public List<Region> getAll() {
        return regionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Region> getById(@PathVariable Long id) {
        return regionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Region create(@RequestBody Region region) {
        return regionRepository.save(region);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Region> update(@PathVariable Long id, @RequestBody Region region) {
        if (!regionRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(regionRepository.save(region));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!regionRepository.existsById(id)) return ResponseEntity.notFound().build();
        regionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
