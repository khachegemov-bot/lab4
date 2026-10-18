package ru.lab.cityreports.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab.cityreports.exception.BadRequestException;
import ru.lab.cityreports.exception.ConflictException;
import ru.lab.cityreports.exception.NotFoundException;
import ru.lab.cityreports.model.CityObject;
import ru.lab.cityreports.model.CityReport;
import ru.lab.cityreports.model.ReportStatus;
import ru.lab.cityreports.model.WorkCrew;
import ru.lab.cityreports.model.WorkOrder;
import ru.lab.cityreports.model.WorkOrderStatus;
import ru.lab.cityreports.repository.CityReportRepository;
import ru.lab.cityreports.repository.WorkCrewRepository;
import ru.lab.cityreports.repository.WorkOrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Бизнес-операции предметной области "городские обращения".
 *
 * Каждая операция — одна транзакция (@Transactional): либо все изменения
 * в нескольких таблицах сохраняются вместе, либо при любой ошибке откатываются.
 * Порядок внутри: СНАЧАЛА все проверки, ПОТОМ изменения.
 */
@Service
public class WorkflowService {

    private final CityReportRepository cityReportRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkCrewRepository workCrewRepository;
    private final DomainStateSync stateSync;
    private final Clock clock;

    public WorkflowService(CityReportRepository cityReportRepository,
                           WorkOrderRepository workOrderRepository,
                           WorkCrewRepository workCrewRepository,
                           DomainStateSync stateSync,
                           Clock clock) {
        this.cityReportRepository = cityReportRepository;
        this.workOrderRepository = workOrderRepository;
        this.workCrewRepository = workCrewRepository;
        this.stateSync = stateSync;
        this.clock = clock;
    }

    /**
     * Операция 1. Назначить бригаду на обращение (создать наряд).
     * Затрагивает: обращение (NEW -> IN_PROGRESS), наряд (новый, PLANNED), бригаду (занимает место в лимите).
     */
    @Transactional
    public WorkOrder assignCrew(Long reportId, Long crewId, LocalDate deadline) {
        CityReport report = findReport(reportId);
        if (report.isClosed()) {
            throw new ConflictException("Нельзя назначить бригаду на закрытое обращение (статус " + report.getStatus() + ")");
        }
        workOrderRepository.findFirstByReportIdAndStatusIn(reportId, WorkOrderStatus.ACTIVE).ifPresent(o -> {
            throw new ConflictException("У обращения уже есть активный наряд id=" + o.getId()
                    + " — чтобы сменить бригаду, используйте передачу наряда (reassign)");
        });
        ensureDeadlineNotInPast(deadline);
        WorkCrew crew = lockCrew(crewId);                 // SELECT ... FOR UPDATE
        ensureCrewSupports(crew, report.getObject());
        ensureCrewHasCapacity(crew);

        WorkOrder order = new WorkOrder();
        order.setReport(report);
        order.setCrew(crew);
        order.setDeadline(deadline);
        order.setStatus(WorkOrderStatus.PLANNED);         // статус задаёт сервер
        Instant now = Instant.now(clock);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        workOrderRepository.saveAndFlush(order);

        stateSync.syncReportStatus(report);               // NEW -> IN_PROGRESS
        return order;
    }

    /**
     * Операция 2. Начать работы по наряду.
     * Затрагивает: наряд (PLANNED -> IN_PROGRESS), объект (-> UNDER_REPAIR).
     */
    @Transactional
    public WorkOrder startWork(Long orderId) {
        WorkOrder order = findOrder(orderId);
        if (order.getStatus() != WorkOrderStatus.PLANNED) {
            throw new ConflictException("Начать можно только запланированный наряд (сейчас " + order.getStatus() + ")");
        }
        if (order.getDeadline().isBefore(LocalDate.now(clock))) {
            throw new ConflictException("Срок наряда истёк (" + order.getDeadline()
                    + ") — перенесите срок или передайте наряд другой бригаде");
        }
        Instant now = Instant.now(clock);
        order.setStatus(WorkOrderStatus.IN_PROGRESS);
        order.setStartedAt(now);
        order.setUpdatedAt(now);
        workOrderRepository.saveAndFlush(order);

        stateSync.syncObjectState(order.getReport().getObject(), false);   // -> UNDER_REPAIR
        return order;
    }

    /**
     * Операция 3. Завершить работы.
     * Затрагивает: наряд (IN_PROGRESS -> DONE), обращение (-> RESOLVED), объект (-> GOOD, если других жалоб нет).
     */
    @Transactional
    public WorkOrder completeWork(Long orderId, String resultDescription) {
        WorkOrder order = findOrder(orderId);
        if (order.getStatus() != WorkOrderStatus.IN_PROGRESS) {
            throw new ConflictException("Завершить можно только наряд в работе (сейчас " + order.getStatus()
                    + "). Сначала начните работы.");
        }
        if (resultDescription == null || resultDescription.isBlank()) {
            throw new BadRequestException("Для завершения наряда укажите resultDescription");
        }
        Instant now = Instant.now(clock);
        order.setStatus(WorkOrderStatus.DONE);
        order.setResultDescription(resultDescription.trim());
        order.setCompletedAt(now);                        // время завершения — серверное
        order.setUpdatedAt(now);
        workOrderRepository.saveAndFlush(order);

        CityReport report = order.getReport();
        report.setStatus(ReportStatus.RESOLVED);
        report.setUpdatedAt(now);
        cityReportRepository.saveAndFlush(report);

        stateSync.syncObjectState(report.getObject(), true);
        return order;
    }

