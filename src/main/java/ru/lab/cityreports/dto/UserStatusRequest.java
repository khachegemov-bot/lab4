package ru.lab.cityreports.dto;

import jakarta.validation.constraints.NotNull;
import ru.lab.cityreports.model.UserStatus;

public record UserStatusRequest(@NotNull UserStatus status) {}
