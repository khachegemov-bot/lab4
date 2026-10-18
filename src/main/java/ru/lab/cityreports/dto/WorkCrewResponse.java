package ru.lab.cityreports.dto;

import ru.lab.cityreports.model.ObjectType;

import java.time.Instant;
import java.util.Set;

public record WorkCrewResponse(Long id, String name, Set<ObjectType> supportedTypes, Integer activeWorkLimit,
                               long activeWorkCount, Instant createdAt, Instant updatedAt) {}
