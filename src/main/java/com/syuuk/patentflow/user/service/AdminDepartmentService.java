package com.syuuk.patentflow.user.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.mailing.domain.DepartmentEntity;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.user.dto.CreateDepartmentRequest;
import com.syuuk.patentflow.user.dto.UpdateDepartmentRequest;
import com.syuuk.patentflow.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDepartmentService {

    private final DepartmentRepository mailingRecipientMappingRepository;
    private final PatentReviewService patentReviewService;
    private final UserRepository userRepository;

    public AdminDepartmentService(
            DepartmentRepository mailingRecipientMappingRepository,
            PatentReviewService patentReviewService,
            UserRepository userRepository) {
        this.mailingRecipientMappingRepository = mailingRecipientMappingRepository;
        this.patentReviewService = patentReviewService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentRecipientMappingResponse> getDepartments() {
        return mailingRecipientMappingRepository.findAll(Sort.by("departmentId")).stream()
                .map(e -> new DepartmentRecipientMappingResponse(
                        e.getDepartmentId(),
                        e.getDepartmentName(),
                        "",
                        "",
                        List.of(),
                        e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : ""))
                .toList();
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
        return new DepartmentRecipientMappingResponse(
                entity.getDepartmentId(),
                entity.getDepartmentName(),
                entity.getManagerEmail() != null ? entity.getManagerEmail() : "",
                entity.getManagerName() != null ? entity.getManagerName() : "",
                List.of(),
                updatedAt.toString());
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
        mailingRecipientMappingRepository.deleteById(departmentId);
        patentReviewService.refreshDepartmentCache();
    }
}
