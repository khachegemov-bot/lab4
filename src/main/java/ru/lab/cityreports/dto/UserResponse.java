package ru.lab.cityreports.dto;

import ru.lab.cityreports.model.Role;
import ru.lab.cityreports.model.UserStatus;

import java.time.Instant;

/** Хеш пароля и счётчик неудачных входов в ответ НЕ попадают. */
public record UserResponse(Long id, String username, String fullName, String email,
                           Role role, UserStatus status, boolean temporarilyLocked,
                           Instant createdAt, Instant updatedAt) {}
