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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals(LocalDateTime.of(form.getDataAtendimento(), LocalTime.of(22, 0)), agendamento.getDataHoraFim());
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
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.cancelar(1L, profissional));

        assertEquals("Bloqueado: somente a administracao cancela com menos de 24h.", exception.getMessage());
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
