package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import com.syuuk.patentflow.patent.dto.PatentUpsertRequest;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 검토 상태·라이프사이클 라벨 정합성 회귀 테스트.
 * - 기본정보 수정(updatePatent)이 inReview/patentStatus/currentQuarterKey를 리셋하지 않는다(be-patent-core-1).
 * - 존재하지 않는 사업부 배정은 거부된다(be-patent-core-7).
 */
@SpringBootTest(properties = {
        "patentflow.lookup.google-patents.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PatentReviewServiceUpdateTest {

    @Autowired
    private PatentReviewService patentReviewService;

    @Autowired
    private PatentMetadataRepository patentMetadataRepository;

    @MockitoBean
    private AiReportAgentClient aiReportAgentClient;

    /** 미래 납부 기준일 + 검토 대상(inReview/분기키) 상태로 세팅한 특허를 반환한다. */
    PatentMetadataEntity prepareInReviewPatent(String patentId, String quarterKey) {
        PatentMetadataEntity entity = patentMetadataRepository.findById(patentId).orElseThrow();
        entity.setPatentStatus(PatentLifecycleStatus.ACTIVE);
        entity.setFeeDueDate(LocalDate.now().plusYears(1));
        entity.setInReview(true);
        entity.setCurrentQuarterKey(quarterKey);
        return patentMetadataRepository.save(entity);
    }

    @Test
    void updatePatentPreservesReviewWorkflowState() {
        String patentId = "PAT-2026-0005";
        String quarterKey = "2026-Q3";
        PatentMetadataEntity before = prepareInReviewPatent(patentId, quarterKey);
        String originalManagementNumber = before.getManagementNumber();

        PatentUpsertRequest request = new PatentUpsertRequest(
                originalManagementNumber,
                "수정된 제목",
                before.getApplicationDate(),
                "없음",
                before.getCountry(),
                before.getRegistrationDate(),
                before.getApplicationNumber(),
                before.getRegistrationNumber(),
                before.getExpectedExpirationDate(),
                "MANUAL",
                before.getBusinessArea(),
                before.getTechnologyArea(),
                before.getProductName());

        patentReviewService.updatePatent(patentId, request);

        PatentMetadataEntity after = patentMetadataRepository.findById(patentId).orElseThrow();
        // 기본정보는 갱신된다.
        assertThat(after.getTitle()).isEqualTo("수정된 제목");
        // 워크플로우 상태는 리셋되지 않는다.
        assertThat(after.isInReview()).isTrue();
        assertThat(after.getCurrentQuarterKey()).isEqualTo(quarterKey);
        assertThat(after.getPatentStatus()).isEqualTo(PatentLifecycleStatus.ACTIVE);
    }

    @Test
    void updatePatentDoesNotReviveAbandonedPatent() {
        String patentId = "PAT-2026-0005";
        PatentMetadataEntity before = patentMetadataRepository.findById(patentId).orElseThrow();
        // 사람이 포기 결정한 특허로 세팅.
        beginAbandoned(patentId);
        String originalManagementNumber = before.getManagementNumber();

        PatentUpsertRequest request = new PatentUpsertRequest(
                originalManagementNumber,
                "기본 정보만 수정",
                before.getApplicationDate(),
                "없음",
                before.getCountry(),
                before.getRegistrationDate(),
                before.getApplicationNumber(),
                before.getRegistrationNumber(),
                before.getExpectedExpirationDate(),
                "MANUAL",
                before.getBusinessArea(),
                before.getTechnologyArea(),
                before.getProductName());

        patentReviewService.updatePatent(patentId, request);

        PatentMetadataEntity after = patentMetadataRepository.findById(patentId).orElseThrow();
        assertThat(after.getTitle()).isEqualTo("기본 정보만 수정");
        // 기본 정보 수정만으로 포기 특허가 보유 중(ACTIVE)으로 되살아나지 않는다.
        assertThat(after.getPatentStatus()).isEqualTo(PatentLifecycleStatus.ABANDONED);
    }

    void beginAbandoned(String patentId) {
        PatentMetadataEntity entity = patentMetadataRepository.findById(patentId).orElseThrow();
        // 미래 납부 기준일 — 자동 소멸(EXPIRED) 보정을 피하고 사람이 기록한 포기만 검증한다.
        entity.setFeeDueDate(LocalDate.now().plusYears(1));
        entity.setPatentStatus(PatentLifecycleStatus.ABANDONED);
        entity.setInReview(false);
        entity.setCurrentQuarterKey(null);
        patentMetadataRepository.save(entity);
    }

    @Test
    void assignDepartmentRejectsUnknownDepartment() {
        assertThatThrownBy(() -> patentReviewService.assignDepartment("PAT-2026-0001", "DEPT-DOES-NOT-EXIST"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(ex -> ((PatentFlowException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void assignDepartmentAcceptsExistingDepartment() {
        PatentDetailResponse updated = patentReviewService.assignDepartment("PAT-2026-0001", "DEPT-ICT");
        assertThat(updated.departmentId()).isEqualTo("DEPT-ICT");
        assertThat(updated.departmentName()).isEqualTo("ICT사업부");
    }
}
