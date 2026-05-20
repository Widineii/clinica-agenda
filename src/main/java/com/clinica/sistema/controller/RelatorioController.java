package com.clinica.sistema.controller;

import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.RelatorioMensalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.util.Optional;

@Controller
@RequestMapping("/agendamentos/relatorio")
public class RelatorioController {

    private static final String MSG_RELATORIO_DISPONIVEL_APOS_DIA_3 =
            "O relatorio so fica disponivel a partir do dia 3 do mes seguinte.";

    private static final Logger log = LoggerFactory.getLogger(RelatorioController.class);

    private final RelatorioMensalService relatorioMensalService;
    private final AuthService authService;

    public RelatorioController(RelatorioMensalService relatorioMensalService, AuthService authService) {
        this.relatorioMensalService = relatorioMensalService;
        this.authService = authService;
    }

    @GetMapping
    public String relatorioPrincipal(Model model, RedirectAttributes redirectAttributes) {
        return relatorioMensal(model, redirectAttributes);
    }

    @GetMapping("/mensal")
    public String relatorioMensal(Model model, RedirectAttributes redirectAttributes) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!podeVerRelatorio(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Somente administracao ou dona da clinica pode ver o relatorio."
            );
            return "redirect:/agendamentos/dashboard";
        }

        if (!relatorioMensalService.podeExecutarFechamentoAutomatico()) {
            redirectAttributes.addFlashAttribute("erroContexto", "relatorio");
            redirectAttributes.addFlashAttribute("erro", MSG_RELATORIO_DISPONIVEL_APOS_DIA_3);
            return "redirect:/agendamentos/dashboard";
        }

        YearMonth mesPassado = relatorioMensalService.mesPassadoReferencia();
        Optional<RelatorioMensalArquivado> arquivado = Optional.empty();
        RelatorioMensalUsoSalasView relatorio;

        try {
            relatorioMensalService.executarFechamentoAutomaticoSeDevido();
            arquivado = relatorioMensalService.buscarArquivado(mesPassado);
            relatorio = relatorioMensalService.carregarRelatorioParaExibicao(mesPassado);
        } catch (RuntimeException e) {
            log.error("Falha ao carregar relatorio mensal", e);
            redirectAttributes.addFlashAttribute("erroContexto", "relatorio");
            redirectAttributes.addFlashAttribute("erro", MSG_RELATORIO_DISPONIVEL_APOS_DIA_3);
            return "redirect:/agendamentos/dashboard";
        }

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
        model.addAttribute("relatorio", relatorio);
        model.addAttribute("mesPassadoLabel", relatorio.getMesReferenciaLabel());
        model.addAttribute("relatorioArquivado", arquivado.isPresent());
        model.addAttribute("podeBaixarPdf", arquivado.isPresent());
        model.addAttribute("aguardandoDia3", !relatorioMensalService.podeExecutarFechamentoAutomatico());
        model.addAttribute("aguardandoProcessamentoAutomatico",
                relatorioMensalService.podeExecutarFechamentoAutomatico() && arquivado.isEmpty());
        model.addAttribute("diaFechamento", 3);
        model.addAttribute("historico", relatorioMensalService.listarArquivados());
        if (arquivado.isPresent()) {
            model.addAttribute("geradoEm", arquivado.get().getGeradoEm());
            model.addAttribute("agendamentosRemovidos", arquivado.get().getAgendamentosRemovidos());
        }
        return "relatorio-mensal";
    }

    @GetMapping("/mensal/download")
    public ResponseEntity<byte[]> baixarPdf(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!podeVerRelatorio(usuarioLogado)) {
            return ResponseEntity.status(403).build();
        }

        YearMonth mesReferencia = ano != null && mes != null
                ? YearMonth.of(ano, mes)
                : relatorioMensalService.mesPassadoReferencia();

        Optional<RelatorioMensalArquivado> arquivado = relatorioMensalService.buscarArquivado(mesReferencia);
        if (arquivado.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdf = arquivado.get().getPdf();
        String nomeArquivo = relatorioMensalService.nomeArquivoPdf(mesReferencia);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private boolean podeVerRelatorio(Usuario usuario) {
        return authService.isAdmin(usuario) || authService.isDonaClinica(usuario);
    }
}
