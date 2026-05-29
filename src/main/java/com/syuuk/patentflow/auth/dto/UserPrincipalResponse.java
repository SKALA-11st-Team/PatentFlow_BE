package com.syuuk.patentflow.auth.dto;

import java.util.List;

public record UserPrincipalResponse(
        String username,
        String displayName,
        List<String> roles,
        String userId,
        String name,
        String email,
        String role,
        String departmentId,
        String departmentName
) {

    public UserPrincipalResponse(String username, String displayName, List<String> roles) {
        this(
                username,
                displayName,
                roles,
                username,
                displayName,
                email(username),
                role(roles),
                departmentId(roles),
                departmentName(roles));
    }

    private static String email(String username) {
        if (username.contains("@")) {
            return username;
        }
        return switch (username) {
            case "admin" -> "admin@syuuk.test";
            case "business" -> "business@syuuk.test";
            default -> username + "@syuuk.test";
        };
    }

    private static String role(List<String> roles) {
        return roles.stream().anyMatch(role -> role.equals("ROLE_ADMIN")) ? "ADMIN" : "BUSINESS";
    }

    private static String departmentId(List<String> roles) {
        return role(roles).equals("BUSINESS") ? "DEPT-RND" : null;
    }

    private static String departmentName(List<String> roles) {
        return role(roles).equals("BUSINESS") ? "R&D본부" : null;
    }
}
