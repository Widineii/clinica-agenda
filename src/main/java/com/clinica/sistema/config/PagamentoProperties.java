package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.pagamento")
public class PagamentoProperties {

    private int prazoConfirmacaoMinutos = 5;

    public int getPrazoConfirmacaoMinutos() {
        return prazoConfirmacaoMinutos;
    }

    public void setPrazoConfirmacaoMinutos(int prazoConfirmacaoMinutos) {
        this.prazoConfirmacaoMinutos = prazoConfirmacaoMinutos;
    }
}
