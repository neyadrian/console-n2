package com.console.app.config;

import com.console.app.models.Usuario;
import com.console.app.repositories.UsuarioRepository;
import com.console.app.services.AuditoriaService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UsuarioRepository usuarioRepository) {
        return username -> {
            Usuario u = usuarioRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + username));
            return User.builder()
                    .username(u.getUsername())
                    .password(u.getPasswordHash())
                    .roles(u.getPerfil()) // ADMINISTRADOR, OPERADOR ou LEITOR
                    .disabled(!u.isAtivo())
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuditoriaService auditoriaService) throws Exception {
        http
                // Mantemos CSRF ignorado apenas nas chamadas internas de API/polling se necessário, ou ativo nos forms
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/img/**").permitAll()

                        // 1. Apenas ADMINISTRADOR gerencia usuários e altera configurações críticas (Seção 7)
                        .requestMatchers("/usuarios/**", "/config/**", "/servicos/configurar/**").hasRole("ADMINISTRADOR")

                        // 2. LEITOR não pode consultar auditoria (Seção 7: Admin=Global, Operador=Próprias, Leitor=Não)
                        .requestMatchers("/auditoria/**").hasAnyRole("ADMINISTRADOR", "OPERADOR")

                        // 3. Bloqueio de operações de escrita para o perfil LEITOR
                        .requestMatchers(
                                "/arquivos/upload/**",
                                "/email/enviar/**",
                                "/ssh/alterar/**",
                                "/ssh/restaurar/**",
                                "/servicos/operar/**",
                                "/dns/operar/**",
                                "/dhcp/operar/**",
                                "/vdi/solicitar/**",
                                "/vdi/operar/**",
                                "/pki/operar/**"
                        ).hasAnyRole("ADMINISTRADOR", "OPERADOR")

                        // 4. Demais telas de visualização (Dashboard, Logs, Listagens) liberadas para todos os autenticados
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            auditoriaService.registrar("SESSAO", "LOGIN", "SUCESSO", "Login efetuado no console");
                            response.sendRedirect("/dashboard");
                        })
                        .failureHandler((request, response, exception) -> {
                            String userTentado = request.getParameter("username");
                            auditoriaService.registrar("SESSAO", "LOGIN", "RECUSADO", "Falha de autenticação para: " + userTentado);
                            response.sendRedirect("/login?error");
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                auditoriaService.registrar("SESSAO", "LOGOUT", "SUCESSO", "Sessão encerrada");
                            }
                            response.sendRedirect("/login?logout");
                        })
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            auditoriaService.registrar("ROTA:" + request.getRequestURI(), "ACESSO_NEGADO", "RECUSADO", "Tentativa de acesso sem permissão");
                            response.sendRedirect("/dashboard?erro=AcessoNegado");
                        })
                );

        return http.build();
    }

    // Popula o banco SQLite com os mesmos logins que você já usa (senha "123" cifrada em BCrypt)
    @Bean
    public CommandLineRunner initUsers(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (usuarioRepository.count() == 0) {
                Usuario admin = new Usuario();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("123"));
                admin.setPerfil("ADMINISTRADOR");
                usuarioRepository.save(admin);

                Usuario operador = new Usuario();
                operador.setUsername("operador");
                operador.setPasswordHash(passwordEncoder.encode("123"));
                operador.setPerfil("OPERADOR");
                usuarioRepository.save(operador);

                Usuario leitor = new Usuario();
                leitor.setUsername("leitor");
                leitor.setPasswordHash(passwordEncoder.encode("123"));
                leitor.setPerfil("LEITOR");
                usuarioRepository.save(leitor);
            }
        };
    }
}