package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "color_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColorCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "color_name", length = 50)
    private String colorName;

    @Column(name = "hex_code", length = 10)
    private String hexCode;
}
