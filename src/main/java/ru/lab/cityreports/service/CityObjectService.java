package ru.lab.cityreports.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab.cityreports.exception.ConflictException;
import ru.lab.cityreports.exception.NotFoundException;
import ru.lab.cityreports.model.CityObject;
import ru.lab.cityreports.model.ObjectState;
import ru.lab.cityreports.model.ObjectType;
import ru.lab.cityreports.model.WorkOrder;
import ru.lab.cityreports.model.WorkOrderStatus;
import ru.lab.cityreports.repository.CityObjectRepository;
import ru.lab.cityreports.repository.WorkOrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class CityObjectService {

    private final CityObjectRepository cityObjectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final Clock clock;

    public CityObjectService(CityObjectRepository cityObjectRepository,
                             WorkOrderRepository workOrderRepository,
                             Clock clock) {
        this.cityObjectRepository = cityObjectRepository;
        this.workOrderRepository = workOrderRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CityObject> findAll() {
        return cityObjectRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public CityObject getById(Long id) {
        return cityObjectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Городской объект с id=" + id + " не найден"));
    }

    @Transactional
    public CityObject create(String address, ObjectType type, ObjectState state) {
        if (state == ObjectState.UNDER_REPAIR) {
            throw new ConflictException("Состояние UNDER_REPAIR ставится только при начале работ по наряду");
        }
        CityObject object = new CityObject();
        object.setAddress(address.trim());
        object.setType(type);
        object.setState(state);
        Instant now = Instant.now(clock);
        object.setCreatedAt(now);
        object.setUpdatedAt(now);
        return cityObjectRepository.saveAndFlush(object);
    }

    @Transactional
    public CityObject update(Long id, String address, ObjectType type, ObjectState state) {
        CityObject object = getById(id);
        // сначала проверки, потом изменения (при ошибке транзакция откатится целиком)
        if (type != null && type != object.getType()) {
            ensureActiveCrewsSupport(id, type);
        }
        if (state != null && state != object.getState()) {
            if (object.getState() == ObjectState.UNDER_REPAIR || state == ObjectState.UNDER_REPAIR) {
                throw new ConflictException(
                        "Состояние UNDER_REPAIR управляется нарядами (начало/завершение/отмена работ)");
            }
        }
        if (address != null) {
            object.setAddress(address.trim());
        }
        if (type != null) {
            object.setType(type);
        }
        if (state != null) {
            object.setState(state);
        }
        object.setUpdatedAt(Instant.now(clock));
        return cityObjectRepository.saveAndFlush(object);
    }

    /** Каскад REMOVE (см. CityObject.reports -> CityReport.workOrders): удаляются обращения и наряды. */
    @Transactional
    public void delete(Long id) {
        cityObjectRepository.delete(getById(id));
    }

    /** Нельзя сменить тип объекта, если над ним активно работает бригада, которая этот тип не поддерживает. */
    private void ensureActiveCrewsSupport(Long objectId, ObjectType newType) {
        for (WorkOrder order : workOrderRepository.findByObjectWithCrew(objectId, WorkOrderStatus.ACTIVE)) {
            if (!order.getCrew().supports(newType)) {
                throw new ConflictException("Нельзя сменить тип объекта: активный наряд id=" + order.getId()
                        + " выполняет бригада, не поддерживающая тип " + newType);
            }
        }
    }
}
