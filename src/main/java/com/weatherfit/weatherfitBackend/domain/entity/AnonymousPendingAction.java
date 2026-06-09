package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anonymous_pending_action")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnonymousPendingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anonymous_id", nullable = false, length = 100)
    private String anonymousId;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
