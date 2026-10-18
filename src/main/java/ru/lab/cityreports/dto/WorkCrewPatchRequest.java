package ru.lab.cityreports.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.lab.cityreports.model.ObjectType;

import java.util.Set;

public record WorkCrewPatchRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "не должно быть пустым") String name,
        @Size(min = 1, message = "должен быть хотя бы один тип") Set<@NotNull ObjectType> supportedTypes,
        @Positive @Max(100) Integer activeWorkLimit
) {}
