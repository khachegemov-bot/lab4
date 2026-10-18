package ru.lab.cityreports.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab.cityreports.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;

/**
 * Загружает учётную запись из БД по логину (без учёта регистра).
 * Пароль проверяет DaoAuthenticationProvider через PasswordEncoder (BCrypt).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final Clock clock;

    public AppUserDetailsService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(user -> new AppUserPrincipal(user, Instant.now(clock)))
                // текст исключения клиенту не уходит: на любой сбой входа ответ одинаковый (401)
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
    }
}
