package com.syuuk.patentflow.mailing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.syuuk.patentflow.mailing.domain.MailingHistoryEntity;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/**
 * 회귀: patentsJson을 무경계 LIKE '%id%'로 검색하던 오매칭(접두 충돌·교차필드)을 토큰 경계 매칭으로 막았는지
 * 실제 DB(H2) LIKE로 검증한다. 기존 MailingServiceTest는 repository를 모킹해 실제 매칭을 검증하지 못했다.
 */
@DataJpaTest
class MailingHistoryRepositoryTest {

    @Autowired
    private MailingHistoryRepository repository;

    private MailingHistoryEntity history(String mailingId, String recipient, String patentId, String managementNumber) {
        String patentsJson = "[{\"patentId\":\"" + patentId + "\",\"managementNumber\":\"" + managementNumber
                + "\",\"originalPatentUrl\":\"https://patents.google.com/patent/" + patentId + "/ko\",\"title\":\"t\"}]";
        return new MailingHistoryEntity(mailingId, "body", "[]", 1, patentsJson, recipient, "name", "DEPT-1",
                OffsetDateTime.now(), "admin@x.test", "SENT", "subject");
    }

    @BeforeEach
    void seed() {
        repository.save(history("M-A", "a@x.com", "PAT-1", "M-1"));
        repository.save(history("M-B", "b@x.com", "PAT-10", "M-2"));   // 접두 충돌(PAT-1 ⊂ PAT-10)
        repository.save(history("M-C", "c@x.com", "PAT-2", "M-PAT-1")); // 교차필드(managementNumber에 PAT-1 포함)
    }

    @Test
    void exactPatentIdMatchesOnlyTokenBoundary() {
        var result = repository.findByPatentIdToken("%\"patentId\":\"PAT-1\"%", PageRequest.of(0, 10));

        // 접두 충돌(M-B)·교차필드(M-C) 없이 정확 매칭 1건만.
        assertThat(result.getContent()).extracting(MailingHistoryEntity::getMailingId).containsExactly("M-A");
    }

    @Test
    void longerIdAndCrossFieldAreNotMatchedByShorterPrefix() {
        // PAT-10 검색은 PAT-1(M-A)을 잡지 않고 정확히 M-B만.
        var result = repository.findByPatentIdToken("%\"patentId\":\"PAT-10\"%", PageRequest.of(0, 10));
        assertThat(result.getContent()).extracting(MailingHistoryEntity::getMailingId).containsExactly("M-B");
    }

    @Test
    void recipientCombinedQueryAlsoEnforcesTokenBoundary() {
        var hit = repository.findByRecipientEmailAndPatentIdToken("a@x.com", "%\"patentId\":\"PAT-1\"%", PageRequest.of(0, 10));
        assertThat(hit.getContent()).extracting(MailingHistoryEntity::getMailingId).containsExactly("M-A");

        // 교차필드 레코드(M-C, c@x.com)는 PAT-1 검색에 잡히지 않는다.
        var miss = repository.findByRecipientEmailAndPatentIdToken("c@x.com", "%\"patentId\":\"PAT-1\"%", PageRequest.of(0, 10));
        assertThat(miss.getContent()).isEmpty();
    }
}
