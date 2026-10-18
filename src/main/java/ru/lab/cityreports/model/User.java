package ru.lab.cityreports.model;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Пользователь системы: житель, диспетчер или администратор. Таблица users.
 *
 * @DynamicUpdate — в UPDATE попадают только изменённые столбцы. Иначе, например, PATCH ФИО
 * мог бы перезаписать счётчик неверных паролей устаревшим значением.
 */
@Entity
@Table(name = "users")
@DynamicUpdate
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // id выдаёт только БД
    private Long id;

    @Column(name = "username", nullable = false, length = 32)
    private String username;

    /** Только BCrypt-хеш. Сам пароль нигде не хранится и в ответах API не возвращается. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    /** Сколько раз подряд введён неверный пароль. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /** До какого момента вход временно запрещён (защита от перебора паролей). */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Оптимистичная блокировка: защищает от "потерянного обновления" при параллельных PATCH. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Обращения пользователя. При удалении пользователя удаляются и они (каскад REMOVE). */
    @OneToMany(mappedBy = "author", cascade = CascadeType.REMOVE)
    private List<CityReport> reports = new ArrayList<>();

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<CityReport> getReports() { return reports; }

    /** Временная блокировка за перебор паролей ещё действует. */
    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
