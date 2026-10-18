package ru.lab.cityreports.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab.cityreports.exception.ConflictException;
import ru.lab.cityreports.exception.NotFoundException;
import ru.lab.cityreports.model.CityObject;
import ru.lab.cityreports.model.CityReport;
import ru.lab.cityreports.model.ReportStatus;
import ru.lab.cityreports.model.User;
import ru.lab.cityreports.repository.CityObjectRepository;
import ru.lab.cityreports.repository.CityReportRepository;
import ru.lab.cityreports.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class CityReportService {

    private final CityReportRepository cityReportRepository;
    private final CityObjectRepository cityObjectRepository;
    private final UserRepository userRepository;
    private final DomainStateSync stateSync;
    private final Clock clock;

    public CityReportService(CityReportRepository cityReportRepository,
                             CityObjectRepository cityObjectRepository,
                             UserRepository userRepository,
                             DomainStateSync stateSync,
                             Clock clock) {
        this.cityReportRepository = cityReportRepository;
        this.cityObjectRepository = cityObjectRepository;
        this.userRepository = userRepository;
        this.stateSync = stateSync;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CityReport> findAll() {
        return cityReportRepository.findAll(Sort.by("id"));
    }

    /** Обращения одного автора (для жителя). */
    @Transactional(readOnly = true)
    public List<CityReport> findByAuthor(Long authorId) {
        return cityReportRepository.findByAuthorIdOrderByIdAsc(authorId);
    }

    @Transactional(readOnly = true)
    public CityReport getById(Long id) {
        return cityReportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Обращение с id=" + id + " не найдено"));
    }

    /** Новое обращение всегда NEW; если объект был GOOD, он становится NEEDS_REPAIR. */
    @Transactional
    public CityReport create(Long objectId, Long authorId, String description) {
        CityObject object = cityObjectRepository.findById(objectId)
                .orElseThrow(() -> new NotFoundException("Городской объект с id=" + objectId + " не найден"));
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + authorId + " не найден"));

        CityReport report = new CityReport();
        report.setObject(object);
        report.setAuthor(author);
        report.setDescription(description.trim());
        report.setStatus(ReportStatus.NEW);                  // статус задаёт сервер
        Instant now = Instant.now(clock);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        cityReportRepository.saveAndFlush(report);

        stateSync.syncObjectState(object, false);
        return report;
    }

    /**
     * PATCH меняет только описание. Статус меняется только бизнес-операциями.
     * @param onlyWhileNew true — правит автор-житель: после назначения бригады описание
     *                     уже нельзя менять, бригада работает по нему.
     */
    @Transactional
    public CityReport update(Long id, String description, boolean onlyWhileNew) {
        CityReport report = getById(id);
        if (report.isClosed()) {
            throw new ConflictException("Обращение в статусе " + report.getStatus() + " закрыто и не изменяется");
        }
        if (onlyWhileNew && report.getStatus() != ReportStatus.NEW) {
            throw new ConflictException("Автор может изменить обращение только до назначения бригады (статус NEW), "
                    + "сейчас " + report.getStatus());
        }
        if (description != null) {
            report.setDescription(description.trim());
        }
        report.setUpdatedAt(Instant.now(clock));
        return cityReportRepository.saveAndFlush(report);
    }

    /**
     * Каскад REMOVE: удаляются наряды обращения, затем пересчитывается состояние объекта.
     * @param onlyWhileNew true — удаляет автор-житель: отозвать можно только ещё не взятое в работу обращение.
     */
    @Transactional
    public void delete(Long id, boolean onlyWhileNew) {
        CityReport report = getById(id);
        if (onlyWhileNew && report.getStatus() != ReportStatus.NEW) {
            throw new ConflictException("Автор может отозвать только новое обращение (статус NEW), сейчас "
                    + report.getStatus());
        }
        CityObject object = report.getObject();
        cityReportRepository.delete(report);
        cityReportRepository.flush();
        stateSync.syncObjectState(object, false);
    }
}
