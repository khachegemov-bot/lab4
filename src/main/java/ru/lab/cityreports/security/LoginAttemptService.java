package ru.lab.cityreports.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab.cityreports.repository.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Защита от перебора паролей (brute force).
 * После N неверных паролей подряд учётная запись временно блокируется.
 * Spring Security сам публикует события успешного и неуспешного входа — мы их слушаем.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(UserRepository userRepository, Clock clock,
                               @Value("${app.security.max-failed-attempts:5}") int maxAttempts,
                               @Value("${app.security.lock-duration:15m}") Duration lockDuration) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
    }

    /** Неверный пароль (или несуществующий логин). */
    @EventListener
    @Transactional
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = String.valueOf(event.getAuthentication().getName());
        // атомарные UPDATE в БД: параллельные запросы не "потеряют" попытки
        int updated = userRepository.incrementFailedAttempts(username);
        if (updated == 0) {
            log.warn("Неудачный вход: неизвестный логин '{}'", safe(username));
            return;
        }
        Instant until = Instant.now(clock).plus(lockDuration);
        if (userRepository.lockIfTooManyAttempts(username, maxAttempts, until) > 0) {
            log.warn("Учётная запись '{}' временно заблокирована до {} после {} неверных паролей",
                    safe(username), until, maxAttempts);
        } else {
            log.warn("Неудачный вход: неверный пароль для '{}'", safe(username));
        }
    }

    /** Успешный вход: сбрасываем счётчик (пишем в БД, только если он не нулевой). */
    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof AppUserPrincipal p
                && p.getFailedLoginAttempts() > 0) {
            userRepository.resetFailedAttempts(p.getId());
        }
    }

    /**
     * Логин приходит от клиента — перед записью в лог убираем всё, кроме допустимых символов.
     * Иначе через перевод строки в логине можно подделать записи журнала (log injection).
     */
    static String safe(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9_.@-]", "?");
        return cleaned.length() > 32 ? cleaned.substring(0, 32) + "..." : cleaned;
    }
}
