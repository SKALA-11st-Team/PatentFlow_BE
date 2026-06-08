package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class AnnualFeeScheduleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_EXTENSION_MONTHS = 12;

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
        LocalDate base = applicationDate;
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

    public int getCountryExtensionMonths(String country) {
        if (country == null || country.isBlank()) {
            return DEFAULT_EXTENSION_MONTHS;
        }
        return systemSettingsService.getCountryExtensionMonths(country);
    }
}
