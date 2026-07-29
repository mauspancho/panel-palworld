package com.palworldadmin.app.config;

import com.palworldadmin.app.repository.UserRepository;
import com.palworldadmin.app.service.UserAccountService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({AdminProperties.class, PalworldDefaultsProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserAccountService accounts) throws Exception {
        AuthenticationEntryPoint apiEntryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/assets/**", "/css/**", "/webjars/**", "/favicon.ico", "/api/auth/csrf").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(apiEntryPoint, new AntPathRequestMatcher("/api/**")))
                .formLogin(login -> login
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            accounts.loginSucceeded(authentication.getName());
                            if (wantsJson(request.getHeader("Accept"))) {
                                response.setStatus(HttpStatus.NO_CONTENT.value());
                            } else {
                                response.sendRedirect("/");
                            }
                        })
                        .failureHandler((request, response, exception) -> {
                            accounts.loginFailed(request.getParameter("username"));
                            if (wantsJson(request.getHeader("Accept"))) {
                                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                                response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                                response.getWriter().write("El nombre de usuario o la contrasena no son correctos.");
                            } else {
                                response.sendRedirect("/login?error");
                            }
                        })
                        .permitAll())
                .logout(logout -> logout.logoutSuccessHandler((request, response, authentication) -> {
                    if (authentication != null) {
                        accounts.logout(authentication.getName());
                    }
                    if (wantsJson(request.getHeader("Accept"))) {
                        response.setStatus(HttpStatus.NO_CONTENT.value());
                    } else {
                        response.sendRedirect("/login?logout");
                    }
                }).permitAll())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByNormalizedUsername(username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT))
                .or(() -> users.findByUsername(username))
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .disabled(!user.isEnabled())
                        .accountLocked(user.isLocked())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AdminProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsAllowedOriginPatterns(properties));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> corsAllowedOriginPatterns(AdminProperties properties) {
        List<String> patterns = new ArrayList<>();
        patterns.addAll(splitCommaSeparated(properties.getCorsAllowedOrigins()));
        patterns.addAll(splitCommaSeparated(properties.getCorsAllowedOriginPatterns()));
        return patterns.stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .distinct()
                .toList();
    }

    private List<String> splitCommaSeparated(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static boolean wantsJson(String accept) {
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }
}
