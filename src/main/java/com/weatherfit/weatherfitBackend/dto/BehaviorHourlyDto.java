package com.weatherfit.weatherfitBackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BehaviorHourlyDto {
    private int  hour;
    private long count;
}
