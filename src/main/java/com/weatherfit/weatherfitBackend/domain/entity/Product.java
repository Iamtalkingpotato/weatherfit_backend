package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "price")
    private Integer price;

    @Column(name = "style", length = 50)
    private String style;

    @Column(name = "color_id")
    private Long colorId;

    @Column(name = "warmth")
    private Integer warmth;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "in_stock")
    @Builder.Default
    private Boolean inStock = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (inStock == null) inStock = true;
    }
}
