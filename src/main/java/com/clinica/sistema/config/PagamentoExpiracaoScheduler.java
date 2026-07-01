package com.clinica.sistema.config;

import com.clinica.sistema.service.PagamentoConsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PagamentoExpiracaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(PagamentoExpiracaoScheduler.class);

    private final PagamentoConsultaService pagamentoConsultaService;

    public PagamentoExpiracaoScheduler(PagamentoConsultaService pagamentoConsultaService) {
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    @Scheduled(fixedDelayString = "${app.pagamento.cron-expiracao-ms:30000}")
    public void expirarPagamentosVencidos() {
        int revertidos = pagamentoConsultaService.expirarPagamentosVencidos();
        if (revertidos > 0) {
            log.info("Revertidos {} agendamento(s) com confirmacao PIX expirada (reserva mantida).", revertidos);
        }
    }

    @Scheduled(fixedDelayString = "${app.pagamento.cron-sincronizacao-ms:60000}")
    public void sincronizarPagamentosPendentes() {
        int confirmados = pagamentoConsultaService.sincronizarPagamentosPendentesNaInfinitePay();
        if (confirmados > 0) {
            log.info("Confirmados {} pagamento(s) via consulta InfinitePay.", confirmados);
        }
    }
}
