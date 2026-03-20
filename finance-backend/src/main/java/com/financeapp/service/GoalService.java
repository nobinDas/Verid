package com.financeapp.service;

import com.financeapp.dto.goal.CreateGoalRequest;
import com.financeapp.dto.goal.GoalResponse;
import com.financeapp.dto.goal.UpdateGoalRequest;
import com.financeapp.model.CashEntry;
import com.financeapp.model.Goal;
import com.financeapp.model.GoalDeposit;
import com.financeapp.model.Transaction;
import com.financeapp.repository.CashEntryRepository;
import com.financeapp.repository.GoalDepositRepository;
import com.financeapp.repository.GoalRepository;
import com.financeapp.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GoalService {

    private static final Set<String> INCOME_CLS  = Set.of("INCOME", "CASH_IN");
    private static final Set<String> EXPENSE_CLS = Set.of("EXPENSE", "CASH_OUT");

    private final GoalRepository           goalRepository;
    private final GoalDepositRepository    goalDepositRepository;
    private final TransactionRepository    transactionRepository;
    private final CashEntryRepository      cashEntryRepository;

    public record SavingsSummary(BigDecimal totalSaving, BigDecimal totalDeposited, BigDecimal availableSaving) {}

    // ── CRUD ──────────────────────────────────────────────────────────────────

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
        return GoalResponse.from(goalRepository.save(goal), false);
    }

    public List<GoalResponse> findAll(Long userId) {
        LocalDate now = LocalDate.now();
        short month = (short) now.getMonthValue();
        short year  = (short) now.getYear();

        return goalRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(g -> {
                    boolean deposited = goalDepositRepository
                            .existsByGoalIdAndUserIdAndMonthAndYear(g.getId(), userId, month, year);
                    return GoalResponse.from(g, deposited);
                })
                .toList();
    }

    public GoalResponse findById(Long userId, Long goalId) {
        Goal goal = getOwnedGoal(userId, goalId);
        LocalDate now = LocalDate.now();
        boolean deposited = goalDepositRepository.existsByGoalIdAndUserIdAndMonthAndYear(
                goalId, userId, (short) now.getMonthValue(), (short) now.getYear());
        return GoalResponse.from(goal, deposited);
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
        LocalDate now = LocalDate.now();
        boolean deposited = goalDepositRepository.existsByGoalIdAndUserIdAndMonthAndYear(
                goalId, userId, (short) now.getMonthValue(), (short) now.getYear());
        return GoalResponse.from(goalRepository.save(goal), deposited);
    }

    public void delete(Long userId, Long goalId) {
        Goal goal = getOwnedGoal(userId, goalId);
        goalRepository.delete(goal);
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    @Transactional
    public GoalResponse deposit(Long userId, Long goalId, BigDecimal amount) {
        Goal goal = getOwnedGoal(userId, goalId);

        if (goal.getStatus() != Goal.GoalStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Goal is not active");
        }

        SavingsSummary summary = getSavingsSummary(userId);
        if (amount.compareTo(summary.availableSaving()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Amount exceeds available savings of $" + summary.availableSaving().toPlainString());
        }

        LocalDate now = LocalDate.now();
        short month = (short) now.getMonthValue();
        short year  = (short) now.getYear();

        GoalDeposit deposit = GoalDeposit.builder()
                .goalId(goalId)
                .userId(userId)
                .amount(amount)
                .month(month)
                .year(year)
                .build();
        goalDepositRepository.save(deposit);

        goal.setCurrentAmount(goal.getCurrentAmount().add(amount));
        if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(Goal.GoalStatus.COMPLETED);
        }
        goalRepository.save(goal);

        return GoalResponse.from(goal, true);
    }

    // ── Savings summary ───────────────────────────────────────────────────────

    public SavingsSummary getSavingsSummary(Long userId) {
        List<Transaction> txList     = transactionRepository.findByUserIdOrderByDateDesc(userId);
        List<CashEntry>   cashList   = cashEntryRepository.findByUserIdOrderByDateDesc(userId);

        BigDecimal income   = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (Transaction t : txList) {
            String cls = t.getClassification();
            if (INCOME_CLS.contains(cls))       income   = income.add(t.getAmount());
            else if (EXPENSE_CLS.contains(cls)) expenses = expenses.add(t.getAmount());
            // EXTRA_IN intentionally excluded
        }
        for (CashEntry c : cashList) {
            if ("IN".equals(c.getType()))  income   = income.add(c.getAmount());
            else                           expenses = expenses.add(c.getAmount());
        }

        BigDecimal totalSaving    = income.subtract(expenses).max(BigDecimal.ZERO);
        BigDecimal totalDeposited = goalDepositRepository.sumAmountByUserId(userId);
        BigDecimal available      = totalSaving.subtract(totalDeposited).max(BigDecimal.ZERO);

        return new SavingsSummary(totalSaving, totalDeposited, available);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private Goal getOwnedGoal(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
    }
}
