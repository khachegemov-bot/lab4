package ru.lab.cityreports.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.lab.cityreports.model.CityReport;
import ru.lab.cityreports.model.ReportStatus;

import java.util.Collection;
import java.util.List;

public interface CityReportRepository extends JpaRepository<CityReport, Long> {

    /** Есть ли у объекта обращения в указанных статусах (Spring Data строит запрос по имени метода). */
    /** Обращения одного автора — житель видит только их. */
    List<CityReport> findByAuthorIdOrderByIdAsc(Long authorId);

    /** Является ли пользователь автором обращения (проверка доступа "только своё"). */
    boolean existsByIdAndAuthorId(Long id, Long authorId);

    boolean existsByObjectIdAndStatusIn(Long objectId, Collection<ReportStatus> statuses);

    /** id объектов, по которым есть обращения данного автора (нужно, чтобы пересчитать их состояние). */
    @Query("select distinct r.object.id from CityReport r where r.author.id = :authorId")
    List<Long> findObjectIdsByAuthor(@Param("authorId") Long authorId);
}
