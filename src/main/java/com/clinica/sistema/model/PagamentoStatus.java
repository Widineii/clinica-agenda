package com.clinica.sistema.model;

public enum PagamentoStatus {
    PAGAMENTO_FUTURO,
    AGUARDANDO_PAGAMENTO,
    PAGO;

    public static PagamentoStatus fromString(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return PagamentoStatus.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
