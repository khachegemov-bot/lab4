package ru.lab.cityreports.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import ru.lab.cityreports.exception.ErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Ответы на отказы, которые происходят ещё в фильтрах Spring Security (до контроллеров).
 * Формат тот же, что у GlobalExceptionHandler, — единый JSON без внутренних подробностей.
 *
 *  401 — не удалось установить личность: нет логина/пароля, пароль неверный,
 *        учётная запись заблокирована. Причину НЕ уточняем: иначе по ответу можно
 *        узнать, существует ли логин и заблокирован ли он.
 *  403 — личность известна, но прав не хватает; отдельно — отсутствует/неверен CSRF-токен.
 */
@Component
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorHandlers.class);

    public static final String AUTH_REQUIRED =
            "Требуется аутентификация: неверный логин или пароль, либо учётная запись недоступна";
    public static final String FORBIDDEN = "Недостаточно прав для выполнения операции";
    public static final String CSRF_INVALID =
            "CSRF-токен отсутствует или недействителен. Получите токен: GET /api/csrf "
                    + "и передайте его в заголовке X-XSRF-TOKEN";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecurityErrorHandlers(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 401 Unauthorized. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Basic realm=\"city-reports\", charset=\"UTF-8\"");
        write(response, HttpStatus.UNAUTHORIZED, AUTH_REQUIRED);
    }

    /** 403 Forbidden. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        Principal user = request.getUserPrincipal();
        String who = user == null ? "анонимный пользователь" : "'" + user.getName() + "'";
        if (ex instanceof CsrfException) {
            log.warn("Отклонён запрос без корректного CSRF-токена: {} {} ({})",
                    request.getMethod(), request.getRequestURI(), who);
            write(response, HttpStatus.FORBIDDEN, CSRF_INVALID);
        } else {
            log.warn("Доступ запрещён: {} {} ({})", request.getMethod(), request.getRequestURI(), who);
            write(response, HttpStatus.FORBIDDEN, FORBIDDEN);
        }
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        ErrorResponse body = new ErrorResponse(Instant.now(clock), status.value(), status.getReasonPhrase(),
                List.of(message));
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
