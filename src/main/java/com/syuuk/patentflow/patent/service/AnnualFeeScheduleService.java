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
        LocalDate base = annualFeeBaseDate(country, applicationDate, registrationDate);
        if (base == null) {
            return LocalDate.of(baseDate.getYear(), 12, 31);
        }

        if (isUsPatent(country) && registrationDate != null) {
            return capAtExpiration(nextUsMaintenanceDueDate(registrationDate, expectedExpirationDate, baseDate), expectedExpirationDate);
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
        int months = isUsPatent(country) ? 48 : getCountryExtensionMonths(country);
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
        int months = isUsPatent(country) ? 48 : systemSettingsService.getCountryExtensionMonths(country);
        LocalDate nextDueDate = currentDueDate.plusMonths(months);
        return capAtExpiration(nextDueDate, expectedExpirationDate);
    }


    public LocalDate annualFeeBaseDate(String country, LocalDate applicationDate, LocalDate registrationDate) {
        if (isUsPatent(country) && registrationDate != null) {
            return registrationDate;
        }
        // 연차료 기준일은 출원일이 원칙이나, 출원일이 없는 데이터(마이그레이션·해외 등록 등)는 등록일로 폴백한다.
        return applicationDate != null ? applicationDate : registrationDate;
    }

    public String annualFeeBasis(String country) {
        return isUsPatent(country) ? "REGISTRATION_DATE" : "APPLICATION_DATE";
    }

    public String paymentRuleLabel(String country) {
        return isUsPatent(country)
                ? "등록일 기준 3년 6개월, 7년 6개월, 11년 6개월 유지료"
                : "출원일 기준 매년 도래하는 연차료";
    }

    private LocalDate nextUsMaintenanceDueDate(LocalDate registrationDate, LocalDate expectedExpirationDate, LocalDate baseDate) {
        LocalDate[] dueDates = new LocalDate[] {
                registrationDate.plusMonths(42),
                registrationDate.plusMonths(90),
                registrationDate.plusMonths(138)
        };
        for (LocalDate dueDate : dueDates) {
            if (!dueDate.isBefore(baseDate)) {
                return dueDate;
            }
        }
        return expectedExpirationDate != null ? expectedExpirationDate : dueDates[dueDates.length - 1];
    }

    private boolean isUsPatent(String country) {
        return country != null && "US".equalsIgnoreCase(country.trim());
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
