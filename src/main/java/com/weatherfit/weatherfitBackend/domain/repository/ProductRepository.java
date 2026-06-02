package com.weatherfit.weatherfitBackend.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weatherfit.weatherfitBackend.domain.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}