package com.financeapp.service;

import com.financeapp.dto.goal.CreateGoalRequest;
import com.financeapp.model.Goal;
import com.financeapp.model.Transaction;
import com.financeapp.repository.CashEntryRepository;
import com.financeapp.repository.GoalDepositRepository;
import com.financeapp.repository.GoalRepository;
import com.financeapp.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock GoalRepository goalRepository;
    @Mock GoalDepositRepository goalDepositRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock CashEntryRepository cashEntryRepository;

    @InjectMocks
    GoalService goalService;

    // ── Savings summary ───────────────────────────────────────────────────────

    @Test
    void getSavingsSummary_withIncomeAndExpenses_calculatesCorrectly() {
        Long userId = 1L;

        Transaction income = Transaction.builder()
                .userId(userId).amount(new BigDecimal("3000.00"))
                .classification("INCOME").type(Transaction.TransactionType.CREDIT)
                .date(LocalDate.now()).description("Salary")
                .createdAt(LocalDateTime.now()).build();

        Transaction expense = Transaction.builder()
                .userId(userId).amount(new BigDecimal("1000.00"))
                .classification("EXPENSE").type(Transaction.TransactionType.DEBIT)
                .date(LocalDate.now()).description("Rent")
                .createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of(income, expense));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of());
        when(goalDepositRepository.sumAmountByUserId(userId)).thenReturn(new BigDecimal("200.00"));

        GoalService.SavingsSummary summary = goalService.getSavingsSummary(userId);

        assertThat(summary.totalSaving()).isEqualByComparingTo("2000.00");
        assertThat(summary.totalDeposited()).isEqualByComparingTo("200.00");
        assertThat(summary.availableSaving()).isEqualByComparingTo("1800.00");
    }

    @Test
    void getSavingsSummary_noData_returnsAllZeros() {
        Long userId = 2L;
        when(transactionRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of());
        when(cashEntryRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of());
        when(goalDepositRepository.sumAmountByUserId(userId)).thenReturn(BigDecimal.ZERO);

        GoalService.SavingsSummary summary = goalService.getSavingsSummary(userId);

        assertThat(summary.totalSaving()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.totalDeposited()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.availableSaving()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getSavingsSummary_expensesExceedIncome_availableIsZeroNotNegative() {
        Long userId = 3L;

        Transaction income = Transaction.builder()
                .userId(userId).amount(new BigDecimal("500.00"))
                .classification("INCOME").type(Transaction.TransactionType.CREDIT)
                .date(LocalDate.now()).description("Side job")
                .createdAt(LocalDateTime.now()).build();

        Transaction expense = Transaction.builder()
                .userId(userId).amount(new BigDecimal("1000.00"))
                .classification("EXPENSE").type(Transaction.TransactionType.DEBIT)
                .date(LocalDate.now()).description("Overspend")
                .createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of(income, expense));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of());
        when(goalDepositRepository.sumAmountByUserId(userId)).thenReturn(BigDecimal.ZERO);

        GoalService.SavingsSummary summary = goalService.getSavingsSummary(userId);

        assertThat(summary.totalSaving()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.availableSaving()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getSavingsSummary_extraInClassification_isExcludedFromTotalSaving() {
        Long userId = 4L;

        Transaction extraIn = Transaction.builder()
                .userId(userId).amount(new BigDecimal("500.00"))
                .classification("EXTRA_IN").type(Transaction.TransactionType.CREDIT)
                .date(LocalDate.now()).description("Bonus")
                .createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of(extraIn));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of());
        when(goalDepositRepository.sumAmountByUserId(userId)).thenReturn(BigDecimal.ZERO);

        GoalService.SavingsSummary summary = goalService.getSavingsSummary(userId);

        // EXTRA_IN is intentionally excluded from totalSaving
        assertThat(summary.totalSaving()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    @Test
    void deposit_insufficientSavings_throwsBadRequest() {
        Long userId = 1L, goalId = 1L;
        Goal goal = activeGoal(goalId, userId, "500.00");

        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(transactionRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of());
        when(cashEntryRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of());
        when(goalDepositRepository.sumAmountByUserId(userId)).thenReturn(BigDecimal.ZERO);

        // availableSaving = 0, trying to deposit 100 → should fail
        assertThatThrownBy(() -> goalService.deposit(userId, goalId, new BigDecimal("100.00")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deposit_success_increasesCurrentAmount() {
        Long userId = 1L, goalId = 1L;
        Goal goal = activeGoal(goalId, userId, "5000.00");

        // income gives availableSaving = 2000
        Transaction income = Transaction.builder()
                .userId(userId).amount(new BigDecimal("2000.00"))
                .classification("INCOME").type(Transaction.TransactionType.CREDIT)
                .date(LocalDate.now()).description("Salary").createdAt(LocalDateTime.now()).build();

        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(transactionRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of(income));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(userId)).thenReturn(List.of());
        when(goalDepositRepository.sumAmountByUserId(userId)).thenReturn(BigDecimal.ZERO);
        when(goalDepositRepository.save(any())).thenReturn(null);
        when(goalRepository.save(any())).thenReturn(goal);

        goalService.deposit(userId, goalId, new BigDecimal("500.00"));

        assertThat(goal.getCurrentAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void deposit_goalNotOwnedByUser_throwsNotFound() {
        Long userId = 1L, goalId = 99L;
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.deposit(userId, goalId, new BigDecimal("100.00")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deposit_inactiveGoal_throwsBadRequest() {
        Long userId = 1L, goalId = 1L;
        Goal goal = activeGoal(goalId, userId, "1000.00");
        goal.setStatus(Goal.GoalStatus.COMPLETED);

        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> goalService.deposit(userId, goalId, new BigDecimal("100.00")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void delete_goalNotOwned_throwsNotFound() {
        Long userId = 1L, goalId = 99L;
        when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.delete(userId, goalId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void create_savesGoalAndReturnsResponse() {
        Long userId = 1L;
        CreateGoalRequest request = new CreateGoalRequest(
                "Vacation", "Hawaii trip", new BigDecimal("3000.00"),
                Goal.TermType.SHORT, LocalDate.now().plusMonths(6), new BigDecimal("10.00"));

        Goal saved = Goal.builder().id(1L).userId(userId).title("Vacation")
                .description("Hawaii trip").targetAmount(new BigDecimal("3000.00"))
                .currentAmount(BigDecimal.ZERO).termType(Goal.TermType.SHORT)
                .status(Goal.GoalStatus.ACTIVE).allocationPercent(new BigDecimal("10.00"))
                .createdAt(LocalDateTime.now()).build();

        when(goalRepository.save(any())).thenReturn(saved);

        var response = goalService.create(userId, request);

        assertThat(response.title()).isEqualTo("Vacation");
        assertThat(response.targetAmount()).isEqualByComparingTo("3000.00");
        verify(goalRepository).save(any());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Goal activeGoal(Long id, Long userId, String target) {
        return Goal.builder()
                .id(id).userId(userId).title("Test Goal")
                .targetAmount(new BigDecimal(target))
                .currentAmount(BigDecimal.ZERO)
                .termType(Goal.TermType.MID)
                .status(Goal.GoalStatus.ACTIVE)
                .allocationPercent(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
