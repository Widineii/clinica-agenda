package com.clinica.sistema.service;

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

    public PagamentoConsultaService(
            AgendamentoRepository repository,
            InfinitePayService infinitePayService,
            AuthService authService
    ) {
        this.repository = repository;
        this.infinitePayService = infinitePayService;
        this.authService = authService;
    }

    public void configurarPagamentosAoSalvar(List<Agendamento> novosAgendamentos) {
        if (novosAgendamentos == null || novosAgendamentos.isEmpty()) {
            return;
        }
        for (int i = 0; i < novosAgendamentos.size(); i++) {
            Agendamento agendamento = novosAgendamentos.get(i);
            if (i == 0) {
                abrirPagamentoImediato(agendamento);
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
            abrirPagamentoImediato(agendamento);
        } else {
            agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        }
    }

    @Transactional
    public void atualizarJanelasDePagamento() {
        LocalDate hoje = LocalDate.now();
        List<Agendamento> futuros = repository.findByStatusPagamentoAndDataHoraInicioGreaterThanEqual(
                PagamentoStatus.PAGAMENTO_FUTURO,
                hoje.atStartOfDay()
        );
        for (Agendamento agendamento : futuros) {
            if (deveAbrirPagamentoAgora(agendamento)) {
                abrirPagamentoImediato(agendamento);
                repository.save(agendamento);
            }
        }
    }

    @Transactional
    public Agendamento gerarLinkPagamento(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = buscarComPermissao(agendamentoId, usuarioLogado);
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Esta consulta ja esta paga.");
        }
        if (!deveAbrirPagamentoAgora(agendamento) && !PagamentoStatus.AGUARDANDO_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Pagamento disponivel somente a partir de 1 dia antes da consulta.");
        }
        abrirPagamentoImediato(agendamento);
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
            abrirPagamentoImediato(agendamento);
        }
        return marcarComoPago(agendamento);
    }

    public boolean deveAbrirPagamentoAgora(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        LocalDate consulta = agendamento.getDataHoraInicio().toLocalDate();
        LocalDate diaLimitePagamento = consulta.minusDays(1);
        return !LocalDate.now().isBefore(diaLimitePagamento);
    }

    public boolean podeUsarSala(Agendamento agendamento) {
        if (agendamento == null) {
            return false;
        }
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return true;
        }
        if (agendamento.getDataHoraInicio() == null) {
            return true;
        }
        LocalDate consulta = agendamento.getDataHoraInicio().toLocalDate();
        LocalDate hoje = LocalDate.now();
        if (hoje.isBefore(consulta)) {
            return true;
        }
        return false;
    }

    public boolean exibirBotaoPagar(Agendamento agendamento) {
        if (agendamento == null || PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
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
            case AGUARDANDO_PAGAMENTO -> bloqueadoPorPagamento(agendamento) ? "Nao pago - sala bloqueada" : "Aguardando pagamento";
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
        LocalDate consulta = agendamento.getDataHoraInicio().toLocalDate();
        return !LocalDate.now().isBefore(consulta)
                && !PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento());
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
        return repository.save(agendamento);
    }

    private void abrirPagamentoImediato(Agendamento agendamento) {
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return;
        }
        LinkPagamentoGerado link = infinitePayService.gerarLinkPagamento(agendamento);
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        agendamento.setPagamentoOrderNsu(link.getOrderNsu());
        agendamento.setPagamentoLink(link.getLinkPagamento());
        agendamento.setPagamentoSlug(link.getSlug());
        agendamento.setValorPagamento(infinitePayService.valorPagamento(agendamento));
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
