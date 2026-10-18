package ru.lab.cityreports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.lab.cityreports.model.ObjectState;
import ru.lab.cityreports.model.ObjectType;

public record CityObjectCreateRequest(
        @NotBlank @Size(max = 255) String address,
        @NotNull ObjectType type,
        @NotNull ObjectState state
) {}
