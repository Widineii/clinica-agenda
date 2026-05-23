package com.clinica.sistema.dto;

/**
 * Resumo de uso do banco para acompanhamento do limite Neon (armazenamento).
 */
public record UsoBancoView(
        long totalAgendamentos,
        long agendamentosAvulsos,
        long agendamentosFixosOuQuinzenais,
        long agendamentosJaEncerrados,
        long agendamentosMesAtual,
        long agendamentosHoje,
        long totalUsuarios,
        long totalProfissionais,
        long totalSalas,
        long relatoriosMensaisArquivados,
        long relatoriosComPdfLegado,
        String bytesJsonLabel,
        String bytesPdfLegadoLabel,
        Long bytesBancoReal,
        String tamanhoBancoRealLabel,
        String tamanhoEstimadoLabel,
        int limiteNeonMb,
        String percentualLabel,
        int barraPercentual,
        String nivelAlerta,
        boolean postgresComTamanhoReal
) {
}
