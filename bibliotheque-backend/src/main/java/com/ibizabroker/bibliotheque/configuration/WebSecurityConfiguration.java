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
 * API stateless protégée par JWT :
 *  - /authenticate et /borrow/** restent publics (comportement historique) ;
 *  - la lecture des livres reste publique (le catalogue est consultable
 *    sans compte, comme sur le main d'origine) ;
 *  - tout le reste exige un token JWT valide -> 401 JSON sinon,
 *    délégué à JwtAuthenticationEntryPoint.
 *
 * Les handlers d'exception (401 / 403) sont des composants dédiés afin de
 * garantir la distinction « identité inconnue » vs « droits insuffisants ».
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
                        .requestMatchers(HttpMethod.GET, "/admin/books").permitAll()
                        .requestMatchers(HttpMethod.GET, "/admin/books/*").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Séance 4 : /api/reservations/** n'est plus en permitAll —
                        // il tombe sous anyRequest().authenticated() (RS-01). La logique
                        // fine de rôle est portée par @PreAuthorize et les vérifications
                        // de propriété du service (RS-02 à RS-05).
                        .requestMatchers(HttpHeaders.ALLOW).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        // 401 : token absent/invalide/expiré — « je ne sais pas qui vous êtes »
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        // 403 : identité connue mais droits insuffisants
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
