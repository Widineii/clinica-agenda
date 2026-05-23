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
        long bytesJsonRelatorios,
        long bytesPdfLegado,
        Long bytesBancoReal,
        String tamanhoBancoRealLabel,
        long bytesEstimados,
        String tamanhoEstimadoLabel,
        int limiteNeonMb,
        double percentualDoLimite,
        String nivelAlerta,
        boolean postgresComTamanhoReal
) {
}
