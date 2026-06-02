package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "process_fail_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessFailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;

    @Column(nullable = false, length = 100)
    private String topic;

    private Integer partitionNo;

    private Long offsetValue;

    @Column(length = 100)
    private String consumerGroup;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawMessage;

    @Column(length = 255)
    private String failReason;

    @Column(length = 50)
    private String failType;

    @Column
    private Integer retryCount = 0;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Builder
    public ProcessFailLog(Long customerId, String topic, Integer partitionNo,
                          Long offsetValue, String consumerGroup,
                          String rawMessage, String failReason, String failType,
                          Integer retryCount, LocalDateTime processedAt) {
        this.customerId    = customerId;
        this.topic         = topic;
        this.partitionNo   = partitionNo;
        this.offsetValue   = offsetValue;
        this.consumerGroup = consumerGroup;
        this.rawMessage    = rawMessage;
        this.failReason    = failReason;
        this.failType      = failType;
        this.retryCount    = retryCount != null ? retryCount : 0;
        this.processedAt   = processedAt;
    }
}
