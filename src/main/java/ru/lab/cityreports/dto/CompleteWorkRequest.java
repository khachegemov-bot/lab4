package ru.lab.cityreports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Тело бизнес-операции "завершить работы". */
public record CompleteWorkRequest(
        @NotBlank @Size(max = 2000) String resultDescription
) {}
