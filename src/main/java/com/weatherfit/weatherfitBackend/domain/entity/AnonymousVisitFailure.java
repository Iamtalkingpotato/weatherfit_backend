package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anonymous_visit_failures")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AnonymousVisitFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "topic", length = 100)
    private String topic;

    @Column(name = "partition_no")
    private Integer partitionNo;

    @Column(name = "offset_value")
    private Long offsetValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}