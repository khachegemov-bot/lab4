package ru.lab.cityreports.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import ru.lab.cityreports.security.SecurityErrorHandlers;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Перехватывает исключения и превращает их в понятный JSON с правильным HTTP-кодом.
 * Клиент НЕ видит стек-трейс, имена классов и внутренние сообщения Java.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, List.of(e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException e) {
        return build(HttpStatus.CONFLICT, List.of(e.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(BadRequestException e) {
        return build(HttpStatus.BAD_REQUEST, List.of(e.getMessage()));
    }

    /**
     * Отказ @PreAuthorize (AuthorizationDeniedException) или проверки прав в коде -> 403.
     * Без этого обработчика отказ попал бы в общий Exception и превратился в 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException e) {
        log.warn("Доступ запрещён на уровне метода: {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, List.of(SecurityErrorHandlers.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> unauthenticated(AuthenticationException e) {
        return build(HttpStatus.UNAUTHORIZED, List.of(SecurityErrorHandlers.AUTH_REQUIRED));
    }

    /** Ошибки валидации @Valid (пустые поля, слишком длинные строки, отрицательные числа...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .sorted()
                .toList();
        return build(HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> methodValidation(HandlerMethodValidationException e) {
        return build(HttpStatus.BAD_REQUEST, List.of("Некорректные параметры запроса"));
    }

    /** Битый JSON, неизвестное поле, неверное значение enum, неверный формат даты. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST,
                List.of("Некорректное тело запроса: неверный JSON, неизвестное поле или недопустимое значение"));
    }

    /** /api/users/abc вместо числа. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return build(HttpStatus.BAD_REQUEST, List.of("Параметр '" + e.getName() + "' имеет неверный формат"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, List.of("Метод не поддерживается"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException e) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, List.of("Ожидается Content-Type: application/json"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> noResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, List.of("Ресурс не найден"));
    }

    /**
     * Сработало ограничение целостности в БД (UNIQUE, CHECK, FOREIGN KEY...).
     * Клиенту НЕ отдаём текст ошибки PostgreSQL: в нём имена таблиц, столбцов и значения.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e) {
        log.warn("Нарушено ограничение целостности БД: {}", e.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, List.of("Операция нарушает ограничения целостности данных"));
    }

    /** Две транзакции одновременно изменили одну запись (@Version) или не дождались блокировки. */
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> concurrentUpdate(RuntimeException e) {
        log.warn("Конфликт параллельного изменения: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, List.of("Данные были изменены другим запросом, повторите операцию"));
    }

    /** База данных недоступна: детали подключения клиенту не показываем. */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ErrorResponse> databaseUnavailable(RuntimeException e) {
        log.error("База данных недоступна", e);
        return build(HttpStatus.SERVICE_UNAVAILABLE, List.of("Сервис временно недоступен"));
    }

    /** Всё непредвиденное: подробности пишем в лог сервера, клиенту — только общий текст. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Необработанная ошибка", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, List.of("Внутренняя ошибка сервера"));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, List<String> details) {
        ErrorResponse body = new ErrorResponse(Instant.now(clock), status.value(), status.getReasonPhrase(), details);
        return ResponseEntity.status(status).body(body);
    }
}
