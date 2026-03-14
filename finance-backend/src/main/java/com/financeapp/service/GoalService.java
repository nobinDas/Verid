package com.financeapp.service;

import com.financeapp.dto.goal.CreateGoalRequest;
import com.financeapp.dto.goal.GoalResponse;
import com.financeapp.dto.goal.UpdateGoalRequest;
import com.financeapp.model.Goal;
import com.financeapp.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;

    public GoalResponse create(Long userId, CreateGoalRequest req) {
        Goal goal = Goal.builder()
                .userId(userId)
                .title(req.title())
                .description(req.description())
                .targetAmount(req.targetAmount())
                .termType(req.termType())
                .deadline(req.deadline())
                .allocationPercent(req.allocationPercent() != null ? req.allocationPercent() : BigDecimal.ZERO)
                .build();
        return GoalResponse.from(goalRepository.save(goal));
    }

    public List<GoalResponse> findAll(Long userId) {
        return goalRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(GoalResponse::from)
                .toList();
    }

    public GoalResponse findById(Long userId, Long goalId) {
        return GoalResponse.from(getOwnedGoal(userId, goalId));
    }

    public GoalResponse update(Long userId, Long goalId, UpdateGoalRequest req) {
        Goal goal = getOwnedGoal(userId, goalId);
        if (req.title() != null)             goal.setTitle(req.title());
        if (req.description() != null)       goal.setDescription(req.description());
        if (req.targetAmount() != null)      goal.setTargetAmount(req.targetAmount());
        if (req.termType() != null)          goal.setTermType(req.termType());
        if (req.deadline() != null)          goal.setDeadline(req.deadline());
        if (req.allocationPercent() != null) goal.setAllocationPercent(req.allocationPercent());
        if (req.status() != null)            goal.setStatus(req.status());
        return GoalResponse.from(goalRepository.save(goal));
    }

    public void delete(Long userId, Long goalId) {
        Goal goal = getOwnedGoal(userId, goalId);
        goalRepository.delete(goal);
    }

    private Goal getOwnedGoal(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
    }
}
