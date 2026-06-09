package com.syuuk.patentflow.mailing.repository;

import com.syuuk.patentflow.mailing.domain.MailingHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailingHistoryRepository extends JpaRepository<MailingHistoryEntity, String> {

    boolean existsByDepartmentId(String departmentId);

    Page<MailingHistoryEntity> findByRecipientEmailIgnoreCase(String recipientEmail, Pageable pageable);

    // 회귀: patentsJson(특허 객체 배열)을 무경계 LIKE '%id%'로 검색하면 짧은 ID가 더 긴 ID나 다른 필드
    // (managementNumber/URL) 안의 부분문자열로 오매칭된다. "patentId":"<id>" 토큰 경계(닫는 따옴표 포함)로
    // 한정해 정확 멤버십만 매칭한다. :pattern은 서비스가 와일드카드를 escape '!'로 막아 구성한다.
    @Query("select m from MailingHistoryEntity m where m.patentsJson like :pattern escape '!'")
    Page<MailingHistoryEntity> findByPatentIdToken(@Param("pattern") String pattern, Pageable pageable);

    @Query("select m from MailingHistoryEntity m where lower(m.recipientEmail) = lower(:recipientEmail) "
            + "and m.patentsJson like :pattern escape '!'")
    Page<MailingHistoryEntity> findByRecipientEmailAndPatentIdToken(
            @Param("recipientEmail") String recipientEmail,
            @Param("pattern") String pattern,
            Pageable pageable);
}
