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
        // 연차료 기준일은 출원일이 원칙이나, 출원일이 없는 데이터(마이그레이션·해외 등록 등)는 등록일로 폴백한다.
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

    /**
     * FEE-05: 저장된 납부일이 과거(baseDate 이전)이면 국가 연장 개월 단위로 굴려 '오늘 이후 가장 가까운 도래일'을
     * 반환한다. 저장값은 변경하지 않는다(조회 시점 재계산). null/미래면 그대로 반환, 만료일을 넘기지 않는다.
     */
    public LocalDate rollForwardToFuture(
            String country,
            LocalDate storedDueDate,
            LocalDate expectedExpirationDate
    ) {
        return rollForwardToFuture(country, storedDueDate, expectedExpirationDate, LocalDate.now(KST));
    }

    public LocalDate rollForwardToFuture(
            String country,
            LocalDate storedDueDate,
            LocalDate expectedExpirationDate,
            LocalDate baseDate
    ) {
        if (storedDueDate == null || !storedDueDate.isBefore(baseDate)) {
            return storedDueDate;
        }
        int months = getCountryExtensionMonths(country);
        if (months <= 0) {
            return storedDueDate;
        }
        LocalDate dueDate = storedDueDate;
        while (dueDate.isBefore(baseDate)) {
            LocalDate next = capAtExpiration(dueDate.plusMonths(months), expectedExpirationDate);
            if (!next.isAfter(dueDate)) {
                // 만료일에 도달해 더 전진 불가 — 만료일(여전히 과거일 수 있음) 반환.
                return next;
            }
            dueDate = next;
        }
        return dueDate;
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
