package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.sistema")
public class SistemaProperties {

    private String versao = "1.1.0";
    private boolean mostrarAvisoCorrecaoPagamento = true;
    private String avisoCorrecaoPagamentoMensagem =
            "Atualizacao v1.1.0: corrigimos o erro em que o pagamento PIX era feito na InfinitePay "
                    + "mas nao aparecia no sistema. Se voce ja pagou e ainda consta pendente, "
                    + "aguarde 1 minuto e atualize a pagina — o sistema confere automaticamente.";

    public String getVersao() {
        return versao;
    }

    public void setVersao(String versao) {
        this.versao = versao;
    }

    public boolean isMostrarAvisoCorrecaoPagamento() {
        return mostrarAvisoCorrecaoPagamento;
    }

    public void setMostrarAvisoCorrecaoPagamento(boolean mostrarAvisoCorrecaoPagamento) {
        this.mostrarAvisoCorrecaoPagamento = mostrarAvisoCorrecaoPagamento;
    }

    public String getAvisoCorrecaoPagamentoMensagem() {
        return avisoCorrecaoPagamentoMensagem;
    }

    public void setAvisoCorrecaoPagamentoMensagem(String avisoCorrecaoPagamentoMensagem) {
        this.avisoCorrecaoPagamentoMensagem = avisoCorrecaoPagamentoMensagem;
    }
}
