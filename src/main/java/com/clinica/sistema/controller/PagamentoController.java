package com.clinica.sistema.controller;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.QrCodeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoConsultaService pagamentoConsultaService;
    private final AgendamentoRepository agendamentoRepository;
    private final AuthService authService;
    private final QrCodeService qrCodeService;

    public PagamentoController(
            PagamentoConsultaService pagamentoConsultaService,
            AgendamentoRepository agendamentoRepository,
            AuthService authService,
            QrCodeService qrCodeService
    ) {
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.agendamentoRepository = agendamentoRepository;
        this.authService = authService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping("/{id}")
    public String paginaPagamento(@PathVariable Long id, Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        Agendamento agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        validarAcesso(agendamento, usuarioLogado);

        model.addAttribute("agendamento", agendamento);
        model.addAttribute("rotuloStatus", pagamentoConsultaService.rotuloStatusPagamento(agendamento));
        model.addAttribute("bloqueado", pagamentoConsultaService.bloqueadoPorPagamento(agendamento));
        return "pagamento-consulta";
    }

    @GetMapping("/{id}/qr.png")
    public ResponseEntity<byte[]> qrCode(@PathVariable Long id) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        Agendamento agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        validarAcesso(agendamento, usuarioLogado);

        if (agendamento.getPagamentoLink() == null || agendamento.getPagamentoLink().isBlank()) {
            throw new RuntimeException("Link de pagamento ainda nao foi gerado.");
        }

        byte[] png = qrCodeService.gerarPng(agendamento.getPagamentoLink(), 280);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @GetMapping("/checkout-teste")
    public String checkoutTeste(
            @RequestParam String order,
            @RequestParam Long agendamento,
            Model model
    ) {
        Agendamento registro = agendamentoRepository.findById(agendamento)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        if (registro.getPagamentoOrderNsu() == null || !registro.getPagamentoOrderNsu().equals(order)) {
            throw new RuntimeException("Pedido de pagamento invalido.");
        }
        model.addAttribute("agendamento", registro);
        model.addAttribute("orderNsu", order);
        return "pagamento-checkout-teste";
    }

    @PostMapping("/{id}/gerar-link")
    public String gerarLink(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            Agendamento agendamento = pagamentoConsultaService.gerarLinkPagamento(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Link de pagamento gerado.");
            redirectAttributes.addFlashAttribute("pagamentoAgendamentoId", agendamento.getId());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/simular")
    public String simularPagamento(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            pagamentoConsultaService.simularPagamento(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Pagamento simulado com sucesso (modo teste).");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/pagamentos/checkout-teste")) {
            return "redirect:/agendamentos/dashboard";
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/checkout-teste/confirmar")
    public String confirmarCheckoutTeste(
            @RequestParam String order,
            @RequestParam Long agendamento,
            RedirectAttributes redirectAttributes
    ) {
        try {
            pagamentoConsultaService.confirmarPagamentoPorOrderNsu(order);
            redirectAttributes.addFlashAttribute("sucesso", "Pagamento confirmado (modo teste).");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/agendamentos/dashboard";
    }

    private void validarAcesso(Agendamento agendamento, Usuario usuarioLogado) {
        if (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado)) {
            return;
        }
        if (agendamento.getProfissional() == null
                || !agendamento.getProfissional().getId().equals(usuarioLogado.getId())) {
            throw new RuntimeException("Acesso negado.");
        }
    }
}
