package ru.lab.cityreports.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/** Статус при создании не принимаем: новый наряд всегда PLANNED. */
public record WorkOrderCreateRequest(
        @NotNull @Positive Long reportId,
        @NotNull @Positive Long crewId,
        @NotNull LocalDate deadline
) {}
