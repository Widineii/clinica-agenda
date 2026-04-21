package com.clinica.sistema.service;

import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;

    public UsuarioService(UsuarioRepository usuarioRepository, AuthService authService) {
        this.usuarioRepository = usuarioRepository;
        this.authService = authService;
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
        usuario.setSenha(senha);
        usuario.setCargo("ROLE_PROFISSIONAL");
        return usuarioRepository.save(usuario);
    }
}
