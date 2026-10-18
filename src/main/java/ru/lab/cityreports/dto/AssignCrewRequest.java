package ru.lab.cityreports.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/** Тело бизнес-операции "назначить бригаду на обращение". */
public record AssignCrewRequest(
        @NotNull @Positive Long crewId,
        @NotNull LocalDate deadline
) {}
