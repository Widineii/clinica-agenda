package com.clinica.sistema.controller;

import com.clinica.sistema.dto.LoginForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping({"/", "/login"})
    public String login(Model model, HttpSession session) {
        if (authService.buscarUsuarioLogado(session).isPresent()) {
            return "redirect:/agendamentos/dashboard";
        }

        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
        return "login";
    }

    @PostMapping("/login")
    public String autenticar(
            @ModelAttribute LoginForm loginForm,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuario = authService.autenticar(loginForm.getLogin(), loginForm.getSenha());
            authService.registrarSessao(session, usuario);
            return "redirect:/agendamentos/dashboard";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("loginForm", loginForm);
            return "redirect:/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        authService.encerrarSessao(session);
        return "redirect:/login";
    }
}
