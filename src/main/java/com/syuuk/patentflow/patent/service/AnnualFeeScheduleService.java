package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.patent.dto.PatentFeeScheduleResponse.FeeScheduleEntry;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

    private static final int SCHEDULE_PAST_ENTRIES = 3;
    private static final int SCHEDULE_FUTURE_ENTRIES = 5;

    // F2: 국가별 연차료 요금표 기본값(개략치, 청구항 수 미반영) — fee.amounts.{CC} 설정으로 오버라이드.
    // KR: 연차 구간별 연 납부액(KRW), US: 등록 후 개월별 유지료(USD).
    private static final String KR_DEFAULT_FEE_AMOUNTS = "4-6:55000,7-9:130000,10-12:290000,13-25:600000";
    private static final String US_DEFAULT_FEE_AMOUNTS = "42:2000,90:3760,138:7700";
    private static final java.util.Map<String, String> FEE_CURRENCIES = java.util.Map.of(
            "KR", "KRW", "US", "USD", "JP", "JPY", "CN", "CNY", "TW", "TWD");

    /**
     * FEE-06: 특허 상세 연차료 일정 항목을 생성한다 — 일괄 납부 구간 1줄 + 직전 납부 최대 3건 +
     * 다음 도래(NEXT) 포함 향후 5건. effectiveNextDueDate가 계산값과 다르면(저장/조정값) NEXT 도래일을
     * 그 값으로 대체한다. 검토 시작일(reviewStartDate)은 도래일 - mailLeadMonths.
     */
    public List<FeeScheduleEntry> buildScheduleEntries(
            String country,
            LocalDate applicationDate,
            LocalDate registrationDate,
            LocalDate expectedExpirationDate,
            LocalDate effectiveNextDueDate,
            boolean nextDueAdjusted,
            int mailLeadMonths,
            LocalDate baseDate
    ) {
        CountryAnnualFeeRule rule = ruleFor(country);
        LocalDate basisDate = annualFeeBaseDate(rule, applicationDate, registrationDate);
        if (basisDate == null) {
            if (effectiveNextDueDate == null) {
                return List.of();
            }
            return List.of(new FeeScheduleEntry(
                    "다음 납부", 0, false, effectiveNextDueDate,
                    effectiveNextDueDate.minusMonths(mailLeadMonths), "NEXT", nextDueAdjusted,
                    null, feeCurrency(country)));
        }

        List<FeeScheduleEntry> entries = new ArrayList<>();
        List<LocalDate> dueDates = new ArrayList<>();
        boolean maintenance = rule.hasMaintenanceWindows() && registrationDate != null;
        boolean lumpRow = !maintenance && rule.initialLumpYears() > 0
                && rule.registrationBased() && registrationDate != null;

        if (maintenance) {
            for (Integer months : rule.maintenanceMonths()) {
                LocalDate due = registrationDate.plusMonths(months);
                if (expectedExpirationDate != null && due.isAfter(expectedExpirationDate)) {
                    break;
                }
                dueDates.add(due);
            }
        } else {
            LocalDate firstDue = lumpRow
                    ? registrationDate.plusYears(rule.initialLumpYears())
                    : basisDate.plusMonths(Math.max(rule.cycleMonths(), 1));
            int futureCount = 0;
            LocalDate due = firstDue;
            while (futureCount < SCHEDULE_FUTURE_ENTRIES) {
                if (expectedExpirationDate != null && due.isAfter(expectedExpirationDate)) {
                    break;
                }
                dueDates.add(due);
                if (!due.isBefore(baseDate)) {
                    futureCount++;
                }
                due = due.plusMonths(Math.max(rule.cycleMonths(), 1));
            }
            // 과거 도래분은 최근 3건만 남긴다(오래된 특허의 일정이 수십 줄로 늘어지는 것 방지).
            int pastCount = (int) dueDates.stream().filter(d -> d.isBefore(baseDate)).count();
            if (pastCount > SCHEDULE_PAST_ENTRIES) {
                dueDates = dueDates.subList(pastCount - SCHEDULE_PAST_ENTRIES, dueDates.size());
            }
        }

        String currency = feeCurrency(country);
        if (lumpRow) {
            entries.add(new FeeScheduleEntry(
                    "1~%d년차".formatted(rule.initialLumpYears()), 1, true,
                    registrationDate, null, "PAID_LUMP", false, null, currency));
        }

        boolean nextAssigned = false;
        for (LocalDate due : dueDates) {
            boolean past = due.isBefore(baseDate);
            boolean isNext = !past && !nextAssigned;
            LocalDate entryDue = due;
            boolean adjusted = false;
            if (isNext && effectiveNextDueDate != null && !effectiveNextDueDate.isBefore(baseDate)
                    && !effectiveNextDueDate.equals(due)) {
                entryDue = effectiveNextDueDate;
                adjusted = nextDueAdjusted;
            }
            if (isNext) {
                nextAssigned = true;
            }
            int yearNumber = annuityYearNumber(basisDate, entryDue);
            String yearLabel = maintenance
                    ? maintenanceLabel(registrationDate, due)
                    : (yearNumber > 0 ? "%d년차".formatted(yearNumber) : "납부");
            Long estimatedAmount = maintenance
                    ? estimateFeeAmount(country, true, ChronoUnit.MONTHS.between(registrationDate, due))
                    : estimateFeeAmount(country, false, yearNumber);
            entries.add(new FeeScheduleEntry(
                    yearLabel,
                    yearNumber,
                    false,
                    entryDue,
                    entryDue.minusMonths(mailLeadMonths),
                    past ? "PAST" : (isNext ? "NEXT" : "FUTURE"),
                    adjusted,
                    estimatedAmount,
                    currency));
        }
        return entries;
    }

    /** F2: 통화 코드 — 요금표가 없어도 통화는 안내한다(미지원 국가는 null). */
    public String feeCurrency(String country) {
        return country == null ? null : FEE_CURRENCIES.get(country.trim().toUpperCase());
    }

    /**
     * F2: 납부 예상액(개략치). 연차 기반 국가는 yearNumber로 구간 요금표를, US 유지료는
     * 등록일~도래일 개월수로 요금표를 조회한다. 요금표가 없으면 null.
     */
    public Long estimateFeeAmount(String country, boolean maintenance, long yearNumberOrMonths) {
        String normalized = country == null ? "" : country.trim().toUpperCase();
        String table = systemSettingsService.getCountryFeeAmountTableOverride(normalized);
        if (table == null) {
            table = switch (normalized) {
                case "KR" -> KR_DEFAULT_FEE_AMOUNTS;
                case "US" -> US_DEFAULT_FEE_AMOUNTS;
                default -> null;
            };
        }
        if (table == null) {
            return null;
        }
        for (String token : table.split(",")) {
            String[] parts = token.trim().split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                long amount = Long.parseLong(parts[1].trim());
                String range = parts[0].trim();
                if (range.contains("-")) {
                    String[] bounds = range.split("-");
                    long from = Long.parseLong(bounds[0].trim());
                    long to = Long.parseLong(bounds[1].trim());
                    if (!maintenance && yearNumberOrMonths >= from && yearNumberOrMonths <= to) {
                        return amount;
                    }
                } else if (Long.parseLong(range) == yearNumberOrMonths) {
                    return amount;
                }
            } catch (NumberFormatException ignored) {
                // 잘못된 요금표 토큰은 건너뛴다(설정 실수에 관대).
            }
        }
        return null;
    }

    private String maintenanceLabel(LocalDate registrationDate, LocalDate due) {
        long months = ChronoUnit.MONTHS.between(registrationDate, due);
        if (months % 12 == 0) {
            return "%d년 유지료".formatted(months / 12);
        }
        return "%.1f년 유지료".formatted(months / 12.0);
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
