package com.weatherfit.weatherfitBackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BehaviorPopularPageDto {
    private String pageUrl;
    private long   count;
}
