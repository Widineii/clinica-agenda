package com.clinica.sistema.service;

import com.clinica.sistema.dto.AgendamentoForm;
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
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        profissional.setNome("Maria");
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
        when(agendamentoRepository.existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
        when(agendamentoRepository.existsByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
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
        when(agendamentoRepository.existsByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
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
        when(agendamentoRepository.existsByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
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
        when(agendamentoRepository.existsByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(false);
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals("QUINZENAL", agendamento.getRecorrencia());
        assertEquals(Boolean.TRUE, agendamento.getFixo());
        verify(agendamentoRepository, times(6)).existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class)
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
    void deveSepararSeriesSemanalEQuinzenal() {
        Agendamento semanal = agendamentoSerie("semanal-1", LocalDateTime.now().plusDays(3));
        Agendamento quinzenal = agendamentoSerie("quinzenal-1", LocalDateTime.now().plusDays(4));

        assertEquals(true, semanal.isFixoSemanal());
        assertEquals(false, semanal.isQuinzenal());
        assertEquals(false, quinzenal.isFixoSemanal());
        assertEquals(true, quinzenal.isQuinzenal());
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
