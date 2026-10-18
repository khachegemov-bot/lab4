package ru.lab.cityreports.exception;

/** Нарушение бизнес-правила (лимит бригады, неверный переход статуса...) -> 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
