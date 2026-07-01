package com.clinica.sistema.controller;

import com.clinica.sistema.service.PagamentoConsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class InfinitePayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(InfinitePayWebhookController.class);

    private final PagamentoConsultaService pagamentoConsultaService;

    public InfinitePayWebhookController(PagamentoConsultaService pagamentoConsultaService) {
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    @PostMapping("/infinitepay")
    public ResponseEntity<Map<String, String>> receberWebhook(@RequestBody Map<String, Object> payload) {
        try {
            pagamentoConsultaService.processarWebhookInfinitePay(payload);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (RuntimeException ex) {
            log.error("Falha ao processar webhook InfinitePay: {}", ex.getMessage());
            return ResponseEntity.ok(Map.of("status", "ignorado"));
        }
    }
}
