package ru.lab.cityreports.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import ru.lab.cityreports.dto.ChangePasswordRequest;
import ru.lab.cityreports.dto.CsrfTokenResponse;
import ru.lab.cityreports.dto.RegisterRequest;
import ru.lab.cityreports.dto.UserResponse;
import ru.lab.cityreports.model.User;
import ru.lab.cityreports.security.AppUserPrincipal;
import ru.lab.cityreports.service.UserService;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

@RestController
public class AuthController {

    private final UserService userService;
    private final Clock clock;

    public AuthController(UserService userService, Clock clock) {
        this.userService = userService;
        this.clock = clock;
    }

    /**
     * Получить CSRF-токен. Ответ также ставит cookie XSRF-TOKEN.
     * Дальше в каждом изменяющем запросе передаётся заголовок X-XSRF-TOKEN: <token>.
     * Каждый вызов выдаёт по-новому замаскированный токен (защита от атаки BREACH),
     * но все они соответствуют одной cookie и одинаково действительны.
     */
    @GetMapping("/api/csrf")
    public CsrfTokenResponse csrf(CsrfToken token) {
        return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
    }

    /** Регистрация жителя. Доступна без входа, но с CSRF-токеном. */
    @PostMapping("/api/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User created = userService.register(request.username(), request.password(),
                request.fullName(), request.email());
        return ResponseEntity.created(URI.create("/api/users/" + created.getId()))
                .body(UserController.toResponse(created, Instant.now(clock)));
    }

    /** Кто я: проверка логина/пароля и просмотр своей роли. */
    @GetMapping("/api/auth/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal me) {
        return UserController.toResponse(userService.getById(me.getId()), Instant.now(clock));
    }

    /** Смена своего пароля (нужно подтвердить текущий). */
    @PostMapping("/api/auth/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AppUserPrincipal me,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(me.getId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
