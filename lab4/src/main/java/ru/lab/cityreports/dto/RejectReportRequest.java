package ru.lab.cityreports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Тело бизнес-операции "отклонить обращение". */
public record RejectReportRequest(
        @NotBlank @Size(max = 1000) String reason
) {}
