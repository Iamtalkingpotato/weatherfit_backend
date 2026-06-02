package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "behavior_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BehaviorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "page_url")
    private String pageUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "scroll_depth")
    private Integer scrollDepth;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
