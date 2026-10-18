package ru.lab.cityreports.exception;

/** Некорректные данные, которые нельзя проверить аннотациями -> 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
