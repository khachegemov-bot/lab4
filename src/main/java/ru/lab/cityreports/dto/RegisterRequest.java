package ru.lab.cityreports.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Самостоятельная регистрация жителя.
 * Поля роли и статуса здесь НЕТ: регистрация всегда создаёт RESIDENT/ACTIVE.
 * Попытка прислать "role": "ADMIN" даёт 400 — в настройках включён
 * fail-on-unknown-properties (неизвестные поля JSON запрещены).
 */
public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_.-]{3,32}$", message = "3-32 символа: латиница, цифры, _ . -")
        String username,

        // подробные требования к паролю проверяет PasswordPolicy
        @NotBlank @Size(max = 128)
        String password,

        @NotBlank @Size(max = 100)
        String fullName,

        @NotBlank @Email @Size(max = 254)
        String email
) {}
