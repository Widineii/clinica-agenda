package com.clinica.sistema.model;

public enum PagamentoStatus {
    PAGAMENTO_FUTURO,
    /** Link/QR ativo; prazo curto (5 min) para confirmar ou o agendamento e removido. */
    ESPERANDO_CONFIRMACAO,
    /** Dentro da janela de pagamento (1 dia antes), ainda sem QR aberto. */
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
