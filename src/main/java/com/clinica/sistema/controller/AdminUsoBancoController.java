package com.clinica.sistema.controller;

import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.UsoBancoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/agendamentos/admin")
public class AdminUsoBancoController {

    private final AuthService authService;
    private final UsoBancoService usoBancoService;

    public AdminUsoBancoController(AuthService authService, UsoBancoService usoBancoService) {
        this.authService = authService;
        this.usoBancoService = usoBancoService;
    }

    @GetMapping("/uso-banco")
    public String usoBanco(Model model) {
        var usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.isAdmin(usuario)) {
            return "redirect:/agendamentos/dashboard";
        }

        model.addAttribute("usuarioLogado", usuario);
        model.addAttribute("isAdmin", true);
        model.addAttribute("resumo", usoBancoService.montarResumo());
        return "admin-uso-banco";
    }
}
