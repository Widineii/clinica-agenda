package com.clinica.sistema.service;

import com.clinica.sistema.dto.AgendaSalaLinha;
import com.clinica.sistema.dto.AgendaSalaView;
import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.SerieAgendamentoLinha;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SalaRepository salaRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AgendamentoService agendamentoService;

    private Usuario profissional;
    private Sala sala;

    @BeforeEach
    void setUp() {
        profissional = new Usuario();
        profissional.setId(10L);
        profissional.setNome("Julia");
        profissional.setCargo("ROLE_PROFISSIONAL");

        sala = new Sala();
        sala.setId(3L);
        sala.setNome("Sala 3");
    }

    @Test
    void deveSalvarAgendamentoValido() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(9, 0)));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals("Joao da Silva", agendamento.getNomeCliente());
        assertEquals(LocalDateTime.of(form.getDataAtendimento(), LocalTime.of(9, 0)), agendamento.getDataHoraInicio());
        assertEquals(LocalDateTime.of(form.getDataAtendimento(), LocalTime.of(10, 0)), agendamento.getDataHoraFim());
    }

    @Test
    void naoDevePermitirDomingo() {
        AgendamentoForm form = novoForm(proximoDomingo(LocalTime.of(9, 0)));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.salvar(form, profissional));

        assertEquals("A clinica funciona somente de segunda a sabado.", exception.getMessage());
        verify(agendamentoRepository, never()).save(any(Agendamento.class));
    }

    @Test
    void devePermitirUltimoHorarioDas21Horas() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(21, 0)));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        when(agendamentoRepository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
        when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals(LocalDateTime.of(form.getDataAtendimento(), LocalTime.of(22, 0)), agendamento.getDataHoraFim());
    }

    @Test
    void deveCriarAgendamentoFixoParaAsProximasSemanas() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(9, 0)));
        form.setRecorrencia("SEMANAL");

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        when(agendamentoRepository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
        when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals(Boolean.TRUE, agendamento.getFixo());
        verify(agendamentoRepository).saveAll(any());
        verify(agendamentoRepository, times(12)).existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class)
        );
    }

    @Test
    void deveCriarAgendamentoQuinzenal() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(19, 0)));
        form.setRecorrencia("QUINZENAL");

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        when(agendamentoRepository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
        when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals("QUINZENAL", agendamento.getRecorrencia());
        assertEquals(Boolean.TRUE, agendamento.getFixo());
        verify(agendamentoRepository, times(6)).existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class)
        );
    }

    @Test
    void naoDevePermitirProfissionalEmDuasSalasNoMesmoHorario() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(7, 0)));
        form.setSalaId(2L);

        Sala sala1 = new Sala();
        sala1.setId(1L);
        sala1.setNome("Sala 1");

        Agendamento existente = new Agendamento();
        existente.setProfissional(profissional);
        existente.setSala(sala1);
        existente.setDataHoraInicio(LocalDateTime.of(form.getDataAtendimento(), LocalTime.of(7, 0)));
        existente.setDataHoraFim(existente.getDataHoraInicio().plusHours(1));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(2L)).thenReturn(Optional.of(sala));
        when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.of(existente));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.salvar(form, profissional));

        assertEquals("Voce ja tem um agendamento nesse horario na Sala 1.", exception.getMessage());
        verify(agendamentoRepository, never()).save(any(Agendamento.class));
        verify(agendamentoRepository, never()).existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                ArgumentMatchers.anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)
        );
    }

    @Test
    void naoDevePermitirConflitoDeSala() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(9, 0)));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        when(agendamentoRepository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.salvar(form, profissional));

        assertEquals("Esta sala ja esta ocupada nesse horario.", exception.getMessage());
        verify(agendamentoRepository, never()).save(any(Agendamento.class));
    }

    @Test
    void profissionalNaoPodeCancelarDentroDasUltimas24Horas() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setProfissional(profissional);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusHours(10));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.cancelar(1L, profissional));

        assertEquals("Bloqueado: somente a administracao cancela com menos de 24h.", exception.getMessage());
    }

    @Test
    void donaClinicaPodeCancelarDentroDasUltimas24Horas() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setProfissional(polyana);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusHours(2));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        assertDoesNotThrow(() -> agendamentoService.cancelar(1L, polyana));
        verify(agendamentoRepository).deleteById(1L);
    }

    @Test
    void deveListarTodasOcorrenciasFuturasDaSerieFixaSemanal() {
        LocalDateTime primeiro = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime segundo = primeiro.plusWeeks(1);
        LocalDateTime terceiro = primeiro.plusWeeks(2);

        Agendamento ocorrencia1 = agendamentoSerie("semanal-serie-teste", primeiro);
        Agendamento ocorrencia2 = agendamentoSerie("semanal-serie-teste", segundo);
        Agendamento ocorrencia3 = agendamentoSerie("semanal-serie-teste", terceiro);

        List<Agendamento> lista = agendamentoService.listarProximasOcorrencias(
                List.of(ocorrencia1, ocorrencia2, ocorrencia3),
                Agendamento::isFixoSemanal
        );

        assertEquals(3, lista.size());
        assertEquals(primeiro, lista.get(0).getDataHoraInicio());
        assertEquals(terceiro, lista.get(2).getDataHoraInicio());
        assertEquals(3, agendamentoService.contarOcorrencias(List.of(ocorrencia1, ocorrencia2, ocorrencia3), Agendamento::isFixoSemanal));
    }

    @Test
    void devePreferirSalaComAgendamentosQuandoNenhumaSalaFoiInformada() {
        Sala sala1 = new Sala();
        sala1.setId(1L);
        sala1.setNome("Sala 1");

        Sala sala2 = new Sala();
        sala2.setId(2L);
        sala2.setNome("Sala 2");

        LocalDate sabado = LocalDate.of(2026, 5, 23);

        when(salaRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(sala1, sala2));

        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setSala(sala2);
        agendamento.setDataHoraInicio(LocalDateTime.of(2026, 5, 23, 17, 0));

        when(agendamentoRepository.findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(agendamento));

        Long salaResolvida = agendamentoService.resolverSalaIdParaGrade(null, sabado);

        assertEquals(2L, salaResolvida);
    }

    @Test
    void deveExibirAgendamentoNaGradeMesmoComSegundosNoHorario() {
        LocalDate sabado = LocalDate.of(2026, 5, 23);
        LocalDate inicioSemana = sabado.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        when(salaRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(sala));

        Agendamento agendamento = new Agendamento();
        agendamento.setId(99L);
        agendamento.setSala(sala);
        agendamento.setProfissional(profissional);
        agendamento.setNomeCliente("gdsr");
        agendamento.setDataHoraInicio(LocalDateTime.of(2026, 5, 23, 17, 0, 45));
        agendamento.setFixo(true);

        when(agendamentoRepository.findBySalaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                eq(sala.getId()),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(agendamento));

        AgendaSalaView view = agendamentoService.montarAgendaSala(sala.getId(), sabado);

        int indiceSabado = view.getDiasSemana().indexOf(sabado);
        AgendaSalaLinha linha17 = view.getLinhas().stream()
                .filter(linha -> linha.getHorario().equals(LocalTime.of(17, 0)))
                .findFirst()
                .orElseThrow();

        assertEquals(inicioSemana, view.getInicioSemana());
        assertNotNull(linha17.getAgendamentos().get(indiceSabado));
        assertEquals("gdsr", linha17.getAgendamentos().get(indiceSabado).getNomeCliente());
    }

    @Test
    void deveListarAgendamentosDoDiaDoProfissional() {
        LocalDateTime hoje = LocalDate.now().atTime(10, 0);
        Agendamento agendamento = new Agendamento();
        agendamento.setId(70L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente("Cliente do dia");
        agendamento.setDataHoraInicio(hoje);

        when(agendamentoRepository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                eq(profissional.getId()),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(agendamento));

        List<Agendamento> lista = agendamentoService.listarAgendamentosDoDia(profissional, false);

        assertEquals(1, lista.size());
        assertEquals("Cliente do dia", lista.get(0).getNomeCliente());
    }

    @Test
    void adminPodeAbrirAcaoNaGradeDeAgendamentoDeOutroProfissional() {
        Usuario admin = new Usuario();
        admin.setId(1L);
        admin.setLogin("admin");
        admin.setCargo("ROLE_ADMIN");

        Usuario outro = new Usuario();
        outro.setId(20L);
        outro.setNome("Julia");
        outro.setCargo("ROLE_PROFISSIONAL");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(50L);
        agendamento.setProfissional(outro);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(3));
        agendamento.setFixo(false);

        AgendaSalaView agenda = new AgendaSalaView();
        agenda.setLinhas(List.of(new AgendaSalaLinha(LocalTime.of(9, 0), List.of(agendamento))));

        when(authService.isAdmin(admin)).thenReturn(true);

        Map<Long, String> acoes = agendamentoService.montarAcoesGradePorId(agenda, admin);

        assertTrue(acoes.containsKey(50L));
        assertEquals("AVULSO", acoes.get(50L));
    }

    @Test
    void profissionalNaoPodeAbrirAcaoNaGradeDeOutroProfissional() {
        Usuario julia = new Usuario();
        julia.setId(20L);
        julia.setCargo("ROLE_PROFISSIONAL");

        Usuario maria = new Usuario();
        maria.setId(10L);
        maria.setNome("Maria");
        maria.setCargo("ROLE_PROFISSIONAL");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(51L);
        agendamento.setProfissional(maria);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(3));

        AgendaSalaView agenda = new AgendaSalaView();
        agenda.setLinhas(List.of(new AgendaSalaLinha(LocalTime.of(10, 0), List.of(agendamento))));

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        Map<Long, String> acoes = agendamentoService.montarAcoesGradePorId(agenda, julia);

        assertFalse(acoes.containsKey(51L));
    }

    @Test
    void donaClinicaPodeAbrirAcaoNaGradeDeOutroProfissional() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Usuario julia = new Usuario();
        julia.setId(20L);
        julia.setNome("Julia");
        julia.setCargo("ROLE_PROFISSIONAL");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(52L);
        agendamento.setProfissional(julia);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(2));

        AgendaSalaView agenda = new AgendaSalaView();
        agenda.setLinhas(List.of(new AgendaSalaLinha(LocalTime.of(11, 0), List.of(agendamento))));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);

        Map<Long, String> acoes = agendamentoService.montarAcoesGradePorId(agenda, polyana);

        assertTrue(acoes.containsKey(52L));
    }

    @Test
    void donaClinicaPodeCancelarAgendamentoDeOutroProfissional() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Usuario julia = new Usuario();
        julia.setId(20L);
        julia.setCargo("ROLE_PROFISSIONAL");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(53L);
        agendamento.setProfissional(julia);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(2));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(agendamentoRepository.findById(53L)).thenReturn(Optional.of(agendamento));

        assertDoesNotThrow(() -> agendamentoService.cancelar(53L, polyana));
        verify(agendamentoRepository).deleteById(53L);
    }

    @Test
    void deveEncerrarSerieFixaFutura() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(2L);
        agendamento.setProfissional(profissional);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(7));
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("serie-1");

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(agendamentoRepository.findById(2L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.findBySerieFixaIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
                "serie-1",
                agendamento.getDataHoraInicio()
        )).thenReturn(Collections.singletonList(agendamento));

        assertDoesNotThrow(() -> agendamentoService.encerrarSerieFixa(2L, profissional));

        verify(agendamentoRepository).deleteAll(any());
    }

    private Agendamento agendamentoSerie(String serieFixaId, LocalDateTime inicio) {
        Agendamento agendamento = new Agendamento();
        agendamento.setFixo(true);
        agendamento.setSerieFixaId(serieFixaId);
        agendamento.setTipoRecorrencia(serieFixaId.contains("quinzenal") ? "QUINZENAL" : "SEMANAL");
        agendamento.setDataHoraInicio(inicio);
        agendamento.setDataHoraFim(inicio.plusHours(1));
        return agendamento;
    }

    @Test
    void deveLimparAgendamentosSomenteDoMesPassado() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        LocalDateTime inicio = mesPassado.atDay(1).atStartOfDay();
        LocalDateTime fim = YearMonth.now().atDay(1).atStartOfDay();

        when(agendamentoRepository.deleteAvulsosByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(inicio, fim))
                .thenReturn(42);

        long removidos = agendamentoService.limparAgendamentosDoMesPassado();

        assertEquals(42L, removidos);
        verify(agendamentoRepository).deleteAvulsosByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(inicio, fim);
    }

    @Test
    void deveMontarRelatorioMensalUsoSalasPorProfissional() {
        YearMonth maio = YearMonth.of(2026, 5);
        LocalDateTime inicio = maio.atDay(1).atStartOfDay();
        LocalDateTime fim = maio.plusMonths(1).atDay(1).atStartOfDay();

        when(agendamentoRepository.contarUsoSalasPorProfissionalNoPeriodo(inicio, fim))
                .thenReturn(List.of(
                        new Object[]{"Carol", "Sala 1", 10L},
                        new Object[]{"Julia", "Sala 1", 7L},
                        new Object[]{"Julia", "Sala 2", 9L}
                ));

        RelatorioMensalUsoSalasView relatorio = agendamentoService.montarRelatorioMensalUsoSalas(maio);

        assertEquals(26L, relatorio.getTotalGeral());
        assertEquals(2, relatorio.getProfissionais().size());
        assertEquals("Carol", relatorio.getProfissionais().get(0).getProfissionalNome());
        assertEquals(10L, relatorio.getProfissionais().get(0).getSalas().get(0).getQuantidade());
        assertEquals("Julia", relatorio.getProfissionais().get(1).getProfissionalNome());
        assertEquals(16L, relatorio.getProfissionais().get(1).getTotalHorarios());
        assertEquals(2, relatorio.getProfissionais().get(1).getSalas().size());
    }

    @Test
    void relatorioSemanalConsultaApenasHorariosAposRegra24h() {
        LocalDate inicio = LocalDate.of(2026, 5, 25);
        LocalDate fim = LocalDate.of(2026, 5, 28);
        LocalDateTime inicioDataHora = inicio.atStartOfDay();
        LocalDateTime fimDataHora = fim.plusDays(1).atStartOfDay();

        when(agendamentoRepository.contarUsoSalasPorProfissionalNoPeriodoAposRegra24h(
                eq(inicioDataHora),
                eq(fimDataHora),
                any(LocalDateTime.class)
        )).thenReturn(Collections.singletonList(new Object[]{"Julia", "Sala 1", 3L}));

        RelatorioMensalUsoSalasView relatorio = agendamentoService.montarRelatorioUsoSalasNoPeriodoAposRegra24h(
                inicio,
                fim,
                "Semana"
        );

        assertEquals(3L, relatorio.getTotalGeral());
        verify(agendamentoRepository).contarUsoSalasPorProfissionalNoPeriodoAposRegra24h(
                eq(inicioDataHora),
                eq(fimDataHora),
                any(LocalDateTime.class)
        );
        verify(agendamentoRepository, never()).contarUsoSalasPorProfissionalNoPeriodo(any(), any());
    }

    @Test
    void deveSepararSeriesSemanalEQuinzenal() {
        Agendamento semanal = agendamentoSerie("semanal-1", LocalDateTime.now().plusDays(3));
        Agendamento quinzenal = agendamentoSerie("quinzenal-1", LocalDateTime.now().plusDays(4));

        assertEquals(true, semanal.isFixoSemanal());
        assertEquals(false, semanal.isQuinzenal());
        assertEquals(false, quinzenal.isFixoSemanal());
        assertEquals(true, quinzenal.isQuinzenal());
    }

    @Test
    void deveEstenderSerieFixaAtivaAteHorizonteDeDozeSemanas() {
        String serieId = "semanal-renovacao";
        LocalDateTime ultimoHorario = LocalDateTime.now().plusWeeks(2);
        Agendamento ultimo = agendamentoSerie(serieId, ultimoHorario);
        ultimo.setNomeCliente("Cliente fixo");
        ultimo.setSala(sala);
        ultimo.setProfissional(profissional);

        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of(serieId));
        when(agendamentoRepository.countBySerieFixaIdAndDataHoraInicioGreaterThanEqual(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(2L);
        when(agendamentoRepository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieId))
                .thenReturn(Optional.of(ultimo));
        when(agendamentoRepository.existsBySerieFixaIdAndDataHoraInicio(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(false);
        when(agendamentoRepository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
        when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> agendamentoService.renovarSeriesRecorrentesAtivas());

        verify(agendamentoRepository).saveAll(any());
    }

    @Test
    void deveAdicionarProximaDataQuandoSerieFixaFicaComMenosDeDozeFuturas() {
        String serieId = "semanal-rolagem";
        LocalDateTime ultimoHorario = LocalDateTime.now().plusWeeks(10);
        Agendamento ultimo = agendamentoSerie(serieId, ultimoHorario);
        ultimo.setId(50L);
        ultimo.setNomeCliente("Cliente");
        ultimo.setSala(sala);
        ultimo.setProfissional(profissional);

        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of(serieId));
        when(agendamentoRepository.countBySerieFixaIdAndDataHoraInicioGreaterThanEqual(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(11L);
        when(agendamentoRepository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieId))
                .thenReturn(Optional.of(ultimo));
        when(agendamentoRepository.existsBySerieFixaIdAndDataHoraInicio(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(false);
        when(agendamentoRepository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
        when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        agendamentoService.renovarSeriesRecorrentesAtivas();

        verify(agendamentoRepository).saveAll(any());
    }

    @Test
    void naoDeveRenovarSerieSemOcorrenciasFuturas() {
        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of());

        agendamentoService.renovarSeriesRecorrentesAtivas();

        verify(agendamentoRepository, never()).saveAll(any());
    }

    @Test
    void deveAgruparSeriesFixasPorClienteESala() {
        Agendamento bernardo = agendamentoSerie("semanal-bernardo", LocalDateTime.now().plusDays(2));
        bernardo.setNomeCliente("Bernardo");
        bernardo.setSala(sala);

        Agendamento pedro = agendamentoSerie("semanal-pedro", LocalDateTime.now().plusDays(3));
        pedro.setNomeCliente("Pedro");
        Sala sala2 = new Sala();
        sala2.setId(2L);
        sala2.setNome("Sala 2");
        pedro.setSala(sala2);

        List<SerieAgendamentoLinha> linhas = agendamentoService.agruparSeriesAtivas(
                List.of(bernardo, pedro),
                Agendamento::isFixoSemanal
        );

        assertEquals(2, linhas.size());
        assertTrue(linhas.stream().anyMatch(l -> l.getRotuloCabecalho().contains("Bernardo - Sala 3")));
        assertTrue(linhas.stream().anyMatch(l -> l.getRotuloCabecalho().contains("Pedro - Sala 2")));
    }

    @Test
    void deveListarProximasDatasDaMesmaSerieFixa() {
        LocalDateTime primeiro = LocalDateTime.now().plusWeeks(1);
        LocalDateTime segundo = primeiro.plusWeeks(1);
        LocalDateTime terceiro = primeiro.plusWeeks(2);

        Agendamento ocorrencia1 = agendamentoSerie("semanal-bernardo", primeiro);
        ocorrencia1.setId(101L);
        ocorrencia1.setNomeCliente("Bernardo");
        ocorrencia1.setSala(sala);
        Agendamento ocorrencia2 = agendamentoSerie("semanal-bernardo", segundo);
        ocorrencia2.setId(102L);
        ocorrencia2.setNomeCliente("Bernardo");
        ocorrencia2.setSala(sala);
        Agendamento ocorrencia3 = agendamentoSerie("semanal-bernardo", terceiro);
        ocorrencia3.setId(103L);
        ocorrencia3.setNomeCliente("Bernardo");
        ocorrencia3.setSala(sala);

        List<SerieAgendamentoLinha> linhas = agendamentoService.agruparSeriesAtivas(
                List.of(ocorrencia1, ocorrencia2, ocorrencia3),
                Agendamento::isFixoSemanal
        );

        assertEquals(1, linhas.size());
        assertEquals(3, linhas.get(0).getProximasOcorrencias().size());
        assertEquals(
                primeiro.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")),
                linhas.get(0).getProximasOcorrencias().get(0).getDataRotulo()
        );
        assertEquals(101L, linhas.get(0).getProximasOcorrencias().get(0).getAgendamentoId());
    }

    @Test
    void deveLimitarQuinzenalASeisDatasMesmoSemTipoRecorrenciaNoBanco() {
        LocalDateTime primeiro = LocalDateTime.now().plusWeeks(1);
        List<Agendamento> ocorrencias = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Agendamento o = agendamentoSerie("quinzenal-rolagem", primeiro.plusWeeks(2L * i));
            o.setTipoRecorrencia(null);
            o.setId(300L + i);
            o.setNomeCliente("Cliente quinzenal");
            o.setSala(sala);
            ocorrencias.add(o);
        }

        List<SerieAgendamentoLinha> linhas = agendamentoService.agruparSeriesAtivas(
                ocorrencias,
                Agendamento::isQuinzenal
        );

        assertEquals(1, linhas.size());
        assertEquals(6, linhas.get(0).getProximasOcorrencias().size());
    }

    @Test
    void deveLimitarQuinzenalASeisDatasNaListagem() {
        LocalDateTime primeiro = LocalDateTime.now().plusWeeks(1);
        List<Agendamento> ocorrencias = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Agendamento o = agendamentoSerie("quinzenal-rolagem", primeiro.plusWeeks(2L * i));
            o.setId(200L + i);
            o.setNomeCliente("Cliente quinzenal");
            o.setSala(sala);
            ocorrencias.add(o);
        }

        List<SerieAgendamentoLinha> linhas = agendamentoService.agruparSeriesAtivas(
                ocorrencias,
                Agendamento::isQuinzenal
        );

        assertEquals(1, linhas.size());
        assertEquals(6, linhas.get(0).getProximasOcorrencias().size());
    }

    @Test
    void deveAdicionarProximaDataQuinzenalQuandoFicaComMenosDeSeisFuturas() {
        String serieId = "quinzenal-renovacao";
        LocalDateTime ultimoHorario = LocalDateTime.now().plusWeeks(8);
        Agendamento ultimo = agendamentoSerie(serieId, ultimoHorario);
        ultimo.setId(60L);
        ultimo.setNomeCliente("Cliente");
        ultimo.setSala(sala);
        ultimo.setProfissional(profissional);

        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of(serieId));
        when(agendamentoRepository.countBySerieFixaIdAndDataHoraInicioGreaterThanEqual(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(agendamentoRepository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieId))
                .thenReturn(Optional.of(ultimo));
        when(agendamentoRepository.existsBySerieFixaIdAndDataHoraInicio(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(false);
        when(agendamentoRepository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
        when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        agendamentoService.renovarSeriesRecorrentesAtivas();

        verify(agendamentoRepository).saveAll(any());
    }

    @Test
    void naoDeveAdicionarQuinzenalQuandoJaTemSeisFuturas() {
        String serieId = "quinzenal-cheio";
        LocalDateTime ultimoHorario = LocalDateTime.now().plusWeeks(10);
        Agendamento ultimo = agendamentoSerie(serieId, ultimoHorario);
        ultimo.setSala(sala);
        ultimo.setProfissional(profissional);

        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of(serieId));
        when(agendamentoRepository.countBySerieFixaIdAndDataHoraInicioGreaterThanEqual(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(6L);
        when(agendamentoRepository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieId))
                .thenReturn(Optional.of(ultimo));

        agendamentoService.renovarSeriesRecorrentesAtivas();

        verify(agendamentoRepository, never()).saveAll(any());
    }

    private AgendamentoForm novoForm(LocalDateTime dataHoraInicio) {
        AgendamentoForm form = new AgendamentoForm();
        form.setProfissionalId(profissional.getId());
        form.setSalaId(sala.getId());
        form.setNomeCliente("Joao da Silva");
        form.setDataAtendimento(dataHoraInicio.toLocalDate());
        form.setHorarioAtendimento(dataHoraInicio.toLocalTime());
        return form;
    }

    private LocalDateTime proximaDataUtil(LocalTime horario) {
        LocalDate data = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return LocalDateTime.of(data, horario);
    }

    private LocalDateTime proximoDomingo(LocalTime horario) {
        LocalDate data = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        return LocalDateTime.of(data, horario);
    }
}
