package com.reporting.controller;

import com.reporting.entity.SheetRelationship;
import com.reporting.repository.SheetRelationshipRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/relationships")
@CrossOrigin(origins = "*", maxAge = 3600)
public class RelationshipController {

    private final SheetRelationshipRepository repository;

    public RelationshipController(SheetRelationshipRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<SheetRelationship>> getAllRelationships() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<SheetRelationship> saveRelationship(@RequestBody SheetRelationship relationship) {
        return ResponseEntity.ok(repository.save(relationship));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRelationship(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
