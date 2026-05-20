package com.clinica.sistema.controller;

import com.clinica.sistema.dto.LoginForm;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.UsuarioService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final AuthService authService;
    private final UsuarioService usuarioService;

    public AuthController(AuthService authService, UsuarioService usuarioService) {
        this.authService = authService;
        this.usuarioService = usuarioService;
    }

    @GetMapping({"/", "/login"})
    public String login(
            Model model,
            Authentication authentication,
            @RequestParam(required = false) String erro,
            @RequestParam(required = false) String logout
    ) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/agendamentos/dashboard";
        }

        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
        if (erro != null) {
            model.addAttribute("erro", "Login ou senha invalidos.");
        }
        if (logout != null) {
            model.addAttribute("sucesso", "Voce saiu do sistema com sucesso.");
        }
        return "login";
    }

    @PostMapping("/conta/trocar-senha")
    public String trocarSenha(
            @ModelAttribute TrocarSenhaForm trocarSenhaForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            usuarioService.trocarSenha(trocarSenhaForm, authService.buscarUsuarioLogadoObrigatorio());
            redirectAttributes.addFlashAttribute("sucesso", "Senha alterada com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("abrirModalTrocarSenha", true);
        }
        return "redirect:/agendamentos/dashboard";
    }
}
