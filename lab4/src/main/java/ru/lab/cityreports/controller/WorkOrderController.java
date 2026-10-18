package ru.lab.cityreports.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.lab.cityreports.dto.CompleteWorkRequest;
import ru.lab.cityreports.dto.ReassignOrderRequest;
import ru.lab.cityreports.dto.WorkOrderCreateRequest;
import ru.lab.cityreports.dto.WorkOrderPatchRequest;
import ru.lab.cityreports.dto.WorkOrderResponse;
import ru.lab.cityreports.model.WorkOrder;
import ru.lab.cityreports.service.WorkOrderService;
import ru.lab.cityreports.service.WorkflowService;

import java.net.URI;
import java.util.List;

/** Наряды и бизнес-операции над ними — только диспетчер и администратор (право WORKFLOW_MANAGE). */
@RestController
@RequestMapping("/api/work-orders")
@PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final WorkflowService workflowService;

    public WorkOrderController(WorkOrderService workOrderService, WorkflowService workflowService) {
        this.workOrderService = workOrderService;
        this.workflowService = workflowService;
    }

    @GetMapping
    public List<WorkOrderResponse> getAll() {
        return workOrderService.findAll().stream().map(WorkOrderController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public WorkOrderResponse getById(@PathVariable Long id) {
        return toResponse(workOrderService.getById(id));
    }

    /** Создание наряда проходит те же проверки, что и операция "назначить бригаду". */
    @PostMapping
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody WorkOrderCreateRequest request) {
        WorkOrder created = workflowService.assignCrew(request.reportId(), request.crewId(), request.deadline());
        return ResponseEntity.created(URI.create("/api/work-orders/" + created.getId()))
                .body(toResponse(created));
    }

    @PatchMapping("/{id}")
    public WorkOrderResponse update(@PathVariable Long id, @Valid @RequestBody WorkOrderPatchRequest request) {
        return toResponse(workOrderService.updateDeadline(id, request.deadline()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- бизнес-операции ----------

    /** Операция 2: начать работы. */
    @PostMapping("/{id}/start")
    public WorkOrderResponse start(@PathVariable Long id) {
        return toResponse(workflowService.startWork(id));
    }

    /** Операция 3: завершить работы. */
    @PostMapping("/{id}/complete")
    public WorkOrderResponse complete(@PathVariable Long id, @Valid @RequestBody CompleteWorkRequest request) {
        return toResponse(workflowService.completeWork(id, request.resultDescription()));
    }

    /** Операция 4: отменить наряд. */
    @PostMapping("/{id}/cancel")
    public WorkOrderResponse cancel(@PathVariable Long id) {
        return toResponse(workflowService.cancelOrder(id));
    }

    /** Операция 5: передать наряд другой бригаде. */
    @PostMapping("/{id}/reassign")
    public WorkOrderResponse reassign(@PathVariable Long id, @Valid @RequestBody ReassignOrderRequest request) {
        return toResponse(workflowService.reassignOrder(id, request.crewId()));
    }

    static WorkOrderResponse toResponse(WorkOrder o) {
        return new WorkOrderResponse(o.getId(), o.getReport().getId(), o.getCrew().getId(), o.getDeadline(),
                o.getStatus(), o.getResultDescription(), o.getCreatedAt(), o.getUpdatedAt(),
                o.getStartedAt(), o.getCompletedAt());
    }
}
