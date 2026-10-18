package ru.lab.cityreports.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.lab.cityreports.model.ObjectType;

import java.util.Set;

public record WorkCrewCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotEmpty Set<@NotNull ObjectType> supportedTypes,
        @NotNull @Positive @Max(100) Integer activeWorkLimit
) {}
