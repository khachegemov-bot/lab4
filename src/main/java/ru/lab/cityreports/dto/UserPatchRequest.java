package ru.lab.cityreports.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH: все поля необязательные. null = "не менять". */
public record UserPatchRequest(
        @Pattern(regexp = "^[A-Za-z0-9_.-]{3,32}$",
                message = "3-32 символа: латиница, цифры, _ . -")
        String username,

        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "не должно быть пустым")
        String fullName,

        @Email @Size(max = 254) @Pattern(regexp = ".*\\S.*", message = "не должно быть пустым")
        String email
) {}
