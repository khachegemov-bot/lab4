package ru.lab.cityreports.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.lab.cityreports.model.ObjectState;
import ru.lab.cityreports.model.ObjectType;

public record CityObjectPatchRequest(
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "не должно быть пустым") String address,
        ObjectType type,
        ObjectState state
) {}
