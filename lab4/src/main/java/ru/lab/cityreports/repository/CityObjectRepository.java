package ru.lab.cityreports.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.lab.cityreports.model.CityObject;

public interface CityObjectRepository extends JpaRepository<CityObject, Long> {
}
