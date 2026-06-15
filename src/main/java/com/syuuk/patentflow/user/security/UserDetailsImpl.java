package com.syuuk.patentflow.user.security;

import com.syuuk.patentflow.user.domain.UserEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserDetailsImpl implements UserDetails {

    private final UserEntity user;

    public UserDetailsImpl(UserEntity user) {
        this.user = user;
    }

    public UserEntity getUser() {
        return user;
    }

    // Spring Security의 username = 로그인 ID → email 반환
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    // ACTIVE만 로그인 허용 — 초대 재발송(PENDING)·회수(INACTIVE) 계정은 비밀번호 로그인이 차단된다.
    @Override public boolean isEnabled() { return "ACTIVE".equals(user.getStatus()); }
}
