package ru.lab.cityreports.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static ru.lab.cityreports.model.Permission.*;

/**
 * Настройка Spring Security.
 *
 * Аутентификация — HTTP Basic: логин и пароль в заголовке Authorization каждого запроса.
 * Сессий нет (STATELESS): сервер не хранит состояние входа.
 *
 * CSRF включён для всех изменяющих запросов (POST/PATCH/PUT/DELETE).
 * Почему он нужен при Basic: браузер запоминает введённые Basic-учётные данные и сам
 * подставляет их в запросы к сайту — в том числе в запросы, которые отправила чужая
 * вредоносная страница. Токен в заголовке X-XSRF-TOKEN чужая страница прочитать не может,
 * поэтому её подделанный запрос будет отклонён (403).
 *
 * Авторизация — в два слоя:
 *   1) SecurityFilterChain: грубые правила по URL и HTTP-методу;
 *   2) @PreAuthorize на методах контроллеров: точные правила, в том числе зависящие
 *      от контекста (автор обращения, свой профиль).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** BCrypt с cost 12: 2^12 раундов, ~0,2–0,3 с на хеш — перебор украденных хешей очень дорогой. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityErrorHandlers errorHandlers) throws Exception {
        // CSRF-токен хранится в cookie XSRF-TOKEN (HttpOnly, SameSite=Strict), а клиент
        // присылает его копию в заголовке X-XSRF-TOKEN. Сервер сравнивает их.
        // Сессия для этого не нужна — подходит для STATELESS API.
        CookieCsrfTokenRepository csrfRepository = new CookieCsrfTokenRepository();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Strict").path("/"));

        http
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfRepository)
                    // По умолчанию Spring при КАЖДОМ успешном входе удаляет CSRF-токен (защита от
                    // подстановки токена до входа). При HTTP Basic "вход" происходит в каждом запросе,
                    // и токен сгорал бы сразу после получения. Поэтому ротацию при входе отключаем;
                    // подменить cookie чужой странице мешают флаги HttpOnly и SameSite=Strict.
                    .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
            .httpBasic(basic -> basic.authenticationEntryPoint(errorHandlers))
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(Customizer.withDefaults())
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(errorHandlers)   // 401
                    .accessDeniedHandler(errorHandlers))       // 403 (в т.ч. ошибка CSRF)
            .authorizeHttpRequests(auth -> auth
                // --- открыто без входа ---
                .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers("/error").permitAll()

                // --- справочники: читать может любой вошедший ---
                .requestMatchers(HttpMethod.GET, "/api/city-objects", "/api/city-objects/*").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/work-crews", "/api/work-crews/*").authenticated()

                // --- городские объекты: менять — диспетчер, удалять — администратор ---
                .requestMatchers(HttpMethod.DELETE, "/api/city-objects/*").hasAuthority(OBJECT_DELETE.name())
                .requestMatchers("/api/city-objects/**").hasAuthority(OBJECT_MANAGE.name())

                // --- бригады ---
                .requestMatchers("/api/work-crews/**").hasAuthority(CREW_MANAGE.name())

                // --- наряды и бизнес-операции ---
                .requestMatchers("/api/work-orders/**").hasAuthority(WORKFLOW_MANAGE.name())
                .requestMatchers(HttpMethod.POST, "/api/city-reports/*/assign", "/api/city-reports/*/reject")
                    .hasAuthority(WORKFLOW_MANAGE.name())

                // --- обращения, пользователи, профиль: нужен вход, детали — в @PreAuthorize ---
                .requestMatchers("/api/city-reports/**").authenticated()
                .requestMatchers("/api/users/**").authenticated()
                .requestMatchers("/api/auth/**").authenticated()

                // всё, что не перечислено, запрещено (принцип "запрещено всё, что не разрешено")
                .anyRequest().denyAll());

        return http.build();
    }

    /**
     * CORS: браузер разрешит запросы к API со страниц ТОЛЬКО с перечисленных источников (origins).
     * По умолчанию список пуст — запросы из браузера с чужих сайтов блокируются.
     * Postman и curl политику CORS не применяют, им она не мешает.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins.stream().filter(s -> !s.isBlank()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
