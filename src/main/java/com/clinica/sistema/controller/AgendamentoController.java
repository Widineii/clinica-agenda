package com.clinica.sistema.controller;

import com.clinica.sistema.config.StartupDataInitializer;
import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.dto.TrocarSenhaAdminForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.RelatorioMensalService;
import com.clinica.sistema.service.RelatorioSemanalService;
import com.clinica.sistema.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/agendamentos")
public class AgendamentoController {
    private final AgendamentoService service;
    private final AuthService authService;
    private final StartupDataInitializer startupDataInitializer;
    private final UsuarioService usuarioService;
    private final RelatorioSemanalService relatorioSemanalService;
    private final RelatorioMensalService relatorioMensalService;

    public AgendamentoController(
            AgendamentoService service,
            AuthService authService,
            StartupDataInitializer startupDataInitializer,
            UsuarioService usuarioService,
            RelatorioSemanalService relatorioSemanalService,
            RelatorioMensalService relatorioMensalService
    ) {
        this.service = service;
        this.authService = authService;
        this.startupDataInitializer = startupDataInitializer;
        this.usuarioService = usuarioService;
        this.relatorioSemanalService = relatorioSemanalService;
        this.relatorioMensalService = relatorioMensalService;
    }

    @ModelAttribute("gradeAcoesPorId")
    public Map<Long, String> gradeAcoesPorIdPadrao() {
        return Collections.emptyMap();
    }

