package ru.lab.cityreports.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH обращения меняет только описание.
 * Статус меняется исключительно бизнес-операциями (назначение, завершение, отклонение),
 * поэтому поле status из запроса не принимается (неизвестное поле -> 400).
 */
public record CityReportPatchRequest(
        @Size(max = 2000) @Pattern(regexp = "(?s).*\\S.*", message = "не должно быть пустым") String description
) {}
