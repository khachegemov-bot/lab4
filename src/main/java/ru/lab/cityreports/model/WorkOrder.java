package ru.lab.cityreports.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Наряд на работу: обращение, бригада, срок, статус, результат. Таблица work_orders. */
@Entity
@Table(name = "work_orders")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Многие наряды -> одно обращение. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private CityReport report;

    /** Многие наряды -> одна бригада. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    private WorkCrew crew;

    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkOrderStatus status;

    @Column(name = "result_description", length = 2000)
    private String resultDescription;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;      // ставится сервером при переходе в IN_PROGRESS

    @Column(name = "completed_at")
    private Instant completedAt;    // ставится сервером при переходе в DONE

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getId() { return id; }
    public CityReport getReport() { return report; }
    public void setReport(CityReport report) { this.report = report; }
    public WorkCrew getCrew() { return crew; }
    public void setCrew(WorkCrew crew) { this.crew = crew; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public WorkOrderStatus getStatus() { return status; }
    public void setStatus(WorkOrderStatus status) { this.status = status; }
    public String getResultDescription() { return resultDescription; }
    public void setResultDescription(String resultDescription) { this.resultDescription = resultDescription; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
