package ru.lab.cityreports.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab.cityreports.exception.ConflictException;
import ru.lab.cityreports.exception.NotFoundException;
import ru.lab.cityreports.model.ObjectType;
import ru.lab.cityreports.model.WorkCrew;
import ru.lab.cityreports.model.WorkOrder;
import ru.lab.cityreports.model.WorkOrderStatus;
import ru.lab.cityreports.repository.WorkCrewRepository;
import ru.lab.cityreports.repository.WorkOrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class WorkCrewService {

    private final WorkCrewRepository workCrewRepository;
    private final WorkOrderRepository workOrderRepository;
    private final Clock clock;

    public WorkCrewService(WorkCrewRepository workCrewRepository,
                           WorkOrderRepository workOrderRepository,
                           Clock clock) {
        this.workCrewRepository = workCrewRepository;
        this.workOrderRepository = workOrderRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkCrew> findAll() {
        return workCrewRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public WorkCrew getById(Long id) {
        return workCrewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Бригада с id=" + id + " не найдена"));
    }

    /** Сколько активных нарядов сейчас у бригады (вычисляемое поле для ответа). */
    @Transactional(readOnly = true)
    public long countActiveWork(Long crewId) {
        return workOrderRepository.countByCrewIdAndStatusIn(crewId, WorkOrderStatus.ACTIVE);
    }

    @Transactional
    public WorkCrew create(String name, Set<ObjectType> supportedTypes, Integer activeWorkLimit) {
        WorkCrew crew = new WorkCrew();
        crew.setName(name.trim());
        crew.setSupportedTypes(supportedTypes);
        crew.setActiveWorkLimit(activeWorkLimit);
        Instant now = Instant.now(clock);
        crew.setCreatedAt(now);
        crew.setUpdatedAt(now);
        return workCrewRepository.saveAndFlush(crew);
    }

    @Transactional
    public WorkCrew update(Long id, String name, Set<ObjectType> supportedTypes, Integer activeWorkLimit) {
        // блокируем строку бригады, чтобы параллельно не назначили наряд, пока меняем лимит/типы
        WorkCrew crew = workCrewRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Бригада с id=" + id + " не найдена"));
        if (activeWorkLimit != null) {
            long active = countActiveWork(id);
            if (activeWorkLimit < active) {
                throw new ConflictException("Новый лимит (" + activeWorkLimit
                        + ") меньше текущего числа активных работ (" + active + ")");
            }
        }
        if (supportedTypes != null) {
            for (WorkOrder order : workOrderRepository.findByCrewWithObject(id, WorkOrderStatus.ACTIVE)) {
                ObjectType type = order.getReport().getObject().getType();
                if (!supportedTypes.contains(type)) {
                    throw new ConflictException("Нельзя убрать тип " + type
                            + ": у бригады есть активный наряд id=" + order.getId() + " по объекту этого типа");
                }
            }
        }
        if (name != null) {
            crew.setName(name.trim());
        }
        if (activeWorkLimit != null) {
            crew.setActiveWorkLimit(activeWorkLimit);
        }
        if (supportedTypes != null) {
            crew.setSupportedTypes(supportedTypes);
        }
        crew.setUpdatedAt(Instant.now(clock));
        return workCrewRepository.saveAndFlush(crew);
    }

    /** Бригаду с нарядами (включая историю выполненных работ) удалить нельзя. */
    @Transactional
    public void delete(Long id) {
        WorkCrew crew = getById(id);
        if (workOrderRepository.existsByCrewId(id)) {
            throw new ConflictException("У бригады есть наряды (в том числе история работ) — удалить её нельзя");
        }
        workCrewRepository.delete(crew);
    }
}
