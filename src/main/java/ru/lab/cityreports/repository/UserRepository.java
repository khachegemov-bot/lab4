package ru.lab.cityreports.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.lab.cityreports.model.User;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data сам строит SQL по имени метода, значения передаются как параметры
 * (PreparedStatement), поэтому SQL-инъекция невозможна.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    /** Поиск учётной записи при входе: Ivanov и ivanov — один пользователь. */
    Optional<User> findByUsernameIgnoreCase(String username);

    /** +1 неудачная попытка входа. Атомарно в БД, без чтения-изменения-записи в Java. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.failedLoginAttempts = u.failedLoginAttempts + 1 "
            + "where lower(u.username) = lower(:username)")
    int incrementFailedAttempts(@Param("username") String username);

    /** Если попыток набралось max — блокируем вход до :until и обнуляем счётчик. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.lockedUntil = :until, u.failedLoginAttempts = 0 "
            + "where lower(u.username) = lower(:username) and u.failedLoginAttempts >= :max")
    int lockIfTooManyAttempts(@Param("username") String username, @Param("max") int max,
                              @Param("until") Instant until);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.failedLoginAttempts = 0 where u.id = :id and u.failedLoginAttempts > 0")
    int resetFailedAttempts(@Param("id") Long id);
}
