package ru.lab.cityreports.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.lab.cityreports.model.WorkOrder;
import ru.lab.cityreports.model.WorkOrderStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    boolean existsByReportIdAndStatusIn(Long reportId, Collection<WorkOrderStatus> statuses);

    boolean existsByCrewId(Long crewId);

    /** Количество нарядов бригады в указанных статусах (для лимита активных работ). */
    long countByCrewIdAndStatusIn(Long crewId, Collection<WorkOrderStatus> statuses);

    /** Активные наряды бригады вместе с типом объекта (JPQL, параметры только через :name). */
    @Query("""
            select o from WorkOrder o
              join fetch o.report r
              join fetch r.object
            where o.crew.id = :crewId and o.status in :statuses
            """)
    List<WorkOrder> findByCrewWithObject(@Param("crewId") Long crewId,
                                         @Param("statuses") Collection<WorkOrderStatus> statuses);

    /** Активные наряды по объекту — вместе с бригадой (нужно при смене типа объекта). */
    @Query("""
            select o from WorkOrder o
              join fetch o.crew
            where o.report.object.id = :objectId and o.status in :statuses
            """)
    List<WorkOrder> findByObjectWithCrew(@Param("objectId") Long objectId,
                                         @Param("statuses") Collection<WorkOrderStatus> statuses);

    /** Есть ли по объекту наряды в указанных статусах. */
    boolean existsByReport_Object_IdAndStatusIn(Long objectId, Collection<WorkOrderStatus> statuses);

    /** Активный наряд обращения (по правилам предметной области он максимум один). */
    Optional<WorkOrder> findFirstByReportIdAndStatusIn(Long reportId, Collection<WorkOrderStatus> statuses);
}