    @GetMapping("/dashboard")
    public String abrirDashboard(
            Model model,
            @RequestParam(required = false) Long salaId,
            @RequestParam(required = false) LocalDate semana,
            HttpSession session
    ) {
        relatorioSemanalService.limparSessao(session);
        Usuario usuarioLogado = authService.buscarUsuarioLogado().orElse(null);
        if (usuarioLogado == null) {
            return "redirect:/login";
        }

        boolean isAdmin = authService.isAdmin(usuarioLogado);

        if (!model.containsAttribute("agendamentoForm")) {
            AgendamentoForm form = new AgendamentoForm();
            form.setProfissionalId(usuarioLogado.getId());
            LocalDate dataSugerida = agendaDataSugerida(semana);
            form.setDataAtendimento(dataSugerida);
            form.setHorarioAtendimento(service.listarHorariosDisponiveis().get(0));
            model.addAttribute("agendamentoForm", form);
        }
        boolean podeGerenciarEquipe = authService.podeGerenciarEquipe(usuarioLogado);
        if (!podeGerenciarEquipe && !model.containsAttribute("trocarSenhaForm")) {
            model.addAttribute("trocarSenhaForm", new com.clinica.sistema.dto.TrocarSenhaForm());
        }
        if (model.containsAttribute("trocarSenhaForm")) {
            Object formFlash = model.getAttribute("trocarSenhaForm");
            if (formFlash instanceof com.clinica.sistema.dto.TrocarSenhaForm form) {
                form.setSenhaAtual("");
                model.addAttribute("trocarSenhaForm", form);
            }
        }

        List<com.clinica.sistema.model.Agendamento> agendamentos = service.buscarParaUsuario(usuarioLogado);
        List<com.clinica.sistema.model.Agendamento> agendamentosAvulsos =
                service.listarProximosPorSerie(agendamentos, com.clinica.sistema.model.Agendamento::isAvulso);
        List<com.clinica.sistema.model.Agendamento> agendamentosFixos =
                service.listarProximasOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isFixoSemanal);
        List<com.clinica.sistema.model.Agendamento> agendamentosQuinzenais =
                service.listarProximasOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isQuinzenal);

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuarioLogado));
        model.addAttribute("podeGerenciarEquipe", podeGerenciarEquipe);
        model.addAttribute("agendamentos", agendamentos);
        model.addAttribute("agendamentosAvulsos", agendamentosAvulsos);
        model.addAttribute("agendamentosFixos", agendamentosFixos);
        model.addAttribute("agendamentosQuinzenais", agendamentosQuinzenais);
        model.addAttribute("totalAgendamentosAvulsos",
                service.contarSeries(agendamentos, com.clinica.sistema.model.Agendamento::isAvulso));
        model.addAttribute("totalAgendamentosFixos",
                service.contarOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isFixoSemanal));
        model.addAttribute("totalAgendamentosQuinzenais",
                service.contarOcorrencias(agendamentos, com.clinica.sistema.model.Agendamento::isQuinzenal));
        model.addAttribute("salas", service.listarSalas());
        model.addAttribute("profissionais", podeGerenciarEquipe
                ? service.listarProfissionais().stream()
                        .filter(profissional -> !authService.isAdmin(profissional))
                        .toList()
                : java.util.List.of(usuarioLogado));
        model.addAttribute("horariosDisponiveis", service.listarHorariosDisponiveis());
        var agendaSala = service.montarAgendaSala(salaId, semana);
        Map<Long, String> gradeAcoesPorId = service.montarAcoesGradePorId(agendaSala, usuarioLogado);
        List<com.clinica.sistema.model.Agendamento> agendamentosDoDia =
                service.listarAgendamentosDoDia(usuarioLogado, podeGerenciarEquipe);
        model.addAttribute("agendaSala", agendaSala);
        model.addAttribute("agendamentosDoDia", agendamentosDoDia);
        model.addAttribute("dataAgendaDia", LocalDate.now());
        model.addAttribute("totalAgendamentosDoDia", agendamentosDoDia.size());
        model.addAttribute("gradeAcoesPorId", gradeAcoesPorId != null ? gradeAcoesPorId : Collections.emptyMap());
        if (podeGerenciarEquipe) {
            relatorioMensalService.adicionarNotificacaoAoModelSeAplicavel(model);
        }
        return "agenda";
    }

    @GetMapping("/central-profissionais")
    public String abrirCentralProfissionais(Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeGerenciarEquipe(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }

        if (!model.containsAttribute("cadastroProfissionalForm")) {
            model.addAttribute("cadastroProfissionalForm", new CadastroProfissionalForm());
        }
        if (!model.containsAttribute("trocarSenhaAdminForm")) {
            model.addAttribute("trocarSenhaAdminForm", new TrocarSenhaAdminForm());
        }

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
        model.addAttribute("isDonaClinica", authService.isDonaClinica(usuarioLogado));
        model.addAttribute("profissionais", usuarioService.listarProfissionaisDaEquipe());
        model.addAttribute("usuariosSenha", usuarioService.listarUsuariosParaTrocaSenha());
        relatorioMensalService.adicionarNotificacaoAoModelSeAplicavel(model);
        return "central-profissionais";
    }

    @PostMapping("/central-profissionais/cadastrar")
    public String cadastrarProfissionalCentral(
            @ModelAttribute CadastroProfissionalForm cadastroProfissionalForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            Usuario novo = usuarioService.cadastrarProfissional(cadastroProfissionalForm, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Profissional cadastrado: " + novo.getNome() + ".");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("cadastroProfissionalForm", cadastroProfissionalForm);
            redirectAttributes.addFlashAttribute("abrirModalCadastro", true);
        }
        return "redirect:/agendamentos/central-profissionais";
    }

    @PostMapping("/central-profissionais/trocar-senha")
    public String trocarSenhaCentral(
            @ModelAttribute TrocarSenhaAdminForm trocarSenhaAdminForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.trocarSenhaComoGestor(trocarSenhaAdminForm, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Senha alterada com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("trocarSenhaAdminForm", trocarSenhaAdminForm);
            redirectAttributes.addFlashAttribute("abrirModalTrocarSenha", true);
        }
        return "redirect:/agendamentos/central-profissionais";
    }

    @PostMapping("/central-profissionais/excluir")
    public String excluirProfissionalCentral(
            @RequestParam Long usuarioId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.excluirUsuario(usuarioId, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Usuario excluido com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/central-profissionais";
    }

    @PostMapping
    public String criar(
            @ModelAttribute AgendamentoForm agendamentoForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            service.salvar(agendamentoForm, usuarioLogado);
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "QUINZENAL".equalsIgnoreCase(agendamentoForm.getRecorrencia())
                            ? "Agendamento quinzenal cadastrado com sucesso."
                            : "SEMANAL".equalsIgnoreCase(agendamentoForm.getRecorrencia())
                                    ? "Agendamento fixo cadastrado para as proximas 12 semanas."
                                    : "Agendamento cadastrado com sucesso."
            );
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
            redirectAttributes.addFlashAttribute("abrirModalErroAgendamento", true);
            redirectAttributes.addFlashAttribute("agendamentoForm", agendamentoForm);
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            service.cancelar(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Agendamento cancelado com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/encerrar-fixo")
    public String encerrarFixo(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            service.encerrarSerieFixa(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Horario fixo encerrado com sucesso para as proximas ocorrencias.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/sincronizar-fixos")
    public String sincronizarFixos(
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            if (!authService.isAdmin(usuarioLogado)) {
                throw new RuntimeException("Somente a administracao pode carregar a agenda fixa.");
            }

            startupDataInitializer.sincronizarCargaInicialClinica();
            redirectAttributes.addFlashAttribute("sucesso", "Agenda fixa da planilha carregada com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/resetar-demo")
    public String resetarDemonstracao(
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            if (!authService.isAdmin(usuarioLogado)) {
                throw new RuntimeException("Somente a administracao pode restaurar a demonstracao.");
            }

            startupDataInitializer.resetarBaseDemonstracao(usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Demonstracao restaurada com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "agendamento");
        }
        return "redirect:/agendamentos/dashboard";
    }

    private LocalDate agendaDataSugerida(LocalDate semana) {
        LocalDate base = semana != null ? semana : LocalDate.now();
        if (base.getDayOfWeek().getValue() > 6) {
            base = base.with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        }
        return base;
    }
}
