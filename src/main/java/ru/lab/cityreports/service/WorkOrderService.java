package ru.lab.cityreports.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab.cityreports.exception.BadRequestException;
import ru.lab.cityreports.exception.ConflictException;
import ru.lab.cityreports.exception.NotFoundException;
import ru.lab.cityreports.model.CityReport;
import ru.lab.cityreports.model.WorkOrder;
import ru.lab.cityreports.model.WorkOrderStatus;
import ru.lab.cityreports.repository.WorkOrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** CRUD нарядов. Создание и смена статуса/бригады — только через WorkflowService. */
@Service
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final DomainStateSync stateSync;
    private final Clock clock;

    public WorkOrderService(WorkOrderRepository workOrderRepository, DomainStateSync stateSync, Clock clock) {
        this.workOrderRepository = workOrderRepository;
        this.stateSync = stateSync;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> findAll() {
        return workOrderRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public WorkOrder getById(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Наряд с id=" + id + " не найден"));
    }

    /** PATCH: только перенос срока, и только у активного наряда. */
    @Transactional
    public WorkOrder updateDeadline(Long id, LocalDate deadline) {
        WorkOrder order = getById(id);
        if (!order.getStatus().isActive()) {
            throw new ConflictException("Наряд в статусе " + order.getStatus() + " закрыт и не может изменяться");
        }
        if (deadline != null) {
            LocalDate today = LocalDate.now(clock);
            if (deadline.isBefore(today)) {
                throw new BadRequestException("Срок (deadline) не может быть в прошлом. Сегодня: " + today);
            }
            order.setDeadline(deadline);
        }
        order.setUpdatedAt(Instant.now(clock));
        return workOrderRepository.saveAndFlush(order);
    }

    /** Выполненный наряд — история работ, его удалять нельзя. */
    @Transactional
    public void delete(Long id) {
        WorkOrder order = getById(id);
        if (order.getStatus() == WorkOrderStatus.DONE) {
            throw new ConflictException("Выполненный наряд нельзя удалить — это история работ");
        }
        CityReport report = order.getReport();
        workOrderRepository.delete(order);
        workOrderRepository.flush();
        stateSync.syncReportStatus(report);
        stateSync.syncObjectState(report.getObject(), false);
    }
}
