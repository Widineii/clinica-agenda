package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import com.clinica.sistema.repository.RelatorioMensalArquivadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RelatorioMensalService {

    private static final Logger log = LoggerFactory.getLogger(RelatorioMensalService.class);

    private final AgendamentoService agendamentoService;
    private final RelatorioMensalPdfService relatorioMensalPdfService;
    private final RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository;
    private final JsonMapper jsonMapper;

    @Value("${app.relatorio-mensal.dia-fechamento:3}")
    private int diaFechamento;

    public RelatorioMensalService(
            AgendamentoService agendamentoService,
            RelatorioMensalPdfService relatorioMensalPdfService,
            RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository,
            JsonMapper jsonMapper
    ) {
        this.agendamentoService = agendamentoService;
        this.relatorioMensalPdfService = relatorioMensalPdfService;
        this.relatorioMensalArquivadoRepository = relatorioMensalArquivadoRepository;
        this.jsonMapper = jsonMapper;
    }

    /** Sempre o mes anterior ao calendario de hoje (nunca o mes atual). */
    public YearMonth mesPassadoReferencia() {
        return YearMonth.now().minusMonths(1);
    }

    public String formatarMesReferencia(YearMonth mesReferencia) {
        String mes = mesReferencia.getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, new Locale("pt", "BR"));
        if (mes != null && !mes.isBlank()) {
            mes = Character.toUpperCase(mes.charAt(0)) + mes.substring(1);
        }
        return mes + " de " + mesReferencia.getYear();
    }

    public String mensagemRelatorioDisponivelAposDia3() {
        return "O relatorio de "
                + formatarMesReferencia(mesPassadoReferencia())
                + " (mes passado) so fica disponivel a partir do dia "
                + diaFechamento
                + " deste mes. Nesse dia o sistema gera o PDF e remove agendamentos avulsos daquele mes.";
    }

    public boolean podeExecutarFechamentoAutomatico() {
        return LocalDate.now().getDayOfMonth() >= diaFechamento;
    }

    /** @return true se gerou/arquivou o relatorio nesta execucao */
    public boolean executarFechamentoAutomaticoSeDevido() {
        if (!podeExecutarFechamentoAutomatico()) {
            return false;
        }
        try {
            return arquivarMesSeNecessario(mesPassadoReferencia()).isPresent();
        } catch (RuntimeException e) {
            log.error("Fechamento automatico do relatorio mensal falhou", e);
            return false;
        }
    }

    /**
     * Fechamento do mes passado: monta relatorio com dados do periodo, salva PDF e so depois
     * apaga agendamentos avulsos daquele mes (semanal/quinzenal permanecem).
     */
    @Transactional
    public Optional<RelatorioMensalArquivado> arquivarMesSeNecessario(YearMonth mesReferencia) {
        if (relatorioMensalArquivadoRepository.existsByAnoAndMes(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue()
        )) {
            return relatorioMensalArquivadoRepository.findByAnoAndMes(
                    mesReferencia.getYear(),
                    mesReferencia.getMonthValue()
            );
        }

        RelatorioMensalUsoSalasView relatorio = agendamentoService.montarRelatorioMensalUsoSalas(mesReferencia);
        byte[] pdf = relatorioMensalPdfService.gerarPdf(relatorio);

        RelatorioMensalArquivado arquivado = new RelatorioMensalArquivado();
        arquivado.setAno(mesReferencia.getYear());
        arquivado.setMes(mesReferencia.getMonthValue());
        arquivado.setMesLabel(relatorio.getMesReferenciaLabel());
        arquivado.setTotalGeral(relatorio.getTotalGeral());
        arquivado.setGeradoEm(LocalDateTime.now());
        arquivado.setPdf(pdf);
        arquivado.setDadosJson(serializarRelatorio(relatorio));

        LocalDateTime inicio = mesReferencia.atDay(1).atStartOfDay();
        LocalDateTime fim = mesReferencia.plusMonths(1).atDay(1).atStartOfDay();
        long removidos = agendamentoService.limparAgendamentosNoPeriodo(inicio, fim);
        arquivado.setAgendamentosRemovidos(removidos);

        RelatorioMensalArquivado salvo = relatorioMensalArquivadoRepository.save(arquivado);
        log.info(
                "Relatorio mensal arquivado para {}: {} horarios, {} agendamentos removidos.",
                relatorio.getMesReferenciaLabel(),
                relatorio.getTotalGeral(),
                removidos
        );
        return Optional.of(salvo);
    }

    public Optional<RelatorioMensalArquivado> buscarArquivado(YearMonth mesReferencia) {
        return relatorioMensalArquivadoRepository.findByAnoAndMes(
                mesReferencia.getYear(),
                mesReferencia.getMonthValue()
        );
    }

    public List<RelatorioMensalArquivado> listarArquivados() {
        return relatorioMensalArquivadoRepository.findAllByOrderByAnoDescMesDesc();
    }

    public RelatorioMensalUsoSalasView carregarRelatorioParaExibicao(YearMonth mesReferencia) {
        return buscarArquivado(mesReferencia)
                .map(arquivado -> desserializarRelatorio(arquivado.getDadosJson()))
                .orElseGet(() -> agendamentoService.montarRelatorioMensalUsoSalas(mesReferencia));
    }

    public String nomeArquivoPdf(YearMonth mesReferencia) {
        RelatorioMensalUsoSalasView relatorio = carregarRelatorioParaExibicao(mesReferencia);
        return relatorioMensalPdfService.nomeArquivo(relatorio);
    }

    private String serializarRelatorio(RelatorioMensalUsoSalasView relatorio) {
        try {
            return jsonMapper.writeValueAsString(relatorio);
        } catch (JacksonException e) {
            throw new RuntimeException("Nao foi possivel salvar o relatorio mensal.", e);
        }
    }

    private RelatorioMensalUsoSalasView desserializarRelatorio(String dadosJson) {
        try {
            return jsonMapper.readValue(dadosJson, RelatorioMensalUsoSalasView.class);
        } catch (JacksonException e) {
            throw new RuntimeException("Nao foi possivel ler o relatorio mensal arquivado.", e);
        }
    }
}
