package com.clinica.sistema.controller;

import com.clinica.sistema.dto.RelatorioHistoricoResumo;
import com.clinica.sistema.dto.RelatorioLinhaView;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelatorioUsoSalaItem;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.RelatorioMensalService;
import com.clinica.sistema.service.RelatorioSemanalService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/agendamentos/relatorio")
public class RelatorioController {

    private static final Logger log = LoggerFactory.getLogger(RelatorioController.class);

    private final RelatorioMensalService relatorioMensalService;
    private final RelatorioSemanalService relatorioSemanalService;
    private final AuthService authService;
    private final Environment environment;

    public RelatorioController(
            RelatorioMensalService relatorioMensalService,
            RelatorioSemanalService relatorioSemanalService,
            AuthService authService,
            Environment environment
    ) {
        this.relatorioMensalService = relatorioMensalService;
        this.relatorioSemanalService = relatorioSemanalService;
        this.authService = authService;
        this.environment = environment;
    }

    private boolean isPerfilLocal() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    @GetMapping
    public String relatorioPrincipal(Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        return relatorioMensal(model, redirectAttributes, session, false);
    }

    @GetMapping("/mensal")
    public String relatorioMensal(
            Model model,
            RedirectAttributes redirectAttributes,
            HttpSession session,
            @RequestParam(required = false, defaultValue = "false") boolean viaNotificacao
    ) {
        relatorioSemanalService.limparSessao(session);
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
        List<RelatorioHistoricoResumo> historico = Collections.emptyList();

        boolean fechamentoNestaVisita = false;
        try {
            fechamentoNestaVisita = relatorioMensalService.executarFechamentoAutomaticoSeDevido();
            relatorioMensalService.removerPdfsExpiradosSeDevido();
            arquivado = relatorioMensalService.buscarArquivado(mesPassado);
            if (arquivado.isPresent()
                    && relatorioMensalService.pdfRemovidoDoBanco(arquivado.get())) {
                try {
                    relatorioMensalService.regenerarESalvarPdf(arquivado.get());
                    arquivado = relatorioMensalService.buscarArquivado(mesPassado);
                } catch (RuntimeException e) {
                    log.warn("Falha ao regenerar PDF de {}; exibindo relatorio na tela.", mesPassado, e);
                }
            }
            relatorio = relatorioMensalService.carregarRelatorioParaExibicao(mesPassado);
            try {
                historico = relatorioMensalService.listarHistoricoResumo();
            } catch (RuntimeException e) {
                log.warn("Historico de relatorios indisponivel; continuando sem lista.", e);
            }
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
        model.addAttribute("linhas", montarLinhasRelatorio(relatorio));
        model.addAttribute("totalProfissionais", relatorio.getProfissionais().size());
        model.addAttribute("mesPassadoLabel", relatorio.getMesReferenciaLabel());
        model.addAttribute("relatorioArquivado", arquivado.isPresent());
        boolean podeBaixarPdf = arquivado.isPresent()
                && !aguardandoDia3
                && relatorioMensalService.podeExportarPdf(arquivado.orElse(null));
        model.addAttribute("podeBaixarPdf", podeBaixarPdf);
        model.addAttribute("pdfRemovido",
                arquivado.isPresent() && relatorioMensalService.pdfRemovidoDoBanco(arquivado.orElse(null)));
        model.addAttribute("diaRemocaoPdf", relatorioMensalService.getDiaRemocaoPdf());
        model.addAttribute("aguardandoDia3", aguardandoDia3);
        model.addAttribute("aguardandoProcessamentoAutomatico",
                relatorioMensalService.podeExecutarFechamentoAutomatico() && arquivado.isEmpty());
        model.addAttribute("diaFechamento", 3);
        model.addAttribute("historico", historico);
        model.addAttribute("mesAtualLabel", relatorioMensalService.formatarMesReferencia(YearMonth.now()));
        model.addAttribute("versaoDownload", System.currentTimeMillis());
        if (arquivado.isPresent()) {
            model.addAttribute("geradoEm", arquivado.get().getGeradoEm());
            model.addAttribute("agendamentosRemovidos", arquivado.get().getAgendamentosRemovidos());
        }
        if (viaNotificacao && !aguardandoDia3 && arquivado.isPresent() && podeBaixarPdf) {
            model.addAttribute(
                    "sucessoNotificacao",
                    "O relatorio de " + relatorio.getMesReferenciaLabel()
                            + " esta pronto. Use o botao Baixar PDF no topo da pagina."
            );
        } else if (viaNotificacao && !aguardandoDia3 && fechamentoNestaVisita) {
            model.addAttribute(
                    "sucessoNotificacao",
                    "O relatorio de " + relatorio.getMesReferenciaLabel()
                            + " foi gerado agora. Use o botao Baixar PDF no topo da pagina."
            );
        } else if (viaNotificacao && !aguardandoDia3) {
            model.addAttribute(
                    "sucessoNotificacao",
                    "Abrindo o relatorio de " + relatorio.getMesReferenciaLabel()
                            + ". Quando o fechamento terminar, o botao Baixar PDF ficara disponivel."
            );
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
        if (arquivado.isEmpty() || !relatorioMensalService.podeExportarPdf(arquivado.get())) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdf;
        try {
            pdf = relatorioMensalService.obterPdfParaDownload(mesReferencia);
        } catch (RuntimeException e) {
            log.error("Falha ao gerar PDF do relatorio {}", mesReferencia, e);
            return ResponseEntity.notFound().build();
        }
        String nomeArquivo = relatorioMensalService.nomeArquivoPdf(mesReferencia);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/semanal")
    public String relatorioSemanal(Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!podeVerRelatorio(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Somente administracao ou dona da clinica pode ver o relatorio."
            );
            return "redirect:/agendamentos/dashboard";
        }

        try {
            RelatorioMensalUsoSalasView relatorio = relatorioSemanalService.montarRelatorioSemanalAtual();
            relatorioSemanalService.armazenarNaSessao(session, relatorio);

            model.addAttribute("usuarioLogado", usuarioLogado);
            model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
            model.addAttribute("relatorio", relatorio);
            model.addAttribute("linhas", montarLinhasRelatorio(relatorio));
            model.addAttribute("totalProfissionais", relatorio.getProfissionais().size());
            model.addAttribute("periodoLabel", relatorio.getMesReferenciaLabel());
            model.addAttribute("versaoDownload", System.currentTimeMillis());
            model.addAttribute("geradoEm", java.time.LocalDateTime.now());
            model.addAttribute("perfilLocal", isPerfilLocal());
            return "relatorio-semanal";
        } catch (RuntimeException e) {
            log.error("Falha ao carregar relatorio semanal", e);
            redirectAttributes.addFlashAttribute("erroContexto", "relatorio");
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Nao foi possivel gerar o relatorio semanal. Tente novamente."
            );
            return "redirect:/agendamentos/relatorio/mensal";
        }
    }

    @GetMapping("/semanal/download")
    public ResponseEntity<byte[]> baixarPdfSemanal(HttpSession session) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!podeVerRelatorio(usuarioLogado)) {
            return ResponseEntity.status(403).build();
        }

