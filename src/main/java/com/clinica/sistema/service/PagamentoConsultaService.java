package com.clinica.sistema.service;

import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.dto.LinkPagamentoGerado;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PagamentoConsultaService {

    private final AgendamentoRepository repository;
    private final InfinitePayService infinitePayService;
    private final AuthService authService;
    private final PagamentoProperties pagamentoProperties;

    public PagamentoConsultaService(
            AgendamentoRepository repository,
            InfinitePayService infinitePayService,
            AuthService authService,
            PagamentoProperties pagamentoProperties
    ) {
        this.repository = repository;
        this.infinitePayService = infinitePayService;
        this.authService = authService;
        this.pagamentoProperties = pagamentoProperties;
    }

    public void configurarPagamentosAoSalvar(List<Agendamento> novosAgendamentos) {
        if (novosAgendamentos == null || novosAgendamentos.isEmpty()) {
            return;
        }
        for (int i = 0; i < novosAgendamentos.size(); i++) {
            Agendamento agendamento = novosAgendamentos.get(i);
            if (i == 0) {
                iniciarConfirmacaoPagamento(agendamento);
            } else {
                agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
            }
        }
    }

    public void configurarPagamentoNovaOcorrenciaSerie(Agendamento agendamento) {
        if (agendamento.getStatusPagamento() != null) {
            return;
        }
        if (deveAbrirPagamentoAgora(agendamento)) {
            agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        } else {
            agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        }
    }

    @Transactional
    public void processarPagamentosPendentes() {
        expirarPagamentosVencidos();
    }

    @Transactional
    public int expirarPagamentosVencidos() {
        LocalDateTime agora = LocalDateTime.now();
        List<Agendamento> expirados = repository.findByStatusPagamentoAndPagamentoExpiraEmBefore(
                PagamentoStatus.ESPERANDO_CONFIRMACAO,
                agora
        );
        int removidos = 0;
        for (Agendamento agendamento : expirados) {
            removidos += removerAgendamentoExpirado(agendamento);
        }
        return removidos;
    }

    @Transactional
    public Agendamento gerarLinkPagamento(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = buscarComPermissao(agendamentoId, usuarioLogado);
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Esta consulta ja esta paga.");
        }
        if (!deveAbrirPagamentoAgora(agendamento)
                && !PagamentoStatus.AGUARDANDO_PAGAMENTO.equals(agendamento.getStatusPagamento())
                && !PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Pagamento disponivel somente a partir de 1 dia antes da consulta.");
        }
        iniciarConfirmacaoPagamento(agendamento);
        return repository.save(agendamento);
    }

    @Transactional
    public Agendamento confirmarPagamentoPorOrderNsu(String orderNsu) {
        Agendamento agendamento = repository.findByPagamentoOrderNsu(orderNsu)
                .orElseThrow(() -> new RuntimeException("Pedido de pagamento nao encontrado."));
        return marcarComoPago(agendamento);
    }

    @Transactional
    public Agendamento simularPagamento(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = buscarComPermissao(agendamentoId, usuarioLogado);
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return agendamento;
        }
        if (agendamento.getPagamentoOrderNsu() == null || agendamento.getPagamentoOrderNsu().isBlank()) {
            iniciarConfirmacaoPagamento(agendamento);
        }
        return marcarComoPago(agendamento);
    }

    public List<Agendamento> listarAguardandoConfirmacao(Usuario usuarioLogado, boolean verTodos) {
        LocalDateTime agora = LocalDateTime.now();
        if (verTodos && (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado))) {
            return repository.findByStatusPagamentoAndPagamentoExpiraEmAfterOrderByPagamentoExpiraEmAsc(
                    PagamentoStatus.ESPERANDO_CONFIRMACAO,
                    agora
            );
        }
        return repository.findByProfissionalIdAndStatusPagamentoAndPagamentoExpiraEmAfterOrderByPagamentoExpiraEmAsc(
                usuarioLogado.getId(),
                PagamentoStatus.ESPERANDO_CONFIRMACAO,
                agora
        );
    }

    public boolean deveAbrirPagamentoAgora(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        LocalDate consulta = agendamento.getDataHoraInicio().toLocalDate();
        LocalDate diaLimitePagamento = consulta.minusDays(1);
        return !LocalDate.now().isBefore(diaLimitePagamento);
    }

    public boolean exibirBotaoPagar(Agendamento agendamento) {
        if (agendamento == null || PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            return agendamento.possuiQrPagamentoAtivo();
        }
        if (PagamentoStatus.AGUARDANDO_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
            return true;
        }
        return PagamentoStatus.PAGAMENTO_FUTURO.equals(agendamento.getStatusPagamento())
                && deveAbrirPagamentoAgora(agendamento);
    }

    public String rotuloStatusPagamento(Agendamento agendamento) {
        PagamentoStatus status = agendamento.getStatusPagamento();
        if (status == null) {
            return "Sem pagamento";
        }
        return switch (status) {
            case PAGO -> "Pago";
            case ESPERANDO_CONFIRMACAO -> agendamento.possuiQrPagamentoAtivo()
                    ? "Esperando confirmacao (" + agendamento.getTempoRestantePagamentoFormatado() + ")"
                    : "Confirmacao expirada";
            case AGUARDANDO_PAGAMENTO -> bloqueadoPorPagamento(agendamento)
                    ? "Nao pago - sala bloqueada"
                    : "Aguardando pagamento";
            case PAGAMENTO_FUTURO -> "Pagamento em " + formatarDiaPagamento(agendamento);
        };
    }

    public boolean bloqueadoPorPagamento(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        LocalDate consulta = agendamento.getDataHoraInicio().toLocalDate();
        return !LocalDate.now().isBefore(consulta);
    }

    public boolean exibirNaGradeComoReservado(Agendamento agendamento) {
        if (agendamento == null) {
            return false;
        }
        return PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())
                || PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())
                || PagamentoStatus.AGUARDANDO_PAGAMENTO.equals(agendamento.getStatusPagamento())
                || PagamentoStatus.PAGAMENTO_FUTURO.equals(agendamento.getStatusPagamento());
    }

    private String formatarDiaPagamento(Agendamento agendamento) {
        if (agendamento.getDataHoraInicio() == null) {
            return "—";
        }
        return agendamento.getDataHoraInicio().toLocalDate().minusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
    }

    private Agendamento marcarComoPago(Agendamento agendamento) {
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataPagamento(LocalDateTime.now());
        agendamento.setPagamentoExpiraEm(null);
        return repository.save(agendamento);
    }

    private void iniciarConfirmacaoPagamento(Agendamento agendamento) {
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return;
        }
        LinkPagamentoGerado link = infinitePayService.gerarLinkPagamento(agendamento);
        LocalDateTime agora = LocalDateTime.now();
        agendamento.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        agendamento.setPagamentoOrderNsu(link.getOrderNsu());
        agendamento.setPagamentoLink(link.getLinkPagamento());
        agendamento.setPagamentoSlug(link.getSlug());
        agendamento.setValorPagamento(infinitePayService.valorPagamento(agendamento));
        agendamento.setPagamentoIniciadoEm(agora);
        agendamento.setPagamentoExpiraEm(agora.plusMinutes(pagamentoProperties.getPrazoConfirmacaoMinutos()));
    }

    private int removerAgendamentoExpirado(Agendamento agendamento) {
        if (agendamento.getSerieFixaId() != null && !agendamento.getSerieFixaId().isBlank()) {
            return repository.deleteBySerieFixaIdAndStatusPagamentoNot(
                    agendamento.getSerieFixaId(),
                    PagamentoStatus.PAGO
            );
        }
        repository.delete(agendamento);
        return 1;
    }

    private Agendamento buscarComPermissao(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = repository.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        if (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado)) {
            return agendamento;
        }
        if (agendamento.getProfissional() == null
                || !agendamento.getProfissional().getId().equals(usuarioLogado.getId())) {
            throw new RuntimeException("Voce so pode pagar os seus proprios agendamentos.");
        }
        return agendamento;
    }
}
