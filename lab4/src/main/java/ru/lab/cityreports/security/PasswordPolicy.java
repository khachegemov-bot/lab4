package ru.lab.cityreports.security;

import org.springframework.stereotype.Component;
import ru.lab.cityreports.exception.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Требования к надёжности пароля.
 * Сообщения об ошибках описывают правило, но никогда не содержат сам пароль.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    /** BCrypt учитывает только первые 72 байта пароля — длиннее принимать нельзя. */
    private static final int BCRYPT_MAX_BYTES = 72;

    /** Небольшой список самых распространённых паролей (в реальной системе — тысячи записей). */
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "12345678", "123456789", "1234567890",
            "qwerty123", "qwertyuiop", "11111111", "00000000", "iloveyou", "admin123",
            "administrator", "welcome1", "letmein1", "1q2w3e4r", "1qaz2wsx", "zaq12wsx",
            "qwerty12", "abc12345", "password!", "p@ssw0rd", "passw0rd", "123qweasd");

    public void validate(String username, String password) {
        List<String> errors = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            errors.add("длина пароля — от " + MIN_LENGTH + " до " + MAX_LENGTH + " символов");
        } else {
            if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
                errors.add("пароль длиннее " + BCRYPT_MAX_BYTES + " байт (используйте латиницу или сократите)");
            }
            if (password.chars().noneMatch(Character::isLetter)) {
                errors.add("пароль должен содержать хотя бы одну букву");
            }
            if (password.chars().noneMatch(Character::isDigit)) {
                errors.add("пароль должен содержать хотя бы одну цифру");
            }
            if (password.chars().anyMatch(Character::isWhitespace)) {
                errors.add("пароль не должен содержать пробелы");
            }
            String lower = password.toLowerCase(Locale.ROOT);
            if (username != null && !username.isBlank()
                    && lower.contains(username.trim().toLowerCase(Locale.ROOT))) {
                errors.add("пароль не должен содержать логин");
            }
            if (COMMON.contains(lower)) {
                errors.add("пароль входит в список распространённых — выберите другой");
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Пароль ненадёжен: " + String.join("; ", errors));
        }
    }
}
