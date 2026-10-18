package ru.lab.cityreports.model;

/**
 * Состояние учётной записи.
 *  ACTIVE  — может входить;
 *  BLOCKED — заблокирована администратором, вход запрещён.
 * Отдельно от статуса есть временная блокировка после серии неверных паролей (User.lockedUntil).
 */
public enum UserStatus {
    ACTIVE, BLOCKED
}
