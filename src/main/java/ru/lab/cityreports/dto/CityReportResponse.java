package ru.lab.cityreports.dto;

import ru.lab.cityreports.model.ReportStatus;

import java.time.Instant;

public record CityReportResponse(Long id, Long objectId, Long authorId, String description,
                                 ReportStatus status, String rejectionReason,
                                 Instant createdAt, Instant updatedAt) {}
