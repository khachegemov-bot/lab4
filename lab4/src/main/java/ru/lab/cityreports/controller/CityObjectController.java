package ru.lab.cityreports.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.lab.cityreports.dto.CityObjectCreateRequest;
import ru.lab.cityreports.dto.CityObjectPatchRequest;
import ru.lab.cityreports.dto.CityObjectResponse;
import ru.lab.cityreports.model.CityObject;
import ru.lab.cityreports.service.CityObjectService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/city-objects")
public class CityObjectController {

    private final CityObjectService cityObjectService;

    public CityObjectController(CityObjectService cityObjectService) {
        this.cityObjectService = cityObjectService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CityObjectResponse> getAll() {
        return cityObjectService.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CityObjectResponse getById(@PathVariable Long id) {
        return toResponse(cityObjectService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OBJECT_MANAGE')")
    public ResponseEntity<CityObjectResponse> create(@Valid @RequestBody CityObjectCreateRequest request) {
        CityObject created = cityObjectService.create(request.address(), request.type(), request.state());
        return ResponseEntity.created(URI.create("/api/city-objects/" + created.getId()))
                .body(toResponse(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('OBJECT_MANAGE')")
    public CityObjectResponse update(@PathVariable Long id, @Valid @RequestBody CityObjectPatchRequest request) {
        return toResponse(cityObjectService.update(id, request.address(), request.type(), request.state()));
    }

    /** Удаление каскадно стирает обращения и наряды — только администратор. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('OBJECT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cityObjectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private CityObjectResponse toResponse(CityObject o) {
        return new CityObjectResponse(o.getId(), o.getAddress(), o.getType(), o.getState(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
