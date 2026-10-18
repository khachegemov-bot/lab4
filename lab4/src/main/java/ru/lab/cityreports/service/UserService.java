package ru.lab.cityreports.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab.cityreports.exception.BadRequestException;
import ru.lab.cityreports.exception.ConflictException;
import ru.lab.cityreports.exception.NotFoundException;
import ru.lab.cityreports.model.Role;
import ru.lab.cityreports.model.User;
import ru.lab.cityreports.model.UserStatus;
import ru.lab.cityreports.repository.CityObjectRepository;
import ru.lab.cityreports.repository.CityReportRepository;
import ru.lab.cityreports.repository.UserRepository;
import ru.lab.cityreports.security.PasswordPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final CityReportRepository cityReportRepository;
    private final CityObjectRepository cityObjectRepository;
    private final DomainStateSync stateSync;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    public UserService(UserRepository userRepository,
                       CityReportRepository cityReportRepository,
                       CityObjectRepository cityObjectRepository,
                       DomainStateSync stateSync,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy,
                       Clock clock) {
        this.userRepository = userRepository;
        this.cityReportRepository = cityReportRepository;
        this.cityObjectRepository = cityObjectRepository;
        this.stateSync = stateSync;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + id + " не найден"));
    }

    /** Самостоятельная регистрация: роль ВСЕГДА RESIDENT, статус ACTIVE — их задаёт сервер. */
    @Transactional
    public User register(String username, String password, String fullName, String email) {
        return createAccount(username, password, fullName, email, Role.RESIDENT);
    }

    /** Создание пользователя администратором — с произвольной ролью. */
    @Transactional
    public User create(String username, String password, String fullName, String email, Role role) {
        return createAccount(username, password, fullName, email, role);
    }

    private User createAccount(String username, String password, String fullName, String email, Role role) {
        String name = username.trim();
        passwordPolicy.validate(name, password);
        if (userRepository.existsByUsernameIgnoreCase(name)) {
            throw new ConflictException("Имя пользователя '" + name + "' уже занято");
        }
        User user = new User();                                   // id выдаёт БД, время — сервер
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(password));   // в БД — только BCrypt-хеш
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setFullName(fullName.trim());
        user.setEmail(email.trim().toLowerCase(Locale.ROOT));
        Instant now = Instant.now(clock);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        // Если два запроса одновременно пройдут проверку выше, второй всё равно
        // упадёт на уникальном индексе в БД -> 409 (см. GlobalExceptionHandler).
        User saved = userRepository.saveAndFlush(user);
        log.info("Создана учётная запись id={} с ролью {}", saved.getId(), role);
        return saved;
    }

    /** PATCH профиля: логин, ФИО, email. Роль, статус и пароль здесь изменить нельзя. */
    @Transactional
    public User update(Long id, String username, String fullName, String email) {
        User user = getById(id);
        if (username != null) {
            String name = username.trim();
            if (userRepository.existsByUsernameIgnoreCaseAndIdNot(name, id)) {
                throw new ConflictException("Имя пользователя '" + name + "' уже занято");
            }
            user.setUsername(name);
        }
        if (fullName != null) {
            user.setFullName(fullName.trim());
        }
        if (email != null) {
            user.setEmail(email.trim().toLowerCase(Locale.ROOT));
        }
        user.setUpdatedAt(Instant.now(clock));
        return userRepository.saveAndFlush(user);
    }

    /** Смена роли администратором. Свою роль поменять нельзя — иначе можно остаться без администраторов. */
    @Transactional
    public User changeRole(Long id, Role role, Long actingUserId) {
        if (id.equals(actingUserId)) {
            throw new ConflictException("Нельзя изменить роль собственной учётной записи");
        }
        User user = getById(id);
        Role old = user.getRole();
        user.setRole(role);
        user.setUpdatedAt(Instant.now(clock));
        userRepository.saveAndFlush(user);
        log.info("Роль пользователя id={} изменена: {} -> {} (администратор id={})", id, old, role, actingUserId);
        return user;
    }

    /**
     * Блокировка/разблокировка администратором.
     * ACTIVE также снимает временную блокировку за перебор паролей.
     */
    @Transactional
    public User changeStatus(Long id, UserStatus status, Long actingUserId) {
        if (id.equals(actingUserId)) {
            throw new ConflictException("Нельзя заблокировать или разблокировать собственную учётную запись");
        }
        User user = getById(id);
        user.setStatus(status);
        if (status == UserStatus.ACTIVE) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        user.setUpdatedAt(Instant.now(clock));
        userRepository.saveAndFlush(user);
        log.info("Статус пользователя id={} изменён на {} (администратор id={})", id, status, actingUserId);
        return user;
    }

    /** Смена своего пароля: обязательно подтвердить текущий. */
    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword) {
        User user = getById(id);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Текущий пароль указан неверно");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("Новый пароль совпадает с текущим");
        }
        passwordPolicy.validate(user.getUsername(), newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now(clock));
        userRepository.saveAndFlush(user);
        log.info("Пользователь id={} сменил пароль", id);
    }

    /** Каскад: удаляются обращения пользователя и их наряды, состояние затронутых объектов пересчитывается. */
    @Transactional
    public void delete(Long id, Long actingUserId) {
        if (id.equals(actingUserId)) {
            throw new ConflictException("Нельзя удалить собственную учётную запись");
        }
        User user = getById(id);
        List<Long> affectedObjects = cityReportRepository.findObjectIdsByAuthor(id);
        userRepository.delete(user);
        userRepository.flush();
        cityObjectRepository.findAllById(affectedObjects)
                .forEach(object -> stateSync.syncObjectState(object, false));
        log.info("Пользователь id={} удалён (администратор id={})", id, actingUserId);
    }
}
