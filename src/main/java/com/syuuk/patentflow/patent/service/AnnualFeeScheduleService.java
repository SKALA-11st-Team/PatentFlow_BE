package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class AnnualFeeScheduleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SystemSettingsService systemSettingsService;

    public AnnualFeeScheduleService(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    public LocalDate calculateNextDueDate(
            String country,
            LocalDate applicationDate,
            LocalDate registrationDate,
            LocalDate expectedExpirationDate
    ) {
        return calculateNextDueDate(country, applicationDate, registrationDate, expectedExpirationDate, LocalDate.now(KST));
    }

    public LocalDate calculateNextDueDate(
            String country,
            LocalDate applicationDate,
            LocalDate registrationDate,
            LocalDate expectedExpirationDate,
            LocalDate baseDate
    ) {
        if ("US".equalsIgnoreCase(country) && registrationDate != null) {
            for (int[] ym : new int[][]{{3, 6}, {7, 6}, {11, 6}}) {
                LocalDate feeDate = registrationDate.plusYears(ym[0]).plusMonths(ym[1]);
                if (!feeDate.isBefore(baseDate)) {
                    return capAtExpiration(feeDate, expectedExpirationDate);
                }
            }
            return expectedExpirationDate != null ? expectedExpirationDate : baseDate.plusYears(1);
        }

        LocalDate base = applicationDate != null ? applicationDate : registrationDate;
        if (base == null) {
            return LocalDate.of(baseDate.getYear(), 12, 31);
        }

        LocalDate dueDate = dateInYear(baseDate.getYear(), base);
        if (dueDate.isBefore(baseDate)) {
            dueDate = dateInYear(baseDate.getYear() + 1, base);
        }
        return capAtExpiration(dueDate, expectedExpirationDate);
    }

    public LocalDate advanceAfterMaintenance(
            String country,
            LocalDate currentDueDate,
            LocalDate expectedExpirationDate
    ) {
        if (currentDueDate == null) {
            return null;
        }
        LocalDate nextDueDate = currentDueDate.plusMonths(systemSettingsService.getCountryExtensionMonths(country));
        return capAtExpiration(nextDueDate, expectedExpirationDate);
    }

    private LocalDate dateInYear(int year, LocalDate base) {
        try {
            return LocalDate.of(year, base.getMonth(), base.getDayOfMonth());
        } catch (java.time.DateTimeException exception) {
            return LocalDate.of(year, base.getMonth(), 28);
        }
    }

    private LocalDate capAtExpiration(LocalDate dueDate, LocalDate expectedExpirationDate) {
        if (expectedExpirationDate != null && dueDate.isAfter(expectedExpirationDate)) {
            return expectedExpirationDate;
        }
        return dueDate;
    }
}
