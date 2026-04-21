package com.clinica.sistema.controller;

import com.clinica.sistema.config.StartupDataInitializer;
import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.AuthService;
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
import java.util.List;

@Controller
@RequestMapping("/agendamentos")
public class AgendamentoController {
    private final AgendamentoService service;
    private final AuthService authService;
    private final StartupDataInitializer startupDataInitializer;
    private final UsuarioService usuarioService;

    public AgendamentoController(
            AgendamentoService service,
            AuthService authService,
            StartupDataInitializer startupDataInitializer,
            UsuarioService usuarioService
    ) {
        this.service = service;
        this.authService = authService;
        this.startupDataInitializer = startupDataInitializer;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/dashboard")
    public String abrirDashboard(
            Model model,
            HttpSession session,
            @RequestParam(required = false) Long salaId,
            @RequestParam(required = false) LocalDate semana
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogado(session).orElse(null);
        if (usuarioLogado == null) {
            return "redirect:/login";
        }

        boolean isAdmin = authService.isAdmin(usuarioLogado);
        if (isAdmin && service.listarProfissionais().stream()
                .noneMatch(profissional -> !"admin".equalsIgnoreCase(profissional.getLogin()))) {
            startupDataInitializer.sincronizarCargaInicialClinica();
        }

        if (!model.containsAttribute("agendamentoForm")) {
            AgendamentoForm form = new AgendamentoForm();
            form.setProfissionalId(usuarioLogado.getId());
            LocalDate dataSugerida = agendaDataSugerida(semana);
            form.setDataAtendimento(dataSugerida);
            form.setHorarioAtendimento(service.listarHorariosDisponiveis().get(0));
            model.addAttribute("agendamentoForm", form);
        }
        if (!model.containsAttribute("cadastroProfissionalForm")) {
            model.addAttribute("cadastroProfissionalForm", new CadastroProfissionalForm());
        }

        List<com.clinica.sistema.model.Agendamento> agendamentos = service.buscarParaUsuario(usuarioLogado);
        List<com.clinica.sistema.model.Agendamento> agendamentosFixos = agendamentos.stream()
                .filter(agendamento -> Boolean.TRUE.equals(agendamento.getFixo()))
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(LocalDateTime.now().minusDays(1)))
                .limit(24)
                .toList();
        List<com.clinica.sistema.model.Agendamento> agendamentosAvulsos = agendamentos.stream()
                .filter(agendamento -> !Boolean.TRUE.equals(agendamento.getFixo()))
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(LocalDateTime.now().minusDays(30)))
                .limit(24)
                .toList();

        model.addAttribute("usuarioLogado", usuarioLogado);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("agendamentos", agendamentos);
        model.addAttribute("agendamentosFixos", agendamentosFixos);
        model.addAttribute("agendamentosAvulsos", agendamentosAvulsos);
        model.addAttribute("totalAgendamentosFixos", agendamentos.stream()
                .filter(agendamento -> Boolean.TRUE.equals(agendamento.getFixo()))
                .count());
        model.addAttribute("totalAgendamentosAvulsos", agendamentos.stream()
                .filter(agendamento -> !Boolean.TRUE.equals(agendamento.getFixo()))
                .count());
        model.addAttribute("salas", service.listarSalas());
        model.addAttribute("profissionais", isAdmin ? service.listarProfissionais() : java.util.List.of(usuarioLogado));
        model.addAttribute("horariosDisponiveis", service.listarHorariosDisponiveis());
        model.addAttribute("agendaSala", service.montarAgendaSala(salaId, semana));
        return "agenda";
    }

    @PostMapping
    public String criar(
            @ModelAttribute AgendamentoForm agendamentoForm,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio(session);
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
            redirectAttributes.addFlashAttribute("agendamentoForm", agendamentoForm);
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/profissionais")
    public String cadastrarProfissional(
            @ModelAttribute CadastroProfissionalForm cadastroProfissionalForm,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio(session);
            Usuario novoProfissional = usuarioService.cadastrarProfissional(cadastroProfissionalForm, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Profissional cadastrado com sucesso: " + novoProfissional.getNome() + ".");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("cadastroProfissionalForm", cadastroProfissionalForm);
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio(session);
            service.cancelar(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Agendamento cancelado com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/{id}/encerrar-fixo")
    public String encerrarFixo(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio(session);
            service.encerrarSerieFixa(id, usuarioLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Horario fixo encerrado com sucesso para as proximas ocorrencias.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/sincronizar-fixos")
    public String sincronizarFixos(
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio(session);
            if (!authService.isAdmin(usuarioLogado)) {
                throw new RuntimeException("Somente a administracao pode carregar a agenda fixa.");
            }

            startupDataInitializer.sincronizarCargaInicialClinica();
            redirectAttributes.addFlashAttribute("sucesso", "Agenda fixa da planilha carregada com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/resetar-demo")
    public String resetarDemonstracao(
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio(session);
            if (!authService.isAdmin(usuarioLogado)) {
                throw new RuntimeException("Somente a administracao pode restaurar a demonstracao.");
            }

            Usuario adminRestaurado = startupDataInitializer.resetarBaseDemonstracao(usuarioLogado);
            authService.registrarSessao(session, adminRestaurado);
            redirectAttributes.addFlashAttribute("sucesso", "Demonstracao restaurada com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
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
