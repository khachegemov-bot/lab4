package ru.lab.cityreports.dto;

/** headerName — в каком заголовке отправлять токен, token — сам токен. */
public record CsrfTokenResponse(String headerName, String token) {}
