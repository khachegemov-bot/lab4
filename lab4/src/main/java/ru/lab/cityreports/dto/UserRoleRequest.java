package ru.lab.cityreports.dto;

import jakarta.validation.constraints.NotNull;
import ru.lab.cityreports.model.Role;

public record UserRoleRequest(@NotNull Role role) {}
