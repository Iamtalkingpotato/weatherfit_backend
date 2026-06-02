package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "wardrobe_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WardrobeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "category")
    private String category;

    @Column(name = "style")
    private String style;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "color_id")
    private Long colorId;

    @Column(name = "warmth")
    private Integer warmth;

    @Column(name = "registered_date")
    private LocalDate registeredDate;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
