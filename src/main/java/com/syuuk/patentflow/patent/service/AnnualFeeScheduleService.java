package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * @relatedFR FR-LEGAL-24
 * FEE-06: 국가별 연차료 납부 규칙(CountryAnnualFeeRule) 기반 도래일 계산.
 * KR·US는 정밀 규칙(등록일 기준)을 내장하고, 그 외 국가는 출원일 기준 기본 규칙에
 * system_settings(fee.rule.{CC}.*) 오버라이드를 얹는다.
 */
@Service
public class AnnualFeeScheduleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int KR_DEFAULT_INITIAL_LUMP_YEARS = 3;
    private static final int KR_DEFAULT_CYCLE_MONTHS = 12;
    private static final List<Integer> US_MAINTENANCE_MONTHS = List.of(42, 90, 138);
    private static final int US_MAINTENANCE_CYCLE_MONTHS = 48;

    private final SystemSettingsService systemSettingsService;

    public AnnualFeeScheduleService(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    /**
     * FEE-06: 국가별 연차료 규칙을 해석한다. KR(설정등록 시 1~3년차 일괄, 4년차부터 등록일
     * 기준 매년)과 US(등록일 기준 3.5/7.5/11.5년 유지료)는 정밀 기본값을 내장하고,
     * system_settings의 fee.rule.{CC}.* 키로 국가별 기본값을 덮어쓸 수 있다.
     */
    public CountryAnnualFeeRule ruleFor(String country) {
        String normalized = country == null ? "" : country.trim().toUpperCase();
        if ("US".equals(normalized)) {
            return new CountryAnnualFeeRule(
                    normalized,
                    CountryAnnualFeeRule.BASIS_REGISTRATION_DATE,
                    0,
                    US_MAINTENANCE_CYCLE_MONTHS,
                    US_MAINTENANCE_MONTHS,
                    "등록일 기준 3년 6개월, 7년 6개월, 11년 6개월 유지료");
        }
        if ("KR".equals(normalized)) {
            String basis = basisOrDefault(normalized, CountryAnnualFeeRule.BASIS_REGISTRATION_DATE);
            int lumpYears = lumpYearsOrDefault(normalized, KR_DEFAULT_INITIAL_LUMP_YEARS);
            int cycleMonths = cycleMonthsOrDefault(normalized, KR_DEFAULT_CYCLE_MONTHS);
            return new CountryAnnualFeeRule(normalized, basis, lumpYears, cycleMonths, List.of(),
                    annualRuleLabel(basis, lumpYears));
        }
        if (normalized.isBlank()) {
            return new CountryAnnualFeeRule("", CountryAnnualFeeRule.BASIS_APPLICATION_DATE, 0,
                    KR_DEFAULT_CYCLE_MONTHS, List.of(),
                    annualRuleLabel(CountryAnnualFeeRule.BASIS_APPLICATION_DATE, 0));
        }
        String basis = basisOrDefault(normalized, CountryAnnualFeeRule.BASIS_APPLICATION_DATE);
        int lumpYears = lumpYearsOrDefault(normalized, 0);
        Integer cycleOverride = systemSettingsService.getCountryFeeCycleMonthsOverride(normalized);
        int cycleMonths = cycleOverride != null && cycleOverride > 0
                ? cycleOverride
                : systemSettingsService.getCountryExtensionMonths(normalized);
        return new CountryAnnualFeeRule(normalized, basis, lumpYears, cycleMonths, List.of(),
                annualRuleLabel(basis, lumpYears));
    }

    /** FEE-06: basis 오버라이드는 알려진 값일 때만 적용한다(오타·잘못된 설정 무시). */
    private String basisOrDefault(String country, String fallback) {
        String override = systemSettingsService.getCountryFeeBasisOverride(country);
        if (CountryAnnualFeeRule.BASIS_APPLICATION_DATE.equals(override)
                || CountryAnnualFeeRule.BASIS_REGISTRATION_DATE.equals(override)) {
            return override;
        }
        return fallback;
    }

    /** FEE-06: 일괄 연차 오버라이드는 0(일괄 없음) 이상일 때만 적용한다. */
    private int lumpYearsOrDefault(String country, int fallback) {
        Integer override = systemSettingsService.getCountryFeeInitialLumpYearsOverride(country);
        return override != null && override >= 0 ? override : fallback;
    }

    /** FEE-06: 납부 주기 오버라이드는 양수일 때만 적용한다. */
    private int cycleMonthsOrDefault(String country, int fallback) {
        Integer override = systemSettingsService.getCountryFeeCycleMonthsOverride(country);
        return override != null && override > 0 ? override : fallback;
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
        CountryAnnualFeeRule rule = ruleFor(country);
        LocalDate base = annualFeeBaseDate(rule, applicationDate, registrationDate);
        if (base == null) {
            return LocalDate.of(baseDate.getYear(), 12, 31);
        }

        if (rule.hasMaintenanceWindows() && registrationDate != null) {
            return capAtExpiration(
                    nextMaintenanceDueDate(rule, registrationDate, expectedExpirationDate, baseDate),
                    expectedExpirationDate);
        }

        LocalDate dueDate = dateInYear(baseDate.getYear(), base);
        if (dueDate.isBefore(baseDate)) {
            dueDate = dateInYear(baseDate.getYear() + 1, base);
        }
        // FEE-06: 설정등록 시 일괄 납부한 연차(KR 1~3년차)에는 도래일이 생기지 않는다.
        // 최초 도래일은 기산일 + 일괄 연차 수(KR: 등록일 + 3년 = 4년차분 납부일)다.
        if (rule.initialLumpYears() > 0 && rule.registrationBased() && registrationDate != null) {
            LocalDate firstDueAfterLump = registrationDate.plusYears(rule.initialLumpYears());
            if (dueDate.isBefore(firstDueAfterLump)) {
                dueDate = firstDueAfterLump;
            }
        }
        return capAtExpiration(dueDate, expectedExpirationDate);
    }

    /**
     * FEE-05: 저장된 납부일이 과거(baseDate 이전)이면 국가 납부 주기 단위로 굴려 '오늘 이후 가장 가까운 도래일'을
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
        int months = ruleFor(country).cycleMonths();
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
        LocalDate nextDueDate = currentDueDate.plusMonths(ruleFor(country).cycleMonths());
        return capAtExpiration(nextDueDate, expectedExpirationDate);
    }


    public LocalDate annualFeeBaseDate(String country, LocalDate applicationDate, LocalDate registrationDate) {
        return annualFeeBaseDate(ruleFor(country), applicationDate, registrationDate);
    }

    private LocalDate annualFeeBaseDate(CountryAnnualFeeRule rule, LocalDate applicationDate, LocalDate registrationDate) {
        if (rule.registrationBased() && registrationDate != null) {
            return registrationDate;
        }
        // 기산일이 없는 데이터(미등록·마이그레이션 등)는 출원일 → 등록일 순으로 폴백한다.
        return applicationDate != null ? applicationDate : registrationDate;
    }

    public String annualFeeBasis(String country) {
        return ruleFor(country).basis();
    }

    public String paymentRuleLabel(String country) {
        return ruleFor(country).label();
    }

    /**
     * FEE-06: 도래일이 몇 년차 연차료인지 계산한다(기산일~도래일 경과 연수 + 1).
     * 예: KR 등록일 + 3년 도래일 = 4년차. 기산일 정보가 없으면 0을 반환한다.
     */
    public int annuityYearNumber(LocalDate basisDate, LocalDate dueDate) {
        if (basisDate == null || dueDate == null || dueDate.isBefore(basisDate)) {
            return 0;
        }
        return (int) ChronoUnit.YEARS.between(basisDate, dueDate) + 1;
    }

    private LocalDate nextMaintenanceDueDate(
            CountryAnnualFeeRule rule, LocalDate registrationDate, LocalDate expectedExpirationDate, LocalDate baseDate
    ) {
        List<Integer> windows = rule.maintenanceMonths();
        for (Integer months : windows) {
            LocalDate dueDate = registrationDate.plusMonths(months);
            if (!dueDate.isBefore(baseDate)) {
                return dueDate;
            }
        }
        return expectedExpirationDate != null
                ? expectedExpirationDate
                : registrationDate.plusMonths(windows.get(windows.size() - 1));
    }

    private String annualRuleLabel(String basis, int lumpYears) {
        String basisLabel = CountryAnnualFeeRule.BASIS_REGISTRATION_DATE.equals(basis) ? "등록일" : "출원일";
        if (lumpYears > 0) {
            return "설정등록 시 1~%d년차 일괄 납부, %d년차부터 %s 기준 매년 납부"
                    .formatted(lumpYears, lumpYears + 1, basisLabel);
        }
        return "%s 기준 매년 도래하는 연차료".formatted(basisLabel);
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
            return KR_DEFAULT_CYCLE_MONTHS;
        }
        return systemSettingsService.getCountryExtensionMonths(country);
    }

}
