package ru.lab.cityreports.model;

import java.util.List;

/** Статус наряда на работу. PLANNED и IN_PROGRESS считаются "активными". */
public enum WorkOrderStatus {
    PLANNED, IN_PROGRESS, DONE, CANCELLED;

    /** Активные статусы — занимают место в лимите бригады. */
    public static final List<WorkOrderStatus> ACTIVE = List.of(PLANNED, IN_PROGRESS);

    public boolean isActive() {
        return this == PLANNED || this == IN_PROGRESS;
    }
}
