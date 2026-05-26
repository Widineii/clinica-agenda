package com.clinica.sistema.service;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.dto.LinkPagamentoGerado;
import com.clinica.sistema.model.Agendamento;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class InfinitePayService {

    private static final String API_LINKS = "https://api.checkout.infinitepay.io/links";

    private final InfinitePayProperties properties;
    private final RestTemplate restTemplate;

    public InfinitePayService(InfinitePayProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    public LinkPagamentoGerado gerarLinkPagamento(Agendamento agendamento) {
        String orderNsu = "ag-" + agendamento.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal valor = valorPagamento(agendamento);
        if (valor == null || valor.signum() <= 0) {
            throw new RuntimeException("Valor da taxa da clinica invalido para pagamento.");
        }

        if (properties.isModoTeste()) {
            String link = properties.getBaseUrl().replaceAll("/$", "")
                    + "/pagamentos/checkout-teste?order=" + orderNsu
                    + "&agendamento=" + agendamento.getId();
            return new LinkPagamentoGerado(orderNsu, link, "teste-" + orderNsu);
        }

        return gerarLinkReal(agendamento, orderNsu, valor);
    }

    private LinkPagamentoGerado gerarLinkReal(Agendamento agendamento, String orderNsu, BigDecimal valor) {
        int centavos = valor.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("handle", properties.getHandle());
        body.put("order_nsu", orderNsu);
        body.put("webhook_url", properties.getBaseUrl().replaceAll("/$", "") + "/api/webhooks/infinitepay");
        body.put("items", java.util.List.of(Map.of(
                "quantity", 1,
                "price", centavos,
                "description", descricaoItem(agendamento)
        )));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resposta = restTemplate.postForObject(API_LINKS, request, Map.class);
            if (resposta == null) {
                throw new RuntimeException("InfinitePay nao retornou resposta.");
            }
            Object url = resposta.get("url");
            Object slug = resposta.get("slug");
            if (url == null || url.toString().isBlank()) {
                throw new RuntimeException("InfinitePay nao retornou link de pagamento.");
            }
            return new LinkPagamentoGerado(
                    orderNsu,
                    url.toString(),
                    slug != null ? slug.toString() : null
            );
        } catch (RestClientException ex) {
            throw new RuntimeException("Falha ao gerar link InfinitePay: " + ex.getMessage());
        }
    }

    public BigDecimal valorPagamento(Agendamento agendamento) {
        if (agendamento.getValorClinicaCobra() != null) {
            return agendamento.getValorClinicaCobra();
        }
        return BigDecimal.ZERO;
    }

    private String descricaoItem(Agendamento agendamento) {
        String cliente = agendamento.getNomeCliente() != null ? agendamento.getNomeCliente() : "Consulta";
        String sala = agendamento.getSala() != null && agendamento.getSala().getNome() != null
                ? agendamento.getSala().getNome()
                : "Sala";
        return "Taxa clinica - " + cliente + " - " + sala;
    }
}
