package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anonymous_user")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AnonymousUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anonymous_id", nullable = false, unique = true, length = 36)
    private String anonymousId;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "visit_count")
    private Integer visitCount;

    @Column(name = "first_visit", nullable = false)
    private LocalDateTime firstVisit;

    @Column(name = "last_visit", nullable = false)
    private LocalDateTime lastVisit;

    @Column(name = "popup_shown")
    private Integer popupShown;

    @Column(name = "popup_clicked")
    private Boolean popupClicked;

    @Column(name = "converted")
    private Boolean converted;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (visitCount == null) visitCount = 1;
        if (popupShown == null) popupShown = 0;
        if (popupClicked == null) popupClicked = false;
        if (converted == null) converted = false;
    }
}