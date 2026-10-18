package ru.lab.cityreports.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Единый источник текущего времени — часы сервера.
 * Время НИКОГДА не берётся из запроса клиента.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
