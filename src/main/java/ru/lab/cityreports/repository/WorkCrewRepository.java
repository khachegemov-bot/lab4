package ru.lab.cityreports.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.lab.cityreports.model.WorkCrew;

import java.util.Optional;

public interface WorkCrewRepository extends JpaRepository<WorkCrew, Long> {

    /**
     * SELECT ... FOR UPDATE: строка бригады блокируется до конца транзакции.
     * Второй параллельный запрос на ту же бригаду ждёт, поэтому два наряда
     * не смогут одновременно "пролезть" в последнее свободное место лимита.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WorkCrew c where c.id = :id")
    Optional<WorkCrew> findByIdForUpdate(@Param("id") Long id);
}
