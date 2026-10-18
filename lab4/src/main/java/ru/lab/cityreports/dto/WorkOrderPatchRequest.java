package ru.lab.cityreports.dto;

import java.time.LocalDate;

/**
 * PATCH наряда — только перенос срока.
 * Статус и бригада меняются бизнес-операциями start / complete / cancel / reassign.
 */
public record WorkOrderPatchRequest(
        LocalDate deadline
) {}
