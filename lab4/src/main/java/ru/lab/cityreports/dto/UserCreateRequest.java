package ru.lab.cityreports.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.lab.cityreports.model.Role;

/** Создание пользователя администратором (POST /api/users) — здесь роль задать можно. */
public record UserCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_.-]{3,32}$",
                message = "3-32 символа: латиница, цифры, _ . -")
        String username,

        @NotBlank @Size(max = 128)
        String password,

        @NotBlank @Size(max = 100)
        String fullName,

        @NotBlank @Email @Size(max = 254)
        String email,

        @NotNull
        Role role
) {}
