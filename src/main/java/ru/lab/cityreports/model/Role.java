package ru.lab.cityreports.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static ru.lab.cityreports.model.Permission.*;

/**
 * Роли пользователей.
 *  RESIDENT   — житель: подаёт обращения и работает только со СВОИМИ обращениями;
 *  DISPATCHER — диспетчер службы благоустройства: объекты, бригады, наряды, бизнес-операции;
 *  ADMIN      — администратор: всё, включая управление пользователями и удаление данных.
 */
public enum Role {
    RESIDENT(EnumSet.of(REPORT_CREATE)),
    DISPATCHER(EnumSet.of(REPORT_CREATE, REPORT_CREATE_FOR_OTHERS, REPORT_READ_ALL, REPORT_EDIT_ANY,
            WORKFLOW_MANAGE, OBJECT_MANAGE, CREW_MANAGE)),
    ADMIN(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    /** Authorities для Spring Security: ROLE_<роль> + все права роли. */
    public List<GrantedAuthority> toAuthorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_" + name()));
        permissions.forEach(p -> list.add(new SimpleGrantedAuthority(p.name())));
        return list;
    }
}
