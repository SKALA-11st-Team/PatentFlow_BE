/**
 * @author 유건욱
 * @date 2026-05-19
 */
package com.syuuk.patentflow.user.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.mailing.domain.DepartmentEntity;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import com.syuuk.patentflow.mailing.repository.MailingHistoryRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.user.dto.CreateDepartmentRequest;
import com.syuuk.patentflow.common.response.PageInfo;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.user.dto.UpdateDepartmentRequest;
import com.syuuk.patentflow.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @relatedFR FR-LEGAL-12, FR-LEGAL-25
 * @relatedUI UI-LEGAL-07
 * @description 부서(수신자 매핑) 기준값 관리 서비스. 부서 조회·등록·수정·삭제와 삭제 시 참조 무결성 검증을 다룬다.
 */
@Service
public class AdminDepartmentService {

    private final DepartmentRepository mailingRecipientMappingRepository;
    private final PatentReviewService patentReviewService;
    private final UserRepository userRepository;
    private final PatentReviewHistoryRepository patentReviewHistoryRepository;
    private final MailingHistoryRepository mailingHistoryRepository;

    public AdminDepartmentService(
            DepartmentRepository mailingRecipientMappingRepository,
            PatentReviewService patentReviewService,
            UserRepository userRepository,
            PatentReviewHistoryRepository patentReviewHistoryRepository,
            MailingHistoryRepository mailingHistoryRepository) {
        this.mailingRecipientMappingRepository = mailingRecipientMappingRepository;
        this.patentReviewService = patentReviewService;
        this.userRepository = userRepository;
        this.patentReviewHistoryRepository = patentReviewHistoryRepository;
        this.mailingHistoryRepository = mailingHistoryRepository;
    }

    /**
     * @relatedFR FR-LEGAL-12, FR-LEGAL-25
     * @relatedUI UI-LEGAL-07
     * @description 전체 부서 목록을 부서 ID 순으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<DepartmentRecipientMappingResponse> getDepartments() {
        // 부서 목록 조회 — 수신자(email·name) 정보는 MailingService.getRecipientMappings에서 users 테이블과 합산
        return mailingRecipientMappingRepository.findAll(Sort.by("departmentId")).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * @relatedFR FR-LEGAL-12, FR-LEGAL-25
     * @relatedUI UI-LEGAL-07
     * @description 부서 ID·부서명 검색어로 부서를 검색·페이징 조회한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<DepartmentRecipientMappingResponse> getDepartments(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("departmentId"));
        Page<DepartmentEntity> departments = isBlank(search)
                ? mailingRecipientMappingRepository.findAll(pageable)
                : mailingRecipientMappingRepository.findByDepartmentIdContainingIgnoreCaseOrDepartmentNameContainingIgnoreCase(
                        search.trim(), search.trim(), pageable);
        return PageResponse.ok(departments.map(this::toResponse).getContent(),
                new PageInfo(departments.getNumber(), departments.getSize(), departments.getTotalElements(), departments.getTotalPages()));
    }

    /**
     * @relatedFR FR-LEGAL-12, FR-LEGAL-25
     * @relatedUI UI-LEGAL-07
     * @description 부서를 신규 등록하고 검토 서비스의 부서 캐시를 갱신한다(중복 ID 검증 포함).
     */
    @Transactional
    public DepartmentRecipientMappingResponse createDepartment(CreateDepartmentRequest request) {
        if (mailingRecipientMappingRepository.existsById(request.departmentId())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "이미 존재하는 사업부 ID입니다: " + request.departmentId());
        }
        DepartmentEntity entity = new DepartmentEntity(
                request.departmentId(),
                request.departmentName(),
                LocalDate.now());
        mailingRecipientMappingRepository.save(entity);
        patentReviewService.refreshDepartmentCache();
        // 응답 updatedAt은 저장된 엔티티의 시각을 그대로 사용해 LocalDate.now() 재호출로 인한 미세 불일치를 제거한다.
        return toResponse(entity);
    }

    /**
     * @relatedFR FR-LEGAL-12, FR-LEGAL-25
     * @relatedUI UI-LEGAL-07
     * @description 부서명을 수정하고 검토 서비스의 부서 캐시를 갱신한다.
     */
    @Transactional
    public DepartmentRecipientMappingResponse updateDepartment(String departmentId, UpdateDepartmentRequest request) {
        DepartmentEntity entity = mailingRecipientMappingRepository.findById(departmentId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사업부를 찾을 수 없습니다: " + departmentId));
        LocalDate updatedAt = LocalDate.now();
        entity.rename(request.departmentName(), updatedAt);
        mailingRecipientMappingRepository.save(entity);
        patentReviewService.refreshDepartmentCache();
        return toResponse(entity);
    }

    /**
     * @relatedFR FR-LEGAL-12, FR-LEGAL-25
     * @relatedUI UI-LEGAL-07
     * @description 부서를 삭제한다. 소속 계정·검토 이력·메일 발송 이력이 있으면 삭제를 거부하고, 삭제 후 부서 캐시를 갱신한다.
     */
    @Transactional
    public void deleteDepartment(String departmentId) {
        if (!mailingRecipientMappingRepository.existsById(departmentId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "사업부를 찾을 수 없습니다: " + departmentId);
        }
        if (userRepository.existsByDepartmentId(departmentId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "해당 사업부에 소속된 계정이 있어 삭제할 수 없습니다.");
        }
        if (patentReviewHistoryRepository.existsByDepartmentId(departmentId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "해당 사업부에 배정된 특허 검토 이력이 있어 삭제할 수 없습니다.");
        }
        if (mailingHistoryRepository.existsByDepartmentId(departmentId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "해당 사업부의 메일 발송 이력이 있어 삭제할 수 없습니다.");
        }
        mailingRecipientMappingRepository.deleteById(departmentId);
        patentReviewService.refreshDepartmentCache();
    }

    private DepartmentRecipientMappingResponse toResponse(DepartmentEntity entity) {
        return new DepartmentRecipientMappingResponse(
                entity.getDepartmentId(),
                entity.getDepartmentName(),
                "",
                "",
                List.of(),
                entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
