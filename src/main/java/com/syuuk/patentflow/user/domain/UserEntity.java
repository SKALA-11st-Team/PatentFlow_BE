package com.syuuk.patentflow.user.domain;

import com.syuuk.patentflow.department.domain.DepartmentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"email"}))
public class UserEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @Column(length = 64)
    private String id;

    // 로그인 ID — 반드시 이메일 형식, UNIQUE
    @Column(nullable = false, length = 256)
    private String email;

    @Column(nullable = false, length = 256)
    private String password;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(name = "department_id", length = 64)
    private String departmentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", insertable = false, updatable = false)
    private DepartmentEntity department;

    // 실제 사람 이름 (예: 이소율)
    @Column(length = 128)
    private String username;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected UserEntity() {}

    public UserEntity(String id, String email, String password, String role,
            String departmentId, String username) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.role = role;
        this.departmentId = departmentId;
        this.username = username;
        this.createdAt = OffsetDateTime.now(KST);
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return department != null ? department.getDepartmentName() : null; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
