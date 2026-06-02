package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "temperature_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemperatureFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "feedback")
    private String feedback;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "feedback_date")
    private LocalDate feedbackDate;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "feels_like_temp")
    private Double feelsLikeTemp;

    @Column(name = "humidity")
    private Integer humidity;

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "weather_condition")
    private String weatherCondition;

    @Column(name = "recommended_outfit", columnDefinition = "TEXT")
    private String recommendedOutfit;

    @Column(name = "region_name")
    private String regionName;

}
