package ru.lab.cityreports.dto;

import ru.lab.cityreports.model.ObjectState;
import ru.lab.cityreports.model.ObjectType;

import java.time.Instant;

public record CityObjectResponse(Long id, String address, ObjectType type, ObjectState state,
                                 Instant createdAt, Instant updatedAt) {}
