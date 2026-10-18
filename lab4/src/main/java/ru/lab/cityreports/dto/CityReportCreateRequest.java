package ru.lab.cityreports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Статус при создании не принимаем: новое обращение всегда NEW.
 * authorId необязателен: если не указан — автором становится текущий пользователь.
 * Указать ЧУЖОЙ authorId может только тот, у кого есть право REPORT_CREATE_FOR_OTHERS (диспетчер).
 */
public record CityReportCreateRequest(
        @NotNull @Positive Long objectId,
        @Positive Long authorId,
        @NotBlank @Size(max = 2000) String description
) {}
