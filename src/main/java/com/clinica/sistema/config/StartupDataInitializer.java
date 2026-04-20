package com.clinica.sistema.config;

import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class StartupDataInitializer implements CommandLineRunner {
    private static final String SENHA_PROFISSIONAIS_PADRAO = "297b";

    private final SalaRepository salaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    @Value("${app.seed-admin-login:}")
    private String adminLogin;

    @Value("${app.seed-admin-password:}")
    private String adminPassword;

    @Value("${app.seed-admin-name:Administracao}")
    private String adminName;

    public StartupDataInitializer(
            SalaRepository salaRepository,
            UsuarioRepository usuarioRepository,
            AgendamentoRepository agendamentoRepository
    ) {
        this.salaRepository = salaRepository;
        this.usuarioRepository = usuarioRepository;
        this.agendamentoRepository = agendamentoRepository;
    }

    @Override
    public void run(String... args) {
        garantirSalas();

        if (seedDemoData) {
            sincronizarUsuariosPadrao();
        } else {
            garantirAdmin();
        }
    }

    private void garantirSalas() {
        List<String> salas = List.of("Sala 1", "Sala 2", "Sala 3", "Sala 4");
        for (String nomeSala : salas) {
            boolean existe = salaRepository.findAllByOrderByNomeAsc().stream()
                    .anyMatch(sala -> sala.getNome().equalsIgnoreCase(nomeSala));
            if (!existe) {
                Sala sala = new Sala();
                sala.setNome(nomeSala);
                salaRepository.save(sala);
            }
        }
    }

    private void garantirAdmin() {
        if (adminLogin == null || adminLogin.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }

        salvarOuAtualizarUsuario(new UsuarioPadrao(
                adminName,
                adminLogin,
                adminPassword,
                "ROLE_ADMIN"
        ));
    }

    private void sincronizarUsuariosPadrao() {
        List<UsuarioPadrao> usuariosPadrao = List.of(
                new UsuarioPadrao("Administrador do Sistema", "admin", "Luquinha12@", "ROLE_ADMIN"),
                new UsuarioPadrao("Polyana", "polyana", SENHA_PROFISSIONAIS_PADRAO, "ROLE_ADMIN"),
                new UsuarioPadrao("Carol", "carol", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Itamara", "itamara", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Julia", "julia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Juliano", "juliano", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Jessica Mota", "jessicamota", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Jessica Houri", "jessicahouri", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Rosi", "rosi", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Maria Paula", "mariapaula", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Breno", "breno", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Tathiane", "tathiane", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Luiza", "luiza", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Andreia", "andreia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Cibele", "cibele", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Bruna", "bruna", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Claudia", "claudia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL")
        );

        removerUsuariosObsoletos(usuariosPadrao);
        usuariosPadrao.forEach(this::salvarOuAtualizarUsuario);
    }

    private void removerUsuariosObsoletos(List<UsuarioPadrao> usuariosPadrao) {
        Set<String> loginsPermitidos = usuariosPadrao.stream()
                .map(UsuarioPadrao::login)
                .collect(java.util.stream.Collectors.toSet());

        List<Usuario> usuariosObsoletos = usuarioRepository.findAll().stream()
                .filter(usuario -> !loginsPermitidos.contains(usuario.getLogin()))
                .toList();

        if (usuariosObsoletos.isEmpty()) {
            return;
        }

        List<Long> idsObsoletos = usuariosObsoletos.stream()
                .map(Usuario::getId)
                .toList();
        agendamentoRepository.deleteByProfissionalIdIn(idsObsoletos);
        usuarioRepository.deleteAllById(idsObsoletos);
    }

    private void salvarOuAtualizarUsuario(UsuarioPadrao usuarioPadrao) {
        Usuario profissional = usuarioRepository.findByLogin(usuarioPadrao.login())
                .orElseGet(Usuario::new);
        profissional.setNome(usuarioPadrao.nome());
        profissional.setLogin(usuarioPadrao.login());
        profissional.setSenha(usuarioPadrao.senha());
        profissional.setCargo(usuarioPadrao.cargo());
        usuarioRepository.save(profissional);
    }

    private record UsuarioPadrao(String nome, String login, String senha, String cargo) {
    }
}
