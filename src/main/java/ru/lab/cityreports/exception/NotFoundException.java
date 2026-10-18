package ru.lab.cityreports.exception;

/** Ресурс не найден -> 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
