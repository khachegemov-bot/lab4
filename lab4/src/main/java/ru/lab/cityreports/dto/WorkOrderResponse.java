package ru.lab.cityreports.dto;

import ru.lab.cityreports.model.WorkOrderStatus;

import java.time.Instant;
import java.time.LocalDate;

public record WorkOrderResponse(Long id, Long reportId, Long crewId, LocalDate deadline,
                                WorkOrderStatus status, String resultDescription,
                                Instant createdAt, Instant updatedAt,
                                Instant startedAt, Instant completedAt) {}
