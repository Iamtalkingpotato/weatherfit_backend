package com.weatherfit.weatherfitBackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BehaviorConversionDto {
    private long   sessionCount;
    private double avgDuration;
    private double avgScrollDepth;
    private double purchaseConversionRate;
}
