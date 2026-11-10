package com.console.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // CSRF desativado para evitar o Erro 403 nas operações locais
                .authorizeHttpRequests(auth -> auth
                        // 1. TODAS as rotas públicas juntas PRIMEIRO
                        .requestMatchers("/login", "/css/**", "/js/**", "/img/**").permitAll()

                        // 2. BLOQUEIO DE ESCRITA NO BACKEND: Leitor NÃO faz requisições POST (Envio de Arquivo, E-mail, Alteração SSH)
                        .requestMatchers(HttpMethod.POST).hasAnyRole("ADMINISTRADOR", "OPERADOR")

                        // 3. A regra geral "para o resto" SEMPRE NO FIM
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Criação dos 3 perfis exigidos pelo professor (senhas simples para o laboratório)
        UserDetails admin = User.withDefaultPasswordEncoder()
                .username("admin")
                .password("123")
                .roles("ADMINISTRADOR")
                .build();

        UserDetails operador = User.withDefaultPasswordEncoder()
                .username("operador")
                .password("123")
                .roles("OPERADOR")
                .build();

        UserDetails leitor = User.withDefaultPasswordEncoder()
                .username("leitor")
                .password("123")
                .roles("LEITOR")
                .build();

        return new InMemoryUserDetailsManager(admin, operador, leitor);
    }
}