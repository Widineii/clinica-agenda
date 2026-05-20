package com.clinica.sistema.service;

import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            AuthService authService,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario cadastrarProfissional(CadastroProfissionalForm form, Usuario usuarioLogado) {
        if (!authService.isAdmin(usuarioLogado)) {
            throw new RuntimeException("Somente a administracao pode cadastrar profissionais.");
        }

        String nome = form.getNome() != null ? form.getNome().trim() : "";
        String login = form.getLogin() != null ? form.getLogin().trim().toLowerCase() : "";
        String senha = form.getSenha() != null ? form.getSenha().trim() : "";

        if (nome.isBlank()) {
            throw new RuntimeException("Informe o nome do profissional.");
        }
        if (login.isBlank()) {
            throw new RuntimeException("Informe o login do profissional.");
        }
        if (senha.isBlank()) {
            throw new RuntimeException("Informe a senha do profissional.");
        }
        if (senha.length() < 4) {
            throw new RuntimeException("A senha do profissional precisa ter pelo menos 4 caracteres.");
        }
        if (usuarioRepository.findByLogin(login).isPresent()) {
            throw new RuntimeException("Ja existe um usuario com esse login.");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setLogin(login);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setCargo("ROLE_PROFISSIONAL");
        usuario.setDonaClinica(false);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void trocarSenha(TrocarSenhaForm form, Usuario usuarioLogado) {
        Usuario usuario = usuarioRepository.findById(usuarioLogado.getId())
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado."));

        String senhaAtual = normalizarSenha(form.getSenhaAtual());
        String novaSenha = normalizarSenha(form.getNovaSenha());
        String confirmarSenha = normalizarSenha(form.getConfirmarSenha());

        if (senhaAtual.isBlank()) {
            throw new RuntimeException("Informe sua senha atual.");
        }
        if (!verificarSenhaAtual(usuario, senhaAtual)) {
            throw new RuntimeException("Senha atual incorreta.");
        }
        if (novaSenha.isBlank()) {
            throw new RuntimeException("Informe a nova senha.");
        }
        if (novaSenha.length() < 4) {
            throw new RuntimeException("A nova senha precisa ter pelo menos 4 caracteres.");
        }
        if (!novaSenha.equals(confirmarSenha)) {
            throw new RuntimeException("A confirmacao da senha nao confere.");
        }
        if (novaSenha.equals(senhaAtual)) {
            throw new RuntimeException("A nova senha precisa ser diferente da senha atual.");
        }

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
    }

    private boolean verificarSenhaAtual(Usuario usuario, String senhaAtual) {
        String armazenada = usuario.getSenha();
        if (armazenada == null) {
            return false;
        }
        if (passwordEncoder.matches(senhaAtual, armazenada)) {
            return true;
        }
        return !armazenada.startsWith("$2a$") && armazenada.equals(senhaAtual);
    }

    private String normalizarSenha(String senha) {
        return senha != null ? senha.trim() : "";
    }
}
