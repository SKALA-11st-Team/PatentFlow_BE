/**
 * @author 유건욱
 * @date 2026-05-19
 */
package com.syuuk.patentflow.user.security;

import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * @relatedFR FR-COM-01
 * @relatedUI UI-COM-01
 * @description 로그인 ID(email)로 사용자를 조회해 Spring Security 인증 principal을 제공한다.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description email(= Spring Security username = 로그인 ID)로 사용자를 조회해 UserDetails로 반환한다.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));
        return new UserDetailsImpl(user);
    }
}
