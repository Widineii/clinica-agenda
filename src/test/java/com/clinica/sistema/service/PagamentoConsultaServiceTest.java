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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(infinitePayService.resolverValorTaxaClinica(any())).thenReturn(new BigDecimal("32.00"));
        when(infinitePayService.gerarLinkPagamento(any())).thenReturn(
                new com.clinica.sistema.dto.LinkPagamentoGerado("ag-1-test", "http://localhost/link", "slug")
        );
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento), profissional);

        assertEquals(PagamentoStatus.ESPERANDO_CONFIRMACAO, agendamento.getStatusPagamento());
        assertEquals(new BigDecimal("32.00"), agendamento.getValorPagamento());
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

        when(infinitePayService.resolverValorTaxaClinica(any())).thenReturn(new BigDecimal("32.00"));
        when(infinitePayService.gerarLinkPagamento(any())).thenReturn(
                new com.clinica.sistema.dto.LinkPagamentoGerado("ag-1-test", "http://localhost/link", "slug")
        );
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

    @Test
    void listarPendenciasObrigatoriasIncluiSomenteBloqueioOuPagamentoDoDia() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        Agendamento bloqueadoHoje = new Agendamento();
        bloqueadoHoje.setId(1L);
        bloqueadoHoje.setProfissional(profissional);
        bloqueadoHoje.setDataHoraInicio(LocalDate.now().atTime(9, 0));
        bloqueadoHoje.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        Agendamento pagamentoAbertoAmanha = new Agendamento();
        pagamentoAbertoAmanha.setId(2L);
        pagamentoAbertoAmanha.setProfissional(profissional);
        pagamentoAbertoAmanha.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(9, 0));
        pagamentoAbertoAmanha.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        Agendamento pagamentoFuturo = new Agendamento();
        pagamentoFuturo.setId(3L);
        pagamentoFuturo.setProfissional(profissional);
        pagamentoFuturo.setDataHoraInicio(LocalDate.now().plusDays(5).atTime(9, 0));
        pagamentoFuturo.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(10L))
                .thenReturn(java.util.List.of(bloqueadoHoje, pagamentoAbertoAmanha, pagamentoFuturo));

        var pendencias = pagamentoConsultaService.listarPendenciasObrigatoriasParaBloqueio(profissional);

        assertEquals(2, pendencias.size());
        assertEquals(1L, pendencias.get(0).getId());
        assertEquals(2L, pendencias.get(1).getId());
    }

    @Test
    void expiracaoNaoApagaAgendamentoMantemReservaAguardandoPagamento() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(7L);
        agendamento.setPagamentoOrderNsu("ag-7-abc12345");
        agendamento.setPagamentoLink("https://checkout.infinitepay.io/teste");
        agendamento.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        agendamento.setPagamentoExpiraEm(LocalDateTime.now().minusMinutes(1));

        when(repository.findByStatusPagamentoAndPagamentoExpiraEmBefore(
                eq(PagamentoStatus.ESPERANDO_CONFIRMACAO),
                any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(agendamento));
        when(infinitePayService.consultarSePago(agendamento)).thenReturn(false);
        when(repository.save(agendamento)).thenReturn(agendamento);

        int revertidos = pagamentoConsultaService.expirarPagamentosVencidos();

        assertEquals(1, revertidos);
        assertEquals(PagamentoStatus.AGUARDANDO_PAGAMENTO, agendamento.getStatusPagamento());
        assertEquals("ag-7-abc12345", agendamento.getPagamentoOrderNsu());
        verify(repository, never()).delete(agendamento);
    }

    @Test
    void pagarAgoraReutilizaMesmoPedidoQuandoLinkJaFoiGerado() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(15L);
        agendamento.setProfissional(profissional);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(11, 0));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        agendamento.setPagamentoOrderNsu("ag-15-keep0001");
        agendamento.setPagamentoLink("https://checkout.infinitepay.io/link-existente");
        agendamento.setPagamentoSlug("slug-existente");

        when(repository.findById(15L)).thenReturn(java.util.Optional.of(agendamento));
        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);
        when(repository.save(agendamento)).thenReturn(agendamento);

        Agendamento retorno = pagamentoConsultaService.pagarAgora(15L, profissional);

        assertEquals("ag-15-keep0001", retorno.getPagamentoOrderNsu());
        assertEquals(PagamentoStatus.ESPERANDO_CONFIRMACAO, retorno.getStatusPagamento());
        verify(infinitePayService, never()).gerarLinkPagamento(any());
    }

    @Test
    void confirmarPagamentoPorOrderNsuLocalizaPeloIdEmbutidoNoPedido() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(22L);
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        agendamento.setPagamentoOrderNsu("ag-22-novopedido");

        when(repository.findByPagamentoOrderNsu("ag-22-pedidoantigo")).thenReturn(java.util.Optional.empty());
        when(repository.findById(22L)).thenReturn(java.util.Optional.of(agendamento));
        when(repository.save(agendamento)).thenReturn(agendamento);

        Agendamento confirmado = pagamentoConsultaService.confirmarPagamentoPorOrderNsu("ag-22-pedidoantigo");

        assertEquals(PagamentoStatus.PAGO, confirmado.getStatusPagamento());
        assertNotNull(confirmado.getDataPagamento());
    }
}
