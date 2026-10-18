package ru.lab.cityreports.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Городской объект: адрес, тип объекта, состояние. Таблица city_objects. */
@Entity
@Table(name = "city_objects")
public class CityObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ObjectType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ObjectState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Обращения по объекту. При удалении объекта удаляются и они (каскад REMOVE). */
    @OneToMany(mappedBy = "object", cascade = CascadeType.REMOVE)
    private List<CityReport> reports = new ArrayList<>();

    public Long getId() { return id; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public ObjectType getType() { return type; }
    public void setType(ObjectType type) { this.type = type; }
    public ObjectState getState() { return state; }
    public void setState(ObjectState state) { this.state = state; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<CityReport> getReports() { return reports; }
}
