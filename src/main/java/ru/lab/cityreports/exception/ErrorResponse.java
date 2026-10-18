package ru.lab.cityreports.exception;

import java.time.Instant;
import java.util.List;

/** Единый формат ошибки. Без стек-трейсов и внутренних деталей. */
public record ErrorResponse(Instant timestamp, int status, String error, List<String> details) {
}
