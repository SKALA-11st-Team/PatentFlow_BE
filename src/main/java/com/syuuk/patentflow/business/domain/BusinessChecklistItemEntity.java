package com.syuuk.patentflow.business.domain;

import com.syuuk.patentflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사업부 평가 체크리스트 항목 정의.
 *
 * 기존에는 BusinessFixtureService에 하드코딩되어 리걸팀이 변경할 수 없었다.
 * DB로 이전해 관리자(리걸팀)가 항목/설명/점수 라벨을 운영 중 조정할 수 있다.
 * 제출 이력(business_checklist_scores_json)은 itemId+점수 스냅샷이므로
 * 이후 항목이 수정/삭제되어도 과거 제출 데이터는 영향받지 않는다.
 */
@Entity
@Table(name = "business_checklist_items")
public class BusinessChecklistItemEntity extends BaseEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String category;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // 점수 4~1점 각각의 선택지 설명 라벨.
    @Column(name = "score4_label", nullable = false, length = 500)
    private String score4Label;

    @Column(name = "score3_label", nullable = false, length = 500)
    private String score3Label;

    @Column(name = "score2_label", nullable = false, length = 500)
    private String score2Label;

    @Column(name = "score1_label", nullable = false, length = 500)
    private String score1Label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected BusinessChecklistItemEntity() {
    }

    public BusinessChecklistItemEntity(
            String id, String category, String title, String description,
            String score4Label, String score3Label, String score2Label, String score1Label,
            int sortOrder
    ) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.description = description;
        this.score4Label = score4Label;
        this.score3Label = score3Label;
        this.score2Label = score2Label;
        this.score1Label = score1Label;
        this.sortOrder = sortOrder;
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getScore4Label() {
        return score4Label;
    }

    public void setScore4Label(String score4Label) {
        this.score4Label = score4Label;
    }

    public String getScore3Label() {
        return score3Label;
    }

    public void setScore3Label(String score3Label) {
        this.score3Label = score3Label;
    }

    public String getScore2Label() {
        return score2Label;
    }

    public void setScore2Label(String score2Label) {
        this.score2Label = score2Label;
    }

    public String getScore1Label() {
        return score1Label;
    }

    public void setScore1Label(String score1Label) {
        this.score1Label = score1Label;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
