package ru.lab.cityreports.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.lab.cityreports.dto.AssignCrewRequest;
import ru.lab.cityreports.dto.CityReportCreateRequest;
import ru.lab.cityreports.dto.CityReportPatchRequest;
import ru.lab.cityreports.dto.CityReportResponse;
import ru.lab.cityreports.dto.RejectReportRequest;
import ru.lab.cityreports.dto.WorkOrderResponse;
import ru.lab.cityreports.model.CityReport;
import ru.lab.cityreports.model.Permission;
import ru.lab.cityreports.security.AppUserPrincipal;
import ru.lab.cityreports.model.WorkOrder;
import ru.lab.cityreports.service.CityReportService;
import ru.lab.cityreports.service.WorkflowService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/city-reports")
public class CityReportController {

    private final CityReportService cityReportService;
    private final WorkflowService workflowService;

    public CityReportController(CityReportService cityReportService, WorkflowService workflowService) {
        this.cityReportService = cityReportService;
        this.workflowService = workflowService;
    }

    /** Диспетчер и администратор видят все обращения, житель — только свои. */
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_CREATE') or hasAuthority('REPORT_READ_ALL')")
    public List<CityReportResponse> getAll(@AuthenticationPrincipal AppUserPrincipal me) {
        List<CityReport> reports = me.has(Permission.REPORT_READ_ALL)
                ? cityReportService.findAll()
                : cityReportService.findByAuthor(me.getId());
        return reports.stream().map(CityReportController::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@reportAccess.canView(#id, authentication)")
    public CityReportResponse getById(@PathVariable Long id) {
        return toResponse(cityReportService.getById(id));
    }

    /**
     * Подать обращение. Без authorId автором становится текущий пользователь.
     * Чужой authorId разрешён только с правом REPORT_CREATE_FOR_OTHERS — иначе 403
     * (житель не может подать жалобу от имени соседа).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('REPORT_CREATE') and (#request.authorId() == null "
            + "or #request.authorId() == principal.id or hasAuthority('REPORT_CREATE_FOR_OTHERS'))")
    public ResponseEntity<CityReportResponse> create(@Valid @RequestBody CityReportCreateRequest request,
                                                     @AuthenticationPrincipal AppUserPrincipal me) {
        Long authorId = request.authorId() != null ? request.authorId() : me.getId();
        CityReport created = cityReportService.create(request.objectId(), authorId, request.description());
        return ResponseEntity.created(URI.create("/api/city-reports/" + created.getId()))
                .body(toResponse(created));
    }

    /** Автор правит своё обращение только пока оно NEW; диспетчер — любое открытое. */
    @PatchMapping("/{id}")
    @PreAuthorize("@reportAccess.canEdit(#id, authentication)")
    public CityReportResponse update(@PathVariable Long id, @Valid @RequestBody CityReportPatchRequest request,
                                     @AuthenticationPrincipal AppUserPrincipal me) {
        boolean onlyWhileNew = !me.has(Permission.REPORT_EDIT_ANY);
        return toResponse(cityReportService.update(id, request.description(), onlyWhileNew));
    }

    /** Автор может отозвать своё обращение, пока оно NEW; администратор удаляет любое. */
    @DeleteMapping("/{id}")
    @PreAuthorize("@reportAccess.canDelete(#id, authentication)")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me) {
        cityReportService.delete(id, !me.has(Permission.REPORT_DELETE_ANY));
        return ResponseEntity.noContent().build();
    }

    // ---------- бизнес-операции ----------

    /** Операция 1: назначить бригаду на обращение -> 201 + созданный наряд. */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    public ResponseEntity<WorkOrderResponse> assign(@PathVariable Long id, @Valid @RequestBody AssignCrewRequest request) {
        WorkOrder order = workflowService.assignCrew(id, request.crewId(), request.deadline());
        return ResponseEntity.created(URI.create("/api/work-orders/" + order.getId()))
                .body(WorkOrderController.toResponse(order));
    }

    /** Операция 6: отклонить обращение. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    public CityReportResponse reject(@PathVariable Long id, @Valid @RequestBody RejectReportRequest request) {
        return toResponse(workflowService.rejectReport(id, request.reason()));
    }

    static CityReportResponse toResponse(CityReport r) {
        return new CityReportResponse(r.getId(), r.getObject().getId(), r.getAuthor().getId(), r.getDescription(),
                r.getStatus(), r.getRejectionReason(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
