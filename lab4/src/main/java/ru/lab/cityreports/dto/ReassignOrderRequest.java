package ru.lab.cityreports.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Тело бизнес-операции "передать наряд другой бригаде". */
public record ReassignOrderRequest(
        @NotNull @Positive Long crewId
) {}
