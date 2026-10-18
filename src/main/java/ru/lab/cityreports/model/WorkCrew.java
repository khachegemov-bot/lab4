package ru.lab.cityreports.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Рабочая бригада: название, поддерживаемые типы объектов, лимит активных работ. Таблица work_crews. */
@Entity
@Table(name = "work_crews")
public class WorkCrew {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Набор типов объектов, с которыми работает бригада.
     * Хранится в отдельной таблице work_crew_supported_types (crew_id, object_type).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "work_crew_supported_types", joinColumns = @JoinColumn(name = "crew_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 20)
    private Set<ObjectType> supportedTypes = EnumSet.noneOf(ObjectType.class);

    @Column(name = "active_work_limit", nullable = false)
    private Integer activeWorkLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<ObjectType> getSupportedTypes() { return supportedTypes; }
    public void setSupportedTypes(Set<ObjectType> types) {
        // меняем содержимое коллекции, а не саму ссылку — так Hibernate корректно отследит изменения
        this.supportedTypes.clear();
        if (types != null) {
            this.supportedTypes.addAll(types);
        }
    }
    public Integer getActiveWorkLimit() { return activeWorkLimit; }
    public void setActiveWorkLimit(Integer activeWorkLimit) { this.activeWorkLimit = activeWorkLimit; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean supports(ObjectType type) {
        return supportedTypes.contains(type);
    }
}
