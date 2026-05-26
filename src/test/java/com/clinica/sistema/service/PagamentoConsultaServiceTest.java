package com.clinica.sistema.service;

import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagamentoConsultaServiceTest {

    @Mock
    private com.clinica.sistema.repository.AgendamentoRepository repository;

    @Mock
    private InfinitePayService infinitePayService;

    @Mock
    private AuthService authService;

    @Mock
    private PagamentoProperties pagamentoProperties;

    @InjectMocks
    private PagamentoConsultaService pagamentoConsultaService;

    private Agendamento agendamento;

    @BeforeEach
    void setUp() {
        agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setValorClinicaCobra(new BigDecimal("32.00"));
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(3).atTime(10, 0));
    }

    @Test
    void deveAbrirConfirmacaoImediataNaPrimeiraConsultaDaSerie() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        when(pagamentoProperties.getPrazoConfirmacaoMinutos()).thenReturn(5);
        when(infinitePayService.gerarLinkPagamento(any())).thenReturn(
                new com.clinica.sistema.dto.LinkPagamentoGerado("ag-1-test", "http://localhost/link", "slug")
        );
        when(infinitePayService.valorPagamento(any())).thenReturn(new BigDecimal("32.00"));
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento), profissional);

        assertEquals(PagamentoStatus.ESPERANDO_CONFIRMACAO, agendamento.getStatusPagamento());
        assertEquals("http://localhost/link", agendamento.getPagamentoLink());
        assertNotNull(agendamento.getPagamentoExpiraEm());
        assertTrue(agendamento.getPagamentoExpiraEm().isAfter(LocalDateTime.now()));
    }

    @Test
    void consultasFuturasDaSerieFicamComPagamentoFuturo() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        when(pagamentoProperties.getPrazoConfirmacaoMinutos()).thenReturn(5);
        Agendamento segunda = new Agendamento();
        segunda.setId(2L);
        segunda.setDataHoraInicio(LocalDate.now().plusDays(10).atTime(10, 0));

        when(infinitePayService.gerarLinkPagamento(any())).thenReturn(
                new com.clinica.sistema.dto.LinkPagamentoGerado("ag-1-test", "http://localhost/link", "slug")
        );
        when(infinitePayService.valorPagamento(any())).thenReturn(new BigDecimal("32.00"));
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento, segunda), profissional);

        assertEquals(PagamentoStatus.ESPERANDO_CONFIRMACAO, agendamento.getStatusPagamento());
        assertEquals(PagamentoStatus.PAGAMENTO_FUTURO, segunda.getStatusPagamento());
    }

    @Test
    void deveAbrirJanelaUmDiaAntes() {
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(9, 0));
        assertTrue(pagamentoConsultaService.deveAbrirPagamentoAgora(agendamento));

        agendamento.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(9, 0));
        assertFalse(pagamentoConsultaService.deveAbrirPagamentoAgora(agendamento));
    }

    @Test
    void donaClinicaNaoPrecisaConfirmarPagamento() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);
        agendamento.setProfissional(polyana);
        when(authService.profissionalIgnoraValoresEPagamento(polyana)).thenReturn(true);

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento), polyana);

        assertEquals(PagamentoStatus.PAGO, agendamento.getStatusPagamento());
    }

    @Test
    void bloqueiaSalaNoDiaSemPagamento() {
        agendamento.setDataHoraInicio(LocalDate.now().atTime(20, 0));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        assertTrue(pagamentoConsultaService.bloqueadoPorPagamento(agendamento));

        agendamento.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        assertFalse(pagamentoConsultaService.bloqueadoPorPagamento(agendamento));

        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        assertFalse(pagamentoConsultaService.bloqueadoPorPagamento(agendamento));
    }
}
