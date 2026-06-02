package com.weatherfit.weatherfitBackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BehaviorWishlistConversionDto {
    private long   wishlistCount;
    private long   purchaseCount;
    private double conversionRate;
}
