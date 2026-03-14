package com.financeapp.controller;

import com.financeapp.dto.goal.CreateGoalRequest;
import com.financeapp.dto.goal.GoalResponse;
import com.financeapp.dto.goal.UpdateGoalRequest;
import com.financeapp.model.User;
import com.financeapp.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @PostMapping
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody CreateGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(currentUserId(), request));
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getAll() {
        return ResponseEntity.ok(goalService.findAll(currentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(goalService.findById(currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> update(@PathVariable Long id,
                                               @RequestBody UpdateGoalRequest request) {
        return ResponseEntity.ok(goalService.update(currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        goalService.delete(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
