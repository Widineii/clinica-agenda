package com.clinica.sistema.service;

import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.dto.LinkPagamentoGerado;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PagamentoConsultaService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoConsultaService.class);

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

    public void configurarPagamentosAoSalvar(List<Agendamento> novosAgendamentos, Usuario profissional) {
        if (novosAgendamentos == null || novosAgendamentos.isEmpty()) {
            return;
        }
        if (authService.profissionalIgnoraValoresEPagamento(profissional)) {
            for (Agendamento agendamento : novosAgendamentos) {
                agendamento.setStatusPagamento(PagamentoStatus.PAGO);
            }
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
        if (agendamento.getProfissional() != null
                && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
            agendamento.setStatusPagamento(PagamentoStatus.PAGO);
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
        int revertidos = 0;
        for (Agendamento agendamento : expirados) {
            if (tentarConfirmarPagamentoRemoto(agendamento)) {
                continue;
            }
            reverterExpiracaoConfirmacao(agendamento);
            revertidos++;
        }
        return revertidos;
    }

    @Transactional
    public int sincronizarPagamentosPendentesNaInfinitePay() {
        List<Agendamento> candidatos = repository.findByStatusPagamentoInAndPagamentoOrderNsuIsNotNull(
                List.of(PagamentoStatus.ESPERANDO_CONFIRMACAO, PagamentoStatus.AGUARDANDO_PAGAMENTO)
        );
        int confirmados = 0;
        for (Agendamento agendamento : candidatos) {
            if (tentarConfirmarPagamentoRemoto(agendamento)) {
                confirmados++;
            }
        }
        return confirmados;
    }

    @Transactional
    public Agendamento gerarLinkPagamento(Long agendamentoId, Usuario usuarioLogado) {
        return pagarAgora(agendamentoId, usuarioLogado);
    }

    @Transactional
    public Agendamento pagarAgora(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = buscarComPermissao(agendamentoId, usuarioLogado);
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Esta consulta ja esta paga.");
        }
        if (!podePagarAgora(agendamento) && !agendamento.possuiQrPagamentoAtivo()) {
            throw new RuntimeException("Esta consulta nao esta disponivel para pagamento.");
        }
        if (agendamento.possuiQrPagamentoAtivo()) {
            return agendamento;
        }
        if (temLinkPagamentoGerado(agendamento)) {
            reativarConfirmacaoPagamento(agendamento);
            return repository.save(agendamento);
        }
        iniciarConfirmacaoPagamento(agendamento);
        return repository.save(agendamento);
    }

    @Transactional
    public Agendamento confirmarPagamentoPorOrderNsu(String orderNsu) {
        return confirmarPagamento(orderNsu, null, null, false);
    }

    @Transactional
    public Agendamento confirmarPagamentoPorRetornoInfinitePay(
            String orderNsu,
            String slug,
            String transactionNsu,
            Long agendamentoId
    ) {
        if (orderNsu != null && !orderNsu.isBlank()) {
            return confirmarPagamento(orderNsu, slug, transactionNsu, true);
        }
        if (agendamentoId == null) {
            throw new RuntimeException("Pedido de pagamento invalido.");
        }
        Agendamento agendamento = repository.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return agendamento;
        }
        if (!infinitePayService.consultarSePago(
                agendamento.getPagamentoOrderNsu(),
                primeiroValorNaoVazio(slug, agendamento.getPagamentoSlug()),
                transactionNsu
        )) {
            throw new RuntimeException("Pagamento ainda nao confirmado na InfinitePay.");
        }
        return marcarComoPago(agendamento);
    }

    @Transactional
    public void processarWebhookInfinitePay(Map<String, Object> payload) {
        String orderNsu = extrairCampoTexto(payload, "order_nsu", "orderNsu", "order");
        String slug = extrairCampoTexto(payload, "invoice_slug", "slug");
        String transactionNsu = extrairCampoTexto(payload, "transaction_nsu", "transactionNsu");

        log.info(
                "Webhook InfinitePay recebido. order_nsu={}, slug={}, transaction_nsu={}",
                orderNsu,
                slug,
                transactionNsu
        );

        Optional<Agendamento> agendamento = localizarAgendamentoPorPagamento(orderNsu, slug);
        if (agendamento.isEmpty()) {
            log.warn("Webhook InfinitePay sem agendamento correspondente. order_nsu={}, slug={}", orderNsu, slug);
            return;
        }

        if (PagamentoStatus.PAGO.equals(agendamento.get().getStatusPagamento())) {
            return;
        }

        String orderNsuConfirmacao = primeiroValorNaoVazio(orderNsu, agendamento.get().getPagamentoOrderNsu());
        if (infinitePayService.consultarSePago(
                orderNsuConfirmacao,
                primeiroValorNaoVazio(slug, agendamento.get().getPagamentoSlug()),
                transactionNsu
        )) {
            confirmarPagamento(orderNsuConfirmacao, slug, transactionNsu, false);
            return;
        }

        confirmarPagamento(orderNsuConfirmacao, slug, transactionNsu, false);
    }

    private Agendamento confirmarPagamento(
            String orderNsu,
            String slug,
            String transactionNsu,
            boolean exigirConfirmacaoNaApi
    ) {
        Agendamento agendamento = localizarAgendamentoPorPagamento(orderNsu, slug)
                .orElseThrow(() -> new RuntimeException("Pedido de pagamento nao encontrado."));
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return agendamento;
        }
        if (exigirConfirmacaoNaApi && !infinitePayService.consultarSePago(
                primeiroValorNaoVazio(orderNsu, agendamento.getPagamentoOrderNsu()),
                primeiroValorNaoVazio(slug, agendamento.getPagamentoSlug()),
                transactionNsu
        )) {
            throw new RuntimeException("Pagamento ainda nao confirmado na InfinitePay.");
        }
        return marcarComoPago(agendamento);
    }

    private boolean tentarConfirmarPagamentoRemoto(Agendamento agendamento) {
        if (agendamento == null || PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (agendamento.getPagamentoOrderNsu() == null || agendamento.getPagamentoOrderNsu().isBlank()) {
            return false;
        }
        if (!infinitePayService.consultarSePago(agendamento)) {
            return false;
        }
        marcarComoPago(agendamento);
        log.info(
                "Pagamento confirmado via consulta InfinitePay. agendamentoId={}, order_nsu={}",
                agendamento.getId(),
                agendamento.getPagamentoOrderNsu()
        );
        return true;
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

    public List<Agendamento> listarDisponiveisParaPagarAntecipado(Usuario usuarioLogado, boolean verTodos) {
        LocalDateTime agora = LocalDateTime.now();
        List<Agendamento> candidatos;
        if (verTodos && (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado))) {
            candidatos = repository.findByDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(agora);
        } else {
            candidatos = repository.findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
                    usuarioLogado.getId(),
                    agora
            );
        }
        return candidatos.stream()
                .filter(this::podePagarAgora)
                .limit(16)
                .toList();
    }

    public List<Agendamento> listarPendenciasObrigatoriasParaBloqueio(Usuario usuarioLogado) {
        if (usuarioLogado == null
                || authService.isAdmin(usuarioLogado)
                || authService.isDonaClinica(usuarioLogado)
                || authService.profissionalIgnoraValoresEPagamento(usuarioLogado)) {
            return Collections.emptyList();
        }

        return repository.findByProfissionalIdOrderByDataHoraInicioAsc(usuarioLogado.getId()).stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento()))
                .filter(this::ePendenciaObrigatoriaParaDesbloqueio)
                .toList();
    }

    public boolean profissionalBloqueadoPorPendenciaPagamento(Usuario usuarioLogado) {
        return !listarPendenciasObrigatoriasParaBloqueio(usuarioLogado).isEmpty();
    }

    public boolean podePagarAgora(Agendamento agendamento) {
        if (agendamento == null || PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (agendamento.getProfissional() != null
                && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
            return false;
        }
        if (agendamento.possuiQrPagamentoAtivo()) {
            return false;
        }
        if (agendamento.getDataHoraInicio() == null) {
            return false;
        }
        if (!agendamento.getDataHoraInicio().isAfter(LocalDateTime.now())) {
            return false;
        }
        PagamentoStatus status = agendamento.getStatusPagamento();
        return status == null
                || status == PagamentoStatus.PAGAMENTO_FUTURO
                || status == PagamentoStatus.AGUARDANDO_PAGAMENTO;
    }

    public boolean podeVerPagamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado)) {
            return true;
        }
        return agendamento.getProfissional() != null
                && agendamento.getProfissional().getId().equals(usuarioLogado.getId());
    }

    public boolean exibirBotaoPagar(Agendamento agendamento) {
        return podePagarAgora(agendamento);
    }

    public String formatarValorTaxaPix(Agendamento agendamento) {
        BigDecimal valor = infinitePayService.resolverValorTaxaClinica(agendamento);
        if (valor == null || valor.signum() <= 0) {
            return "—";
        }
        return com.clinica.sistema.util.MoedaBrasilUtil.formatar(valor);
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

    private boolean ePendenciaObrigatoriaParaDesbloqueio(Agendamento agendamento) {
        if (agendamento.possuiQrPagamentoAtivo()) {
            return true;
        }
        if (bloqueadoPorPagamento(agendamento)) {
            return true;
        }
        return deveAbrirPagamentoAgora(agendamento) && statusPendenteDePagamento(agendamento);
    }

    private boolean statusPendenteDePagamento(Agendamento agendamento) {
        PagamentoStatus status = agendamento.getStatusPagamento();
        return status == null
                || status == PagamentoStatus.PAGAMENTO_FUTURO
                || status == PagamentoStatus.AGUARDANDO_PAGAMENTO;
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
        BigDecimal taxaPix = infinitePayService.resolverValorTaxaClinica(agendamento);
        if (taxaPix == null || taxaPix.signum() <= 0) {
            throw new RuntimeException("Valor da taxa da clinica invalido para pagamento.");
        }
        if (agendamento.getValorClinicaCobra() == null || agendamento.getValorClinicaCobra().signum() <= 0) {
            agendamento.setValorClinicaCobra(taxaPix);
            if (agendamento.getValorProfissionalRecebe() != null) {
                agendamento.setValorLiquidoProfissional(
                        agendamento.getValorProfissionalRecebe().subtract(taxaPix).max(BigDecimal.ZERO)
                                .setScale(2, java.math.RoundingMode.HALF_UP)
                );
            }
        }
        agendamento.setValorPagamento(taxaPix);
        LinkPagamentoGerado link = infinitePayService.gerarLinkPagamento(agendamento);
        LocalDateTime agora = LocalDateTime.now();
        agendamento.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        agendamento.setPagamentoOrderNsu(link.getOrderNsu());
        agendamento.setPagamentoLink(link.getLinkPagamento());
        agendamento.setPagamentoSlug(link.getSlug());
        agendamento.setPagamentoIniciadoEm(agora);
        agendamento.setPagamentoExpiraEm(agora.plusMinutes(pagamentoProperties.getPrazoConfirmacaoMinutos()));
    }

    private void reativarConfirmacaoPagamento(Agendamento agendamento) {
        LocalDateTime agora = LocalDateTime.now();
        agendamento.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        agendamento.setPagamentoIniciadoEm(agora);
        agendamento.setPagamentoExpiraEm(agora.plusMinutes(pagamentoProperties.getPrazoConfirmacaoMinutos()));
    }

    private void reverterExpiracaoConfirmacao(Agendamento agendamento) {
        agendamento.setPagamentoExpiraEm(null);
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        repository.save(agendamento);
        log.info(
                "Pagamento expirado sem confirmacao; agendamento mantido aguardando pagamento. agendamentoId={}, order_nsu={}",
                agendamento.getId(),
                agendamento.getPagamentoOrderNsu()
        );
    }

    private boolean temLinkPagamentoGerado(Agendamento agendamento) {
        return agendamento.getPagamentoOrderNsu() != null
                && !agendamento.getPagamentoOrderNsu().isBlank()
                && agendamento.getPagamentoLink() != null
                && !agendamento.getPagamentoLink().isBlank();
    }

    private Optional<Agendamento> localizarAgendamentoPorPagamento(String orderNsu, String slug) {
        if (orderNsu != null && !orderNsu.isBlank()) {
            Optional<Agendamento> porOrderNsu = repository.findByPagamentoOrderNsu(orderNsu);
            if (porOrderNsu.isPresent()) {
                return porOrderNsu;
            }
            Optional<Agendamento> porId = extrairAgendamentoIdDoOrderNsu(orderNsu).flatMap(repository::findById);
            if (porId.isPresent()) {
                return porId;
            }
        }
        if (slug != null && !slug.isBlank()) {
            return repository.findByPagamentoSlug(slug);
        }
        return Optional.empty();
    }

    private Optional<Long> extrairAgendamentoIdDoOrderNsu(String orderNsu) {
        if (orderNsu == null || !orderNsu.startsWith("ag-")) {
            return Optional.empty();
        }
        String[] partes = orderNsu.split("-");
        if (partes.length < 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(partes[1]));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String extrairCampoTexto(Map<String, Object> payload, String... chaves) {
        if (payload == null) {
            return null;
        }
        for (String chave : chaves) {
            Object valor = payload.get(chave);
            if (valor != null && !valor.toString().isBlank()) {
                return valor.toString();
            }
        }
        return null;
    }

    private String primeiroValorNaoVazio(String... valores) {
        if (valores == null) {
            return null;
        }
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
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
