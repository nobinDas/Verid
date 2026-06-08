package com.financeapp.scheduler;

import com.financeapp.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReceiptCleanupScheduler {

    private final ReceiptService receiptService;

    @Scheduled(cron = "0 0 2 * * *")
    public void deleteExpiredReceipts() {
        receiptService.deleteExpired();
    }
}
