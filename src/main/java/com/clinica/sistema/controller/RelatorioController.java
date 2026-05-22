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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/agendamentos/relatorio")
public class RelatorioController {

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

        YearMonth mesPassado = relatorioMensalService.mesPassadoReferencia();
        Optional<RelatorioMensalArquivado> arquivado = Optional.empty();
        RelatorioMensalUsoSalasView relatorio;
        List<RelatorioMensalArquivado> historico = Collections.emptyList();

        try {
            relatorioMensalService.executarFechamentoAutomaticoSeDevido();
            relatorioMensalService.removerPdfsExpiradosSeDevido();
            arquivado = relatorioMensalService.buscarArquivado(mesPassado);
            relatorio = relatorioMensalService.carregarRelatorioParaExibicao(mesPassado);
            historico = relatorioMensalService.listarArquivados();
        } catch (RuntimeException e) {
            log.error("Falha ao carregar relatorio mensal de {}", mesPassado, e);
            redirectAttributes.addFlashAttribute("erroContexto", "relatorio");
            if (!relatorioMensalService.podeExecutarFechamentoAutomatico()) {
                redirectAttributes.addFlashAttribute(
                        "erro",
                        relatorioMensalService.mensagemRelatorioDisponivelAposDia3()
                );
            } else {
                redirectAttributes.addFlashAttribute(
                        "erro",
                        "Nao foi possivel abrir o relatorio de "
                                + relatorioMensalService.formatarMesReferencia(mesPassado)
                                + ". Tente novamente em alguns minutos."
                );
            }
            return "redirect:/agendamentos/dashboard";
        }

        boolean aguardandoDia3 = !relatorioMensalService.podeExecutarFechamentoAutomatico();

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
        model.addAttribute("relatorio", relatorio);
        model.addAttribute("mesPassadoLabel", relatorio.getMesReferenciaLabel());
        model.addAttribute("relatorioArquivado", arquivado.isPresent());
        model.addAttribute("podeBaixarPdf",
                arquivado.isPresent() && !aguardandoDia3 && relatorioMensalService.temPdfDisponivel(arquivado.orElse(null)));
        model.addAttribute("pdfRemovido",
                arquivado.isPresent() && !relatorioMensalService.temPdfDisponivel(arquivado.orElse(null)));
        model.addAttribute("diaRemocaoPdf", relatorioMensalService.getDiaRemocaoPdf());
        model.addAttribute("aguardandoDia3", aguardandoDia3);
        model.addAttribute("aguardandoProcessamentoAutomatico",
                relatorioMensalService.podeExecutarFechamentoAutomatico() && arquivado.isEmpty());
        model.addAttribute("diaFechamento", 3);
        model.addAttribute("historico", historico);
        model.addAttribute("mesAtualLabel", relatorioMensalService.formatarMesReferencia(YearMonth.now()));
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
        if (arquivado.isEmpty() || !relatorioMensalService.temPdfDisponivel(arquivado.get())) {
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
