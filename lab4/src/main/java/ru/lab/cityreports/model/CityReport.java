package ru.lab.cityreports.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Обращение жителя: объект, автор, описание проблемы, статус. Таблица city_reports. */
@Entity
@Table(name = "city_reports")
public class CityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Многие обращения -> один объект (внешний ключ object_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "object_id", nullable = false)
    private CityObject object;

    /** Многие обращения -> один автор (внешний ключ author_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    /** Причина отклонения — заполняется только бизнес-операцией "отклонить обращение". */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Наряды по обращению. При удалении обращения удаляются и они (каскад REMOVE). */
    @OneToMany(mappedBy = "report", cascade = CascadeType.REMOVE)
    private List<WorkOrder> workOrders = new ArrayList<>();

    public Long getId() { return id; }
    public CityObject getObject() { return object; }
    public void setObject(CityObject object) { this.object = object; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<WorkOrder> getWorkOrders() { return workOrders; }

    /** Обращение закрыто: решено или отклонено. Закрытые обращения больше не меняются. */
    public boolean isClosed() {
        return status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED;
    }
}
