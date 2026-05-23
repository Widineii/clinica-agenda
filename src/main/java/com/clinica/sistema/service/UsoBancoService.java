package com.clinica.sistema.service;

import com.clinica.sistema.dto.UsoBancoView;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.RelatorioMensalArquivadoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Locale;

@Service
public class UsoBancoService {

    private static final Logger log = LoggerFactory.getLogger(UsoBancoService.class);

    /** Media aproximada por linha em agendamentos + indices (bytes). */
    private static final long BYTES_ESTIMADOS_POR_AGENDAMENTO = 800L;
    private static final long BYTES_ESTIMADOS_OVERHEAD = 512_000L;

    private final AgendamentoRepository agendamentoRepository;
    private final RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SalaRepository salaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.neon.storage-limit-mb:512}")
    private int limiteNeonMb;

    public UsoBancoService(
            AgendamentoRepository agendamentoRepository,
            RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository,
            UsuarioRepository usuarioRepository,
            SalaRepository salaRepository
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.relatorioMensalArquivadoRepository = relatorioMensalArquivadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.salaRepository = salaRepository;
    }

    @Transactional(readOnly = true)
    public UsoBancoView montarResumo() {
        LocalDateTime agora = LocalDateTime.now();
        YearMonth mesAtual = YearMonth.now();
        LocalDateTime inicioMes = mesAtual.atDay(1).atStartOfDay();
        LocalDateTime fimMes = mesAtual.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime inicioHoje = LocalDate.now().atStartOfDay();
        LocalDateTime fimHoje = inicioHoje.plusDays(1);

        long totalAgendamentos = agendamentoRepository.count();
        long avulsos = agendamentoRepository.countAvulsos();
        long fixos = agendamentoRepository.countFixosOuQuinzenais();
        long encerrados = agendamentoRepository.countComDataHoraFimAntesDe(agora);
        long noMes = agendamentoRepository.countNoPeriodo(inicioMes, fimMes);
        long hoje = agendamentoRepository.countNoPeriodo(inicioHoje, fimHoje);

        long relatorios = relatorioMensalArquivadoRepository.count();
        long relatoriosComPdf = relatorioMensalArquivadoRepository.countComPdfLegado();
        long bytesJson = relatorioMensalArquivadoRepository.somaBytesJson();
        long bytesPdf = somarBytesPdfLegado();

        long bytesEstimados = BYTES_ESTIMADOS_OVERHEAD
                + totalAgendamentos * BYTES_ESTIMADOS_POR_AGENDAMENTO
                + bytesJson
                + bytesPdf;

        Long bytesBancoReal = consultarTamanhoBancoPostgres().orElse(null);
        long bytesReferencia = bytesBancoReal != null ? bytesBancoReal : bytesEstimados;
        long limiteBytes = (long) limiteNeonMb * 1024L * 1024L;
        double percentual = limiteBytes > 0
                ? Math.min(100.0, (bytesReferencia * 100.0) / limiteBytes)
                : 0.0;

        return new UsoBancoView(
                totalAgendamentos,
                avulsos,
                fixos,
                encerrados,
                noMes,
                hoje,
                usuarioRepository.count(),
                usuarioRepository.countByCargo("ROLE_PROFISSIONAL"),
                salaRepository.count(),
                relatorios,
                relatoriosComPdf,
                bytesJson,
                bytesPdf,
                bytesBancoReal,
                bytesBancoReal != null ? formatarBytes(bytesBancoReal) : null,
                bytesEstimados,
                formatarBytes(bytesEstimados),
                limiteNeonMb,
                percentual,
                classificarAlerta(percentual),
                bytesBancoReal != null
        );
    }

    private long somarBytesPdfLegado() {
        try {
            Long soma = relatorioMensalArquivadoRepository.somaBytesPdfLegado();
            return soma != null ? soma : 0L;
        } catch (RuntimeException e) {
            log.debug("Soma de PDF legado indisponivel neste banco: {}", e.getMessage());
            return 0L;
        }
    }

    private java.util.Optional<Long> consultarTamanhoBancoPostgres() {
        if (entityManager == null) {
            return java.util.Optional.empty();
        }
        try {
            Object resultado = entityManager.createNativeQuery(
                    "SELECT pg_database_size(current_database())"
            ).getSingleResult();
            if (resultado instanceof Number number) {
                return java.util.Optional.of(number.longValue());
            }
        } catch (RuntimeException e) {
            log.debug("Tamanho real do banco nao disponivel (esperado em H2 local): {}", e.getMessage());
        }
        return java.util.Optional.empty();
    }

    static String formatarBytes(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format(Locale.forLanguageTag("pt-BR"), "%.2f GB", gb);
    }

    private static String classificarAlerta(double percentual) {
        if (percentual >= 85.0) {
            return "critico";
        }
        if (percentual >= 60.0) {
            return "atencao";
        }
        return "ok";
    }
}
