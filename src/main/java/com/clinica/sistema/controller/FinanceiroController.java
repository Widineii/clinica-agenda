package com.clinica.sistema.controller;

import com.clinica.sistema.dto.DespesaForm;
import com.clinica.sistema.dto.DespesaResumoMesView;
import com.clinica.sistema.dto.FinanceiroFiltroMesProfissionalView;
import com.clinica.sistema.model.TipoDespesa;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.DespesaService;
import com.clinica.sistema.service.FinanceiroPolyanaAcessoService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.RelatorioMensalService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

@Controller
@RequestMapping("/agendamentos/financeiro")
public class FinanceiroController {

    private final DespesaService despesaService;
    private final FinanceiroPolyanaAcessoService acessoService;
    private final AuthService authService;
    private final RelatorioMensalService relatorioMensalService;
    private final AgendamentoService agendamentoService;
    private final PagamentoConsultaService pagamentoConsultaService;

    public FinanceiroController(
            DespesaService despesaService,
            FinanceiroPolyanaAcessoService acessoService,
            AuthService authService,
            RelatorioMensalService relatorioMensalService,
            AgendamentoService agendamentoService,
            PagamentoConsultaService pagamentoConsultaService
    ) {
        this.despesaService = despesaService;
        this.acessoService = acessoService;
        this.authService = authService;
        this.relatorioMensalService = relatorioMensalService;
        this.agendamentoService = agendamentoService;
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    @GetMapping
    public String painel(
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String aba,
            @RequestParam(required = false) String mesAno,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano
    ) {
        if ("consultas".equalsIgnoreCase(aba)) {
            return "redirect:/agendamentos/financeiro/configuracao-taxas";
        }

        String bloqueio = verificarAcesso(model, session, redirectAttributes);
        if (bloqueio != null) {
            return bloqueio;
        }

        YearMonth mesSelecionado = resolverMes(mesAno, mes, ano);
        DespesaResumoMesView resumo = despesaService.montarResumoMes(mesSelecionado);

        model.addAttribute("resumo", resumo);
        model.addAttribute("despesaForm", criarFormPadrao(resumo));
        model.addAttribute("mostrarConfiguracaoTaxas", true);
        model.addAttribute("mostrarGestaoFinanceira", false);
        return "financeiro";
    }

    @GetMapping("/configuracao-taxas")
    public String configuracaoTaxas(
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String mesAno,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Long profissionalId
    ) {
        String bloqueio = verificarAcesso(model, session, redirectAttributes);
        if (bloqueio != null) {
            return bloqueio;
        }

        YearMonth mesSelecionado = resolverMes(mesAno, mes, ano);
        var profissionais = agendamentoService.listarProfissionaisComAgendamentoNoMes(mesSelecionado);
        Usuario profissionalSelecionado = resolverProfissional(profissionalId, profissionais);

        model.addAttribute(
                "filtro",
                new FinanceiroFiltroMesProfissionalView(mesSelecionado, profissionalSelecionado, profissionais)
        );
        model.addAttribute(
                "atendimentos",
                profissionalSelecionado != null
                        ? agendamentoService.listarAtendimentosProfissionalNoMes(
                                profissionalSelecionado.getId(),
                                mesSelecionado
                        )
                        : java.util.Collections.emptyList()
        );
        model.addAttribute("pagamentoService", pagamentoConsultaService);
        model.addAttribute("mostrarConfiguracaoTaxas", false);
        model.addAttribute("mostrarGestaoFinanceira", true);
        return "financeiro-configuracao-taxas";
    }

    @PostMapping
    public String cadastrar(
            @ModelAttribute DespesaForm despesaForm,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!acessoService.podeAcessarGestaoFinanceira(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Gestao financeira nao disponivel para este usuario."
            );
            return "redirect:/agendamentos/dashboard";
        }

        try {
            despesaService.cadastrar(despesaForm);
            redirectAttributes.addFlashAttribute("sucesso", "Despesa cadastrada com sucesso.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }

        return redirectMes(despesaForm.getMes(), despesaForm.getAno());
    }

    @PostMapping("/{id}/encerrar-mensal")
    public String encerrarMensal(
            @PathVariable Long id,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!acessoService.podeAcessarGestaoFinanceira(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Gestao financeira nao disponivel para este usuario."
            );
            return "redirect:/agendamentos/dashboard";
        }

        YearMonth mesSelecionado = resolverMes(null, mes, ano);
        try {
            despesaService.encerrarMensal(id, mesSelecionado);
            redirectAttributes.addFlashAttribute("sucesso", "Despesa mensal encerrada neste mes.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }

        return redirectMes(mesSelecionado.getMonthValue(), mesSelecionado.getYear());
    }

    @PostMapping("/{id}/excluir-unica")
    public String excluirUnica(
            @PathVariable Long id,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano,
            RedirectAttributes redirectAttributes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!acessoService.podeAcessarGestaoFinanceira(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Gestao financeira nao disponivel para este usuario."
            );
            return "redirect:/agendamentos/dashboard";
        }

        YearMonth mesSelecionado = resolverMes(null, mes, ano);
        try {
            despesaService.excluirUnica(id, mesSelecionado);
            redirectAttributes.addFlashAttribute("sucesso", "Despesa unica excluida.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }

        return redirectMes(mesSelecionado.getMonthValue(), mesSelecionado.getYear());
    }

    private String verificarAcesso(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!acessoService.podeAcessarGestaoFinanceira(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Gestao financeira nao disponivel para este usuario."
            );
            return "redirect:/agendamentos/dashboard";
        }

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuarioLogado));
        relatorioMensalService.adicionarNotificacaoAoModelSeAplicavel(model, session);
        return null;
    }

    private DespesaForm criarFormPadrao(DespesaResumoMesView resumo) {
        DespesaForm form = new DespesaForm();
        form.setTipo(TipoDespesa.MENSAL);
        form.setMes(resumo.getMes());
        form.setAno(resumo.getAno());
        form.setData(resumo.getMesSelecionado().atDay(1));
        return form;
    }

    private YearMonth resolverMes(String mesAno, Integer mes, Integer ano) {
        if (mesAno != null && !mesAno.isBlank()) {
            return YearMonth.parse(mesAno);
        }
        if (mes != null && ano != null) {
            return YearMonth.of(ano, mes);
        }
        return YearMonth.now();
    }

    private Usuario resolverProfissional(Long profissionalId, java.util.List<Usuario> profissionais) {
        if (profissionais == null || profissionais.isEmpty()) {
            return null;
        }
        if (profissionalId != null) {
            return profissionais.stream()
                    .filter(profissional -> profissionalId.equals(profissional.getId()))
                    .findFirst()
                    .orElse(profissionais.get(0));
        }
        return profissionais.get(0);
    }

    private String redirectMes(Integer mes, Integer ano) {
        YearMonth alvo = resolverMes(null, mes, ano);
        return "redirect:/agendamentos/financeiro?mesAno=" + alvo;
    }
}
