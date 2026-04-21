package com.clinica.sistema.service;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    public static final String USUARIO_ID = "usuarioLogadoId";

    private final UsuarioRepository usuarioRepository;

    public AuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario autenticar(String login, String senha) {
        String loginNormalizado = login != null ? login.trim().toLowerCase(Locale.ROOT) : "";

        Usuario usuario = usuarioRepository.findByLogin(loginNormalizado)
                .orElseThrow(() -> new RuntimeException("Login ou senha invalidos."));

        if (!usuario.getSenha().equals(senha)) {
            throw new RuntimeException("Login ou senha invalidos.");
        }

        return usuario;
    }

    public void registrarSessao(HttpSession session, Usuario usuario) {
        session.setAttribute(USUARIO_ID, usuario.getId());
    }

    public void encerrarSessao(HttpSession session) {
        session.invalidate();
    }

    public Optional<Usuario> buscarUsuarioLogado(HttpSession session) {
        Object usuarioId = session.getAttribute(USUARIO_ID);
        if (!(usuarioId instanceof Long id)) {
            return Optional.empty();
        }
        return usuarioRepository.findById(id);
    }

    public Usuario buscarUsuarioLogadoObrigatorio(HttpSession session) {
        return buscarUsuarioLogado(session)
                .orElseThrow(() -> new RuntimeException("Sessao expirada. Faca login novamente."));
    }

    public boolean isAdmin(Usuario usuario) {
        return "ROLE_ADMIN".equals(usuario.getCargo());
    }
}
