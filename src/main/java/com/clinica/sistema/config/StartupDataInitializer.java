package com.clinica.sistema.config;

import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StartupDataInitializer implements CommandLineRunner {
    private final SalaRepository salaRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    @Value("${app.seed-admin-login:}")
    private String adminLogin;

    @Value("${app.seed-admin-password:}")
    private String adminPassword;

    @Value("${app.seed-admin-name:Administracao}")
    private String adminName;

    public StartupDataInitializer(SalaRepository salaRepository, UsuarioRepository usuarioRepository) {
        this.salaRepository = salaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void run(String... args) {
        garantirSalas();
        garantirAdmin();

        if (seedDemoData) {
            garantirProfissionaisDemo();
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

        Usuario admin = usuarioRepository.findByLogin(adminLogin)
                .orElseGet(Usuario::new);

        admin.setNome(adminName);
        admin.setLogin(adminLogin);
        admin.setSenha(adminPassword);
        admin.setCargo("ROLE_ADMIN");
        usuarioRepository.save(admin);
    }

    private void garantirProfissionaisDemo() {
        criarProfissionalSeNaoExistir("Profissional 1", "prof1", "123456");
        criarProfissionalSeNaoExistir("Profissional 2", "prof2", "123456");
        criarProfissionalSeNaoExistir("Profissional 3", "prof3", "123456");
    }

    private void criarProfissionalSeNaoExistir(String nome, String login, String senha) {
        Usuario profissional = usuarioRepository.findByLogin(login)
                .orElseGet(Usuario::new);
        profissional.setNome(nome);
        profissional.setLogin(login);
        profissional.setSenha(senha);
        profissional.setCargo("ROLE_PROFISSIONAL");
        usuarioRepository.save(profissional);
    }
}