        if (relatorioSemanalService.obterDaSessao(session).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            RelatorioMensalUsoSalasView relatorio = relatorioSemanalService.obterDaSessao(session).get();
            byte[] pdf = relatorioSemanalService.gerarPdfDaSessao(session);
            String nomeArquivo = relatorioSemanalService.nomeArquivoPdf(relatorio);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (RuntimeException e) {
            log.error("Falha ao gerar PDF do relatorio semanal", e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/semanal/limpar")
    public ResponseEntity<Void> limparRelatorioSemanal(HttpSession session) {
        relatorioSemanalService.limparSessao(session);
        return ResponseEntity.noContent().build();
    }

    private boolean podeVerRelatorio(Usuario usuario) {
        return authService.isAdmin(usuario) || authService.isDonaClinica(usuario);
    }

    private List<RelatorioLinhaView> montarLinhasRelatorio(RelatorioMensalUsoSalasView relatorio) {
        return relatorio.getProfissionais().stream()
                .map(this::paraLinhaView)
                .toList();
    }

    private RelatorioLinhaView paraLinhaView(RelatorioUsoSalaProfissional profissional) {
        Map<String, Long> porSala = new HashMap<>();
        for (RelatorioUsoSalaItem item : profissional.getSalas()) {
            porSala.put(item.getSalaNome(), item.getQuantidade());
        }

        RelatorioLinhaView linha = new RelatorioLinhaView();
        linha.setProfissionalNome(profissional.getProfissionalNome());
        linha.setTotalHorarios(profissional.getTotalHorarios());
        linha.setSala1(porSala.getOrDefault("Sala 1", 0L));
        linha.setSala2(porSala.getOrDefault("Sala 2", 0L));
        linha.setSala3(porSala.getOrDefault("Sala 3", 0L));
        linha.setSala4(porSala.getOrDefault("Sala 4", 0L));
        return linha;
    }
}
