package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anonymous_visits")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AnonymousVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anonymous_id", nullable = false, length = 36)
    private String anonymousId;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "page_url", length = 200)
    private String pageUrl;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "visited_at", nullable = false)
    private LocalDateTime visitedAt;

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