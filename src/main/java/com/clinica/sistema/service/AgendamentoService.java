package com.clinica.sistema.service;

import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.AgendaSalaLinha;
import com.clinica.sistema.dto.AgendaSalaView;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelatorioUsoSalaItem;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class AgendamentoService {
    private static final LocalTime HORA_ABERTURA = LocalTime.of(7, 0);
    private static final LocalTime HORA_FECHAMENTO = LocalTime.of(22, 0);
    private static final int SEMANAS_FIXAS_PADRAO = 12;
    private static final String RECORRENCIA_AVULSO = "AVULSO";
    private static final String RECORRENCIA_SEMANAL = "SEMANAL";
    private static final String RECORRENCIA_QUINZENAL = "QUINZENAL";

    private final AgendamentoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final SalaRepository salaRepository;
    private final AuthService authService;

    public AgendamentoService(
            AgendamentoRepository repository,
            UsuarioRepository usuarioRepository,
            SalaRepository salaRepository,
            AuthService authService
    ) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.salaRepository = salaRepository;
        this.authService = authService;
    }

    public List<Agendamento> buscarTodos() {
        return repository.findAllByOrderByDataHoraInicioAsc();
    }

    public List<Agendamento> buscarParaUsuario(Usuario usuarioLogado) {
        return repository.findByProfissionalIdOrderByDataHoraInicioAsc(usuarioLogado.getId());
    }

    public List<Agendamento> listarProximosPorSerie(List<Agendamento> agendamentos, Predicate<Agendamento> filtro) {
        LocalDateTime limite = LocalDateTime.now().minusDays(1);
        Map<String, Agendamento> proximoPorSerie = new LinkedHashMap<>();

        agendamentos.stream()
                .filter(filtro)
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(limite))
                .sorted(Comparator.comparing(Agendamento::getDataHoraInicio))
                .forEach(agendamento -> proximoPorSerie.putIfAbsent(chaveSerie(agendamento), agendamento));

        return proximoPorSerie.values().stream()
                .sorted(Comparator.comparing(Agendamento::getDataHoraInicio))
                .limit(24)
                .toList();
    }

    public long contarSeries(List<Agendamento> agendamentos, Predicate<Agendamento> filtro) {
        return listarProximosPorSerie(agendamentos, filtro).size();
    }

    public List<Agendamento> listarProximasOcorrencias(List<Agendamento> agendamentos, Predicate<Agendamento> filtro) {
        LocalDateTime limite = LocalDateTime.now().minusDays(1);
        return agendamentos.stream()
                .filter(filtro)
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(limite))
                .sorted(Comparator.comparing(Agendamento::getDataHoraInicio))
                .limit(48)
                .toList();
    }

    public long contarOcorrencias(List<Agendamento> agendamentos, Predicate<Agendamento> filtro) {
        LocalDateTime limite = LocalDateTime.now().minusDays(1);
        return agendamentos.stream()
                .filter(filtro)
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(limite))
                .count();
    }

    public List<Sala> listarSalas() {
        return salaRepository.findAllByOrderByNomeAsc();
    }

    public List<Usuario> listarProfissionais() {
        return usuarioRepository.findAll().stream()
                .filter(this::podeAtender)
                .sorted(Comparator.comparing(Usuario::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<LocalTime> listarHorariosDisponiveis() {
        List<LocalTime> horarios = new ArrayList<>();
        for (LocalTime horario = HORA_ABERTURA; !horario.equals(HORA_FECHAMENTO); horario = horario.plusHours(1)) {
            horarios.add(horario);
        }
        return horarios;
    }

    public AgendaSalaView montarAgendaSala(Long salaId, LocalDate referencia) {
        Sala sala = buscarSalaPadrao(salaId);
        LocalDate inicioSemana = obterInicioSemana(referencia);
        LocalDate fimSemana = inicioSemana.plusDays(5);

        LocalDateTime inicioConsulta = inicioSemana.atTime(HORA_ABERTURA);
        LocalDateTime fimConsulta = fimSemana.plusDays(1).atStartOfDay();

        List<Agendamento> agendamentosSemana =
                repository.findBySalaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                        sala.getId(),
                        inicioConsulta,
                        fimConsulta
                );

        Map<LocalDateTime, Agendamento> agendaPorHorario = agendamentosSemana.stream()
                .collect(Collectors.toMap(Agendamento::getDataHoraInicio, agendamento -> agendamento));

        List<LocalDate> diasSemana = inicioSemana.datesUntil(fimSemana.plusDays(1)).toList();
        List<AgendaSalaLinha> linhas = new ArrayList<>();

        for (LocalTime horario = HORA_ABERTURA; horario.isBefore(HORA_FECHAMENTO); horario = horario.plusHours(1)) {
            List<Agendamento> porDia = new ArrayList<>();
            for (LocalDate dia : diasSemana) {
                porDia.add(agendaPorHorario.get(LocalDateTime.of(dia, horario)));
            }
            linhas.add(new AgendaSalaLinha(horario, porDia));
        }

        AgendaSalaView view = new AgendaSalaView();
        view.setSala(sala);
        view.setInicioSemana(inicioSemana);
        view.setDiasSemana(diasSemana);
        view.setLinhas(linhas);
        return view;
    }

    @Transactional
    public long limparAgendamentosDoMesPassado() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        LocalDateTime inicio = mesPassado.atDay(1).atStartOfDay();
        LocalDateTime fim = YearMonth.now().atDay(1).atStartOfDay();
        return limparAgendamentosNoPeriodo(inicio, fim);
    }

    public long contarAgendamentosDoMesPassado() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        LocalDateTime inicio = mesPassado.atDay(1).atStartOfDay();
        LocalDateTime fim = YearMonth.now().atDay(1).atStartOfDay();
        return repository.countByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(inicio, fim);
    }

    /**
     * Remove somente agendamentos avulsos do periodo.
     * Semanal e quinzenal permanecem ate cancelamento ou encerramento da serie.
     */
    @Transactional
    public long limparAgendamentosNoPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (!inicio.isBefore(fim)) {
            throw new RuntimeException("Periodo invalido para limpeza.");
        }
        return repository.deleteAvulsosByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(inicio, fim);
    }

    public RelatorioMensalUsoSalasView montarRelatorioMensalUsoSalas(YearMonth mesReferencia) {
        LocalDateTime inicio = mesReferencia.atDay(1).atStartOfDay();
        LocalDateTime fim = mesReferencia.plusMonths(1).atDay(1).atStartOfDay();

        List<Object[]> linhas = repository.contarUsoSalasPorProfissionalNoPeriodo(inicio, fim);
        Map<String, RelatorioUsoSalaProfissional> porProfissional = new LinkedHashMap<>();
        long totalGeral = 0;

        for (Object[] linha : linhas) {
            String profissionalNome = (String) linha[0];
            String salaNome = (String) linha[1];
            long quantidade = linha[2] instanceof Number numero ? numero.longValue() : 0L;

            RelatorioUsoSalaProfissional bloco = porProfissional.computeIfAbsent(
                    profissionalNome,
                    nome -> {
                        RelatorioUsoSalaProfissional novo = new RelatorioUsoSalaProfissional();
                        novo.setProfissionalNome(nome);
                        return novo;
                    }
            );

            RelatorioUsoSalaItem item = new RelatorioUsoSalaItem();
            item.setSalaNome(salaNome);
            item.setQuantidade(quantidade);
            bloco.getSalas().add(item);
            bloco.setTotalHorarios(bloco.getTotalHorarios() + quantidade);
            totalGeral += quantidade;
        }

        RelatorioMensalUsoSalasView relatorio = new RelatorioMensalUsoSalasView();
        relatorio.setMesReferencia(mesReferencia);
        relatorio.setMesReferenciaLabel(formatarMesReferencia(mesReferencia));
        relatorio.setProfissionais(new ArrayList<>(porProfissional.values()));
        relatorio.setTotalGeral(totalGeral);
        return relatorio;
    }

    public List<Agendamento> listarAgendamentosDoDia(Usuario usuarioLogado, boolean isAdmin) {
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicio = hoje.atStartOfDay();
        LocalDateTime fim = hoje.plusDays(1).atStartOfDay();

        if (isAdmin) {
            return repository.findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                    inicio,
                    fim
            );
        }

        return repository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                usuarioLogado.getId(),
                inicio,
                fim
        );
    }

    @Transactional
    public Agendamento salvar(AgendamentoForm form, Usuario usuarioLogado) {
        validarFormulario(form);

        Usuario profissional = carregarProfissional(form.getProfissionalId(), usuarioLogado);
        Sala sala = salaRepository.findById(form.getSalaId())
                .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));

        LocalDateTime inicio = LocalDateTime.of(
                form.getDataAtendimento(),
                form.getHorarioAtendimento().withMinute(0).withSecond(0).withNano(0)
        );
        LocalDateTime fim = inicio.plusHours(1);

        validarHorario(inicio, fim);

        List<Agendamento> novosAgendamentos = new ArrayList<>();
        String recorrencia = normalizarRecorrencia(form);
        int saltoSemanas = obterSaltoSemanas(recorrencia);
        int repeticoes = obterQuantidadeRepeticoes(recorrencia);
        String serieFixaId = RECORRENCIA_AVULSO.equals(recorrencia)
                ? null
                : recorrencia.toLowerCase() + "-" + UUID.randomUUID();

        for (int semana = 0; semana < repeticoes; semana++) {
            LocalDateTime inicioSemana = inicio.plusWeeks((long) semana * saltoSemanas);
            LocalDateTime fimSemana = fim.plusWeeks((long) semana * saltoSemanas);

            validarConflitos(
                    sala,
                    profissional,
                    usuarioLogado,
                    inicioSemana,
                    fimSemana,
                    !RECORRENCIA_AVULSO.equals(recorrencia),
                    semana
            );

            Agendamento novo = new Agendamento();
            novo.setProfissional(profissional);
            novo.setSala(sala);
            novo.setNomeCliente(form.getNomeCliente().trim());
            novo.setDataHoraInicio(inicioSemana);
            novo.setDataHoraFim(fimSemana);
            novo.setFixo(!RECORRENCIA_AVULSO.equals(recorrencia));
            novo.setSerieFixaId(serieFixaId);
            novo.setTipoRecorrencia(recorrencia);
            novo.setRecorrencia(recorrencia);
            novosAgendamentos.add(novo);
        }

        repository.saveAll(novosAgendamentos);
        return novosAgendamentos.get(0);
    }

    public boolean isAgendamentoDoUsuario(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null || agendamento.getProfissional() == null) {
            return false;
        }
        return agendamento.getProfissional().getId().equals(usuarioLogado.getId());
    }

    public boolean podeCancelarAgendamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (agendamento.getDataHoraInicio() == null
                || !agendamento.getDataHoraInicio().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (podeGerenciarAgendamentoDeOutros(usuarioLogado)) {
            return agendamento.getProfissional() != null;
        }
        if (!isAgendamentoDoUsuario(agendamento, usuarioLogado)) {
            return false;
        }
        try {
            validarPermissaoSobreAgendamento(agendamento, usuarioLogado);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public String tipoAcaoGrade(Agendamento agendamento) {
        if (agendamento.isQuinzenal()) {
            return "QUINZENAL";
        }
        if (agendamento.isFixoSemanal()) {
            return "SEMANAL";
        }
        return "AVULSO";
    }

    /**
     * IDs dos agendamentos na grade em que o usuario logado pode abrir o popup (duplo clique).
     * Valor = tipo (AVULSO, SEMANAL, QUINZENAL).
     */
    public Map<Long, String> montarAcoesGradePorId(AgendaSalaView agendaSala, Usuario usuarioLogado) {
        Map<Long, String> acoes = new LinkedHashMap<>();
        if (agendaSala == null || agendaSala.getLinhas() == null) {
            return acoes;
        }
        for (AgendaSalaLinha linha : agendaSala.getLinhas()) {
            if (linha.getAgendamentos() == null) {
                continue;
            }
            for (Agendamento agendamento : linha.getAgendamentos()) {
                if (podeCancelarAgendamento(agendamento, usuarioLogado)) {
                    acoes.put(agendamento.getId(), tipoAcaoGrade(agendamento));
                }
            }
        }
        return acoes;
    }

    @Transactional
    public void cancelar(Long id, Usuario usuarioLogado) {
        Agendamento agendamento = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        validarPermissaoSobreAgendamento(agendamento, usuarioLogado);
        repository.deleteById(id);
    }

    @Transactional
    public void encerrarSerieFixa(Long id, Usuario usuarioLogado) {
        Agendamento agendamento = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));

        if (!Boolean.TRUE.equals(agendamento.getFixo()) || agendamento.getSerieFixaId() == null || agendamento.getSerieFixaId().isBlank()) {
            throw new RuntimeException("Este agendamento nao pertence a uma serie fixa.");
        }

        validarPermissaoSobreAgendamento(agendamento, usuarioLogado);

        List<Agendamento> futurosDaSerie = repository.findBySerieFixaIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
                agendamento.getSerieFixaId(),
                agendamento.getDataHoraInicio()
        );

        repository.deleteAll(futurosDaSerie);
    }

    private void validarFormulario(AgendamentoForm form) {
        if (form.getProfissionalId() == null) {
            throw new RuntimeException("Selecione o profissional.");
        }
        if (form.getSalaId() == null) {
            throw new RuntimeException("Selecione a sala.");
        }
        if (form.getDataAtendimento() == null) {
            throw new RuntimeException("Informe a data da consulta.");
        }
        if (form.getHorarioAtendimento() == null) {
            throw new RuntimeException("Selecione um horario fixo.");
        }
        if (normalizarRecorrencia(form) == null) {
            throw new RuntimeException("Selecione um tipo de recorrencia valido.");
        }
        if (form.getNomeCliente() == null || form.getNomeCliente().isBlank()) {
            throw new RuntimeException("Informe o nome do cliente.");
        }
    }

    private String normalizarRecorrencia(AgendamentoForm form) {
        if (form.getRecorrencia() != null && !form.getRecorrencia().isBlank()) {
            return switch (form.getRecorrencia().toUpperCase()) {
                case RECORRENCIA_AVULSO, RECORRENCIA_SEMANAL, RECORRENCIA_QUINZENAL -> form.getRecorrencia().toUpperCase();
                default -> null;
            };
        }

        return form.isFixo() ? RECORRENCIA_SEMANAL : RECORRENCIA_AVULSO;
    }

    private int obterSaltoSemanas(String recorrencia) {
        if (RECORRENCIA_QUINZENAL.equals(recorrencia)) {
            return 2;
        }
        return 1;
    }

    private int obterQuantidadeRepeticoes(String recorrencia) {
        if (RECORRENCIA_AVULSO.equals(recorrencia)) {
            return 1;
        }
        if (RECORRENCIA_QUINZENAL.equals(recorrencia)) {
            return 6;
        }
        return SEMANAS_FIXAS_PADRAO;
    }

    private boolean podeGerenciarAgendamentoDeOutros(Usuario usuarioLogado) {
        return authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado);
    }

    private void validarPermissaoSobreAgendamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (podeGerenciarAgendamentoDeOutros(usuarioLogado)) {
            return;
        }

        if (!agendamento.getProfissional().getId().equals(usuarioLogado.getId())) {
            throw new RuntimeException("Voce so pode alterar os seus proprios agendamentos.");
        }

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime limiteParaCancelar = agendamento.getDataHoraInicio().minusHours(24);
        if (agora.isAfter(limiteParaCancelar)) {
            throw new RuntimeException("Bloqueado: somente a administracao cancela com menos de 24h.");
        }
    }

    private void validarConflitos(
            Sala sala,
            Usuario profissional,
            Usuario usuarioLogado,
            LocalDateTime inicio,
            LocalDateTime fim,
            boolean fixo,
            int indiceSemana
    ) {
        repository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                        profissional.getId(),
                        fim,
                        inicio
                )
                .ifPresent(conflito -> {
                    throw conflitoMensagem(
                            mensagemConflitoProfissional(profissional, conflito, usuarioLogado),
                            inicio,
                            fixo,
                            indiceSemana
                    );
                });

        boolean salaOcupada = repository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                sala.getId(),
                fim,
                inicio
        );
        if (salaOcupada) {
            throw conflitoMensagem("Esta sala ja esta ocupada nesse horario.", inicio, fixo, indiceSemana);
        }
    }

    private String mensagemConflitoProfissional(Usuario profissional, Agendamento conflito, Usuario usuarioLogado) {
        String salaConflito = conflito.getSala() != null && conflito.getSala().getNome() != null
                ? conflito.getSala().getNome()
                : "outra sala";

        boolean agendandoParaSiMesmo = usuarioLogado != null
                && profissional.getId() != null
                && profissional.getId().equals(usuarioLogado.getId());

        if (agendandoParaSiMesmo) {
            return "Voce ja tem um agendamento nesse horario na " + salaConflito + ".";
        }

        String nomeProfissional = profissional.getNome() != null && !profissional.getNome().isBlank()
                ? profissional.getNome()
                : "Este profissional";
        return nomeProfissional + " ja tem um agendamento nesse horario na " + salaConflito + ".";
    }

    private RuntimeException conflitoMensagem(String mensagemBase, LocalDateTime inicio, boolean fixo, int indiceSemana) {
        if (!fixo || indiceSemana == 0) {
            return new RuntimeException(mensagemBase);
        }
        return new RuntimeException(
                mensagemBase + " Conflito encontrado na repeticao da semana de "
                        + inicio.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "."
        );
    }

    private Usuario carregarProfissional(Long profissionalId, Usuario usuarioLogado) {
        if (!authService.isAdmin(usuarioLogado) && !usuarioLogado.getId().equals(profissionalId)) {
            throw new RuntimeException("Voce so pode agendar para o seu proprio usuario.");
        }

        Usuario profissional = usuarioRepository.findById(profissionalId)
                .orElseThrow(() -> new RuntimeException("Profissional nao encontrado."));

        if (!podeAtender(profissional)) {
            throw new RuntimeException("O usuario selecionado nao e um profissional.");
        }

        return profissional;
    }

    private boolean podeAtender(Usuario usuario) {
        return "ROLE_PROFISSIONAL".equals(usuario.getCargo())
                || "ROLE_ADMIN".equals(usuario.getCargo());
    }

    private String formatarMesReferencia(YearMonth mesReferencia) {
        String mes = mesReferencia.getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, new Locale("pt", "BR"));
        if (mes != null && !mes.isBlank()) {
            mes = Character.toUpperCase(mes.charAt(0)) + mes.substring(1);
        }
        return mes + " de " + mesReferencia.getYear();
    }

    private void validarHorario(LocalDateTime inicio, LocalDateTime fim) {
        DayOfWeek diaSemana = inicio.getDayOfWeek();
        if (diaSemana == DayOfWeek.SUNDAY) {
            throw new RuntimeException("A clinica funciona somente de segunda a sabado.");
        }

        LocalDate data = inicio.toLocalDate();
        if (!fim.toLocalDate().equals(data)) {
            throw new RuntimeException("Cada consulta deve terminar no mesmo dia.");
        }

        if (inicio.getMinute() != 0 || inicio.getSecond() != 0 || inicio.getNano() != 0) {
            throw new RuntimeException("Os agendamentos precisam iniciar em hora cheia.");
        }

        if (inicio.toLocalTime().isBefore(HORA_ABERTURA) || fim.toLocalTime().isAfter(HORA_FECHAMENTO)) {
            throw new RuntimeException("Os atendimentos devem ficar entre 07:00 e 21:00.");
        }
    }

    private Sala buscarSalaPadrao(Long salaId) {
        List<Sala> salas = listarSalas();
        if (salas.isEmpty()) {
            throw new RuntimeException("Nenhuma sala cadastrada.");
        }

        Long salaSelecionadaId = salaId != null ? salaId : salas.get(0).getId();
        return salas.stream()
                .filter(item -> item.getId().equals(salaSelecionadaId))
                .findFirst()
                .orElse(salas.get(0));
    }

    private String chaveSerie(Agendamento agendamento) {
        if (agendamento.getSerieFixaId() != null && !agendamento.getSerieFixaId().isBlank()) {
            return agendamento.getSerieFixaId();
        }
        return "avulso-" + agendamento.getId();
    }

    private LocalDate obterInicioSemana(LocalDate referencia) {
        LocalDate base;
        if (referencia != null) {
            base = referencia;
        } else {
            LocalDate hoje = LocalDate.now();
            base = hoje.getDayOfWeek() == DayOfWeek.FRIDAY ? hoje.plusWeeks(1) : hoje;
        }
        return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