    /**
     * Операция 4. Отменить наряд.
     * Затрагивает: наряд (-> CANCELLED), обращение (IN_PROGRESS -> NEW), объект (UNDER_REPAIR -> NEEDS_REPAIR).
     */
    @Transactional
    public WorkOrder cancelOrder(Long orderId) {
        WorkOrder order = findOrder(orderId);
        if (!order.getStatus().isActive()) {
            throw new ConflictException("Наряд в статусе " + order.getStatus() + " уже закрыт");
        }
        order.setStatus(WorkOrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now(clock));
        workOrderRepository.saveAndFlush(order);

        CityReport report = order.getReport();
        stateSync.syncReportStatus(report);
        stateSync.syncObjectState(report.getObject(), false);
        return order;
    }

    /**
     * Операция 5. Передать наряд другой бригаде.
     * Затрагивает: наряд и ДВЕ бригады (у старой освобождается место, у новой — занимается).
     */
    @Transactional
    public WorkOrder reassignOrder(Long orderId, Long newCrewId) {
        WorkOrder order = findOrder(orderId);
        if (order.getStatus() != WorkOrderStatus.PLANNED) {
            throw new ConflictException("Передать другой бригаде можно только запланированный наряд (сейчас "
                    + order.getStatus() + ")");
        }
        if (order.getCrew().getId().equals(newCrewId)) {
            throw new BadRequestException("Наряд уже назначен этой бригаде");
        }
        WorkCrew newCrew = lockCrew(newCrewId);
        ensureCrewSupports(newCrew, order.getReport().getObject());
        ensureCrewHasCapacity(newCrew);

        order.setCrew(newCrew);
        order.setUpdatedAt(Instant.now(clock));
        return workOrderRepository.saveAndFlush(order);
    }

    /**
     * Операция 6. Отклонить обращение.
     * Затрагивает: обращение (-> REJECTED), его запланированные наряды (-> CANCELLED), объект.
     * Запрещено, если работы уже начаты.
     */
    @Transactional
    public CityReport rejectReport(Long reportId, String reason) {
        CityReport report = findReport(reportId);
        if (report.isClosed()) {
            throw new ConflictException("Обращение уже закрыто (статус " + report.getStatus() + ")");
        }
        if (workOrderRepository.existsByReportIdAndStatusIn(reportId, List.of(WorkOrderStatus.IN_PROGRESS))) {
            throw new ConflictException("По обращению уже идут работы — его нельзя отклонить");
        }
        Instant now = Instant.now(clock);
        workOrderRepository.findFirstByReportIdAndStatusIn(reportId, List.of(WorkOrderStatus.PLANNED))
                .ifPresent(order -> {
                    order.setStatus(WorkOrderStatus.CANCELLED);
                    order.setUpdatedAt(now);
                    workOrderRepository.saveAndFlush(order);
                });
        report.setStatus(ReportStatus.REJECTED);
        report.setRejectionReason(reason.trim());
        report.setUpdatedAt(now);
        cityReportRepository.saveAndFlush(report);

        stateSync.syncObjectState(report.getObject(), false);
        return report;
    }

    // ---------- вспомогательные проверки ----------

    private CityReport findReport(Long id) {
        return cityReportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Обращение с id=" + id + " не найдено"));
    }

    private WorkOrder findOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Наряд с id=" + id + " не найден"));
    }

    private WorkCrew lockCrew(Long id) {
        return workCrewRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Бригада с id=" + id + " не найдена"));
    }

    private void ensureDeadlineNotInPast(LocalDate deadline) {
        LocalDate today = LocalDate.now(clock);           // "сегодня" — по часам сервера
        if (deadline.isBefore(today)) {
            throw new BadRequestException("Срок (deadline) не может быть в прошлом. Сегодня: " + today);
        }
    }

    private void ensureCrewSupports(WorkCrew crew, CityObject object) {
        if (!crew.supports(object.getType())) {
            throw new ConflictException("Бригада '" + crew.getName() + "' не обслуживает объекты типа " + object.getType());
        }
    }

    private void ensureCrewHasCapacity(WorkCrew crew) {
        long active = workOrderRepository.countByCrewIdAndStatusIn(crew.getId(), WorkOrderStatus.ACTIVE);
        if (active >= crew.getActiveWorkLimit()) {
            throw new ConflictException("Бригада '" + crew.getName() + "' достигла лимита активных работ ("
                    + crew.getActiveWorkLimit() + ")");
        }
    }
}
