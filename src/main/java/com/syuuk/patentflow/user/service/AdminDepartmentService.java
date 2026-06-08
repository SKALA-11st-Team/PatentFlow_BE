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
import com.syuuk.patentflow.user.dto.PageResponse;
import com.syuuk.patentflow.user.dto.UpdateDepartmentRequest;
import com.syuuk.patentflow.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<DepartmentRecipientMappingResponse> getDepartments() {
        // 부서 목록 조회 — 수신자(email·name) 정보는 MailingService.getRecipientMappings에서 users 테이블과 합산
        return mailingRecipientMappingRepository.findAll(Sort.by("departmentId")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentRecipientMappingResponse> getDepartments(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("departmentId"));
        Page<DepartmentEntity> departments = isBlank(search)
                ? mailingRecipientMappingRepository.findAll(pageable)
                : mailingRecipientMappingRepository.findByDepartmentIdContainingIgnoreCaseOrDepartmentNameContainingIgnoreCase(
                        search.trim(), search.trim(), pageable);
        return PageResponse.from(departments.map(this::toResponse));
    }

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
        return new DepartmentRecipientMappingResponse(
                request.departmentId(),
                request.departmentName(),
                "",
                "",
                List.of(),
                LocalDate.now().toString());
    }

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
