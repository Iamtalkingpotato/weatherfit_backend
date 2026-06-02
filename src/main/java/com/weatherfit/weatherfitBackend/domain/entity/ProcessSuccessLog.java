package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "process_success_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessSuccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;

    @Column(nullable = false, length = 100)
    private String topic;

    private Integer partitionNo;

    private Long offsetValue;

    @Column(length = 50)
    private String dataType;

    @Column(length = 100)
    private String consumerGroup;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawMessage;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Builder
    public ProcessSuccessLog(Long customerId, String topic, Integer partitionNo,
                             Long offsetValue, String dataType, String consumerGroup,
                             String rawMessage, LocalDateTime processedAt) {
        this.customerId    = customerId;
        this.topic         = topic;
        this.partitionNo   = partitionNo;
        this.offsetValue   = offsetValue;
        this.dataType      = dataType;
        this.consumerGroup = consumerGroup;
        this.rawMessage    = rawMessage;
        this.processedAt   = processedAt;
    }
}
