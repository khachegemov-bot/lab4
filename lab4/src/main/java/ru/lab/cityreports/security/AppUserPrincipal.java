package ru.lab.cityreports.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.lab.cityreports.model.Permission;
import ru.lab.cityreports.model.Role;
import ru.lab.cityreports.model.User;
import ru.lab.cityreports.model.UserStatus;

import java.time.Instant;
import java.util.Collection;

/**
 * Аутентифицированный пользователь — то, что Spring Security кладёт в SecurityContext.
 * Хранит id (нужен для проверок "это моё обращение?") и состояние учётной записи.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    private final boolean nonLocked;
    private final int failedLoginAttempts;

    public AppUserPrincipal(User user, Instant now) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = user.getStatus() == UserStatus.ACTIVE;
        this.nonLocked = !user.isTemporarilyLocked(now);
        this.failedLoginAttempts = user.getFailedLoginAttempts();
    }

    public Long getId() { return id; }
    public Role getRole() { return role; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }

    public boolean has(Permission permission) {
        return role.getPermissions().contains(permission);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return role.toAuthorities(); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    /** false -> DisabledException: учётная запись заблокирована администратором. */
    @Override public boolean isEnabled() { return enabled; }
    /** false -> LockedException: временная блокировка после перебора паролей. */
    @Override public boolean isAccountNonLocked() { return nonLocked; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    /** Чтобы хеш пароля случайно не попал в лог через toString(). */
    @Override public String toString() { return "AppUserPrincipal[id=" + id + ", role=" + role + "]"; }
}
