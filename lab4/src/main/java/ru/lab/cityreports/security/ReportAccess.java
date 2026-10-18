package ru.lab.cityreports.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import ru.lab.cityreports.model.Permission;
import ru.lab.cityreports.repository.CityReportRepository;

/**
 * Проверки доступа, зависящие от КОНТЕКСТА операции — от того, чьё это обращение.
 * Используются в @PreAuthorize: @PreAuthorize("@reportAccess.canView(#id, authentication)").
 *
 * Житель (без права REPORT_READ_ALL) видит, меняет и удаляет только обращения, автор которых — он сам.
 * Если обращения не существует, житель получает 403, а не 404: так нельзя перебором id
 * выяснить, какие обращения есть у других людей.
 */
@Component("reportAccess")
public class ReportAccess {

    private final CityReportRepository cityReportRepository;

    public ReportAccess(CityReportRepository cityReportRepository) {
        this.cityReportRepository = cityReportRepository;
    }

    public boolean canView(Long reportId, Authentication auth) {
        return hasPermission(auth, Permission.REPORT_READ_ALL) || isAuthor(reportId, auth);
    }

    public boolean canEdit(Long reportId, Authentication auth) {
        return hasPermission(auth, Permission.REPORT_EDIT_ANY) || isAuthor(reportId, auth);
    }

    public boolean canDelete(Long reportId, Authentication auth) {
        return hasPermission(auth, Permission.REPORT_DELETE_ANY) || isAuthor(reportId, auth);
    }

    private boolean isAuthor(Long reportId, Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p
                && cityReportRepository.existsByIdAndAuthorId(reportId, p.getId());
    }

    private static boolean hasPermission(Authentication auth, Permission permission) {
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p && p.has(permission);
    }
}
