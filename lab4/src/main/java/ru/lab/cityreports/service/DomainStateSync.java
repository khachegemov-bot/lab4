package ru.lab.cityreports.service;

import org.springframework.stereotype.Component;
import ru.lab.cityreports.model.CityObject;
import ru.lab.cityreports.model.CityReport;
import ru.lab.cityreports.model.ObjectState;
import ru.lab.cityreports.model.ReportStatus;
import ru.lab.cityreports.model.WorkOrderStatus;
import ru.lab.cityreports.repository.CityReportRepository;
import ru.lab.cityreports.repository.WorkOrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Поддерживает согласованность связанных сущностей после изменений.
 * Вызывается только внутри транзакций сервисов.
 *
 * Правила:
 *  - обращение (если не закрыто) IN_PROGRESS  <=> у него есть активный наряд, иначе NEW;
 *  - объект UNDER_REPAIR <=> по нему есть наряд IN_PROGRESS;
 *  - объект с открытыми обращениями не может быть GOOD (он NEEDS_REPAIR, если не DANGEROUS);
 *  - после завершения ремонта объект становится GOOD, если открытых обращений по нему больше нет.
 */
@Component
public class DomainStateSync {

    private static final List<ReportStatus> OPEN_REPORT = List.of(ReportStatus.NEW, ReportStatus.IN_PROGRESS);

    private final WorkOrderRepository workOrderRepository;
    private final CityReportRepository cityReportRepository;
    private final Clock clock;

    public DomainStateSync(WorkOrderRepository workOrderRepository,
                           CityReportRepository cityReportRepository,
                           Clock clock) {
        this.workOrderRepository = workOrderRepository;
        this.cityReportRepository = cityReportRepository;
        this.clock = clock;
    }

    public void syncReportStatus(CityReport report) {
        if (report.isClosed()) {
            return;                                   // закрытые обращения не трогаем
        }
        boolean hasActive = workOrderRepository.existsByReportIdAndStatusIn(report.getId(), WorkOrderStatus.ACTIVE);
        ReportStatus target = hasActive ? ReportStatus.IN_PROGRESS : ReportStatus.NEW;
        if (report.getStatus() != target) {
            report.setStatus(target);
            report.setUpdatedAt(Instant.now(clock));
        }
    }

    /** @param repairCompleted true — вызвано сразу после успешного завершения наряда. */
    public void syncObjectState(CityObject object, boolean repairCompleted) {
        ObjectState current = object.getState();
        ObjectState target;

        boolean inProgress = workOrderRepository.existsByReport_Object_IdAndStatusIn(
                object.getId(), List.of(WorkOrderStatus.IN_PROGRESS));
        if (inProgress) {
            target = ObjectState.UNDER_REPAIR;
        } else {
            boolean hasOpenReports = cityReportRepository.existsByObjectIdAndStatusIn(object.getId(), OPEN_REPORT);
            if (repairCompleted && !hasOpenReports) {
                target = ObjectState.GOOD;                       // всё отремонтировано
            } else if (current == ObjectState.UNDER_REPAIR) {
                target = ObjectState.NEEDS_REPAIR;               // работы остановлены
            } else if (current == ObjectState.GOOD && hasOpenReports) {
                target = ObjectState.NEEDS_REPAIR;               // поступила жалоба
            } else {
                target = current;
            }
        }
        if (current != target) {
            object.setState(target);
            object.setUpdatedAt(Instant.now(clock));
        }
    }
}
