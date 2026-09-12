package com.ibizabroker.bibliotheque.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration de sécurité (Spring Security 6 / Boot 3).
 *
 * RS-01 : /api/reservations/** n'est plus en permitAll() — tout endpoint de
 * réservation exige un token JWT valide (401 sinon, via jwtAuthenticationEntryPoint).
 * RS-02 : la logique fine de rôle (ADHERENT vs BIBLIOTHECAIRE) est portée par
 * @PreAuthorize dans ReservationController (méthode security activée ci-dessous).
 * La distinction 401/403 est assurée par deux handlers dédiés en JSON.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfiguration {

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private JsonAccessDeniedHandler jsonAccessDeniedHandler;

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Autowired
    private UserDetailsService jwtService;

    @Bean
    public AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.cors();
        httpSecurity.csrf().disable()
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/authenticate", "/borrow/**").permitAll()
                        // Swagger UI accessible pour tester l'API en soutenance
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/admin/books").permitAll()
                        .requestMatchers(HttpMethod.GET, "/admin/books/*").permitAll()
                        .requestMatchers(HttpHeaders.ALLOW).permitAll()
                        // RS-01 : plus aucun permitAll sur /api/reservations/**
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        // 401 : je ne sais pas qui vous êtes (token absent/invalide/expiré)
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        // 403 : je sais qui vous êtes, mais vous n'avez pas le droit
                        .accessDeniedHandler(jsonAccessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        httpSecurity.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
