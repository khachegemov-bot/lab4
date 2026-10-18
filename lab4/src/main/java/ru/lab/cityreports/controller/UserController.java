package ru.lab.cityreports.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.lab.cityreports.dto.UserCreateRequest;
import ru.lab.cityreports.dto.UserPatchRequest;
import ru.lab.cityreports.dto.UserResponse;
import ru.lab.cityreports.dto.UserRoleRequest;
import ru.lab.cityreports.dto.UserStatusRequest;
import ru.lab.cityreports.model.User;
import ru.lab.cityreports.security.AppUserPrincipal;
import ru.lab.cityreports.service.UserService;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Пользователи.
 *  - список, создание, роли, блокировка, удаление — только USER_MANAGE (администратор);
 *  - просмотр и правка профиля — администратор или сам пользователь (контекст: #id == principal.id).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final Clock clock;

    public UserController(UserService userService, Clock clock) {
        this.userService = userService;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public List<UserResponse> getAll() {
        Instant now = Instant.now(clock);
        return userService.findAll().stream().map(u -> toResponse(u, now)).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE') or #id == principal.id")
    public UserResponse getById(@PathVariable Long id) {
        return toResponse(userService.getById(id), Instant.now(clock));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        User created = userService.create(request.username(), request.password(), request.fullName(),
                request.email(), request.role());
        return ResponseEntity.created(URI.create("/api/users/" + created.getId()))
                .body(toResponse(created, Instant.now(clock)));
    }

    /** Свой профиль может править сам пользователь. Полей роли/статуса в запросе нет. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE') or #id == principal.id")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserPatchRequest request) {
        return toResponse(userService.update(id, request.username(), request.fullName(), request.email()),
                Instant.now(clock));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody UserRoleRequest request,
                                   @AuthenticationPrincipal AppUserPrincipal me) {
        return toResponse(userService.changeRole(id, request.role(), me.getId()), Instant.now(clock));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request,
                                     @AuthenticationPrincipal AppUserPrincipal me) {
        return toResponse(userService.changeStatus(id, request.status(), me.getId()), Instant.now(clock));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me) {
        userService.delete(id, me.getId());
        return ResponseEntity.noContent().build();
    }

    static UserResponse toResponse(User u, Instant now) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(),
                u.getRole(), u.getStatus(), u.isTemporarilyLocked(now),
                u.getCreatedAt(), u.getUpdatedAt());
    }
}
