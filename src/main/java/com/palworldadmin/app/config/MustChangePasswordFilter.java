package com.palworldadmin.app.config;

import com.palworldadmin.app.service.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {
    private final UserAccountService accounts;

    public MustChangePasswordFilter(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (path.startsWith("/api/")
                && authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && mustChange(authentication.getName())
                && !isAllowedWhileChangingPassword(path)) {
            response.setStatus(423);
            response.setContentType("text/plain");
            response.getWriter().write("Debes cambiar tu contrasena antes de continuar.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean mustChange(String username) {
        try {
            return accounts.authenticated(username).isMustChangePassword();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isAllowedWhileChangingPassword(String path) {
        return path.equals("/api/auth/me")
                || path.equals("/api/auth/csrf")
                || path.equals("/api/profile")
                || path.equals("/api/profile/password");
    }
}
