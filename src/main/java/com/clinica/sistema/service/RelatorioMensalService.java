package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioHistoricoResumo;
import com.clinica.sistema.dto.RelatorioMensalNotificacaoView;
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

    @Value("${app.relatorio-mensal.dia-remocao-pdf:10}")
    private int diaRemocaoPdf;

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
        return podeExecutarFechamentoAutomatico(LocalDate.now());
    }

    boolean podeExecutarFechamentoAutomatico(LocalDate referencia) {
        return referencia.getDayOfMonth() >= diaFechamento;
    }

    public static final String URL_RELATORIO_VIA_NOTIFICACAO =
            "/agendamentos/relatorio/mensal?viaNotificacao=1";

    /**
     * Sino na agenda: a partir do dia de fechamento, enquanto o relatorio do mes passado
     * ainda pode ser gerado ou baixado em PDF.
     */
    public Optional<RelatorioMensalNotificacaoView> avaliarNotificacaoMensal() {
        return avaliarNotificacaoMensal(LocalDate.now());
    }

    Optional<RelatorioMensalNotificacaoView> avaliarNotificacaoMensal(LocalDate referencia) {
        if (!podeExecutarFechamentoAutomatico(referencia)) {
            return Optional.empty();
        }

        YearMonth mesPassado = mesPassadoReferencia();
        String mesLabel = formatarMesReferencia(mesPassado);
        int ano = mesPassado.getYear();
        int mes = mesPassado.getMonthValue();
        boolean arquivadoExiste = relatorioMensalArquivadoRepository.existsByAnoAndMes(ano, mes);

        if (arquivadoExiste && !relatorioMensalArquivadoRepository.existsComDadosJson(ano, mes)) {
            return Optional.empty();
        }

        boolean pendente = !arquivadoExiste;
        String mensagemPainel = pendente
                ? "O relatorio de " + mesLabel + " ja esta pronto para gerar. "
                        + "Ao abrir, o sistema fecha o mes passado e voce pode baixar o PDF."
                : "O relatorio de " + mesLabel + " ja foi gerado. "
                        + "Abra para conferir os numeros e baixar o PDF.";

        String mensagemResumo = pendente
                ? "Relatorio de " + mesLabel + " pronto para gerar"
                : "Relatorio de " + mesLabel + " pronto para baixar";

        return Optional.of(new RelatorioMensalNotificacaoView(
                mesLabel,
                mensagemResumo,
                mensagemPainel,
                pendente,
                URL_RELATORIO_VIA_NOTIFICACAO
        ));
    }

    public void adicionarNotificacaoAoModelSeAplicavel(org.springframework.ui.Model model) {
        avaliarNotificacaoMensal().ifPresent(notificacao ->
                model.addAttribute("notificacaoRelatorioMensal", notificacao)
        );
    }

    public int getDiaRemocaoPdf() {
        return diaRemocaoPdf;
    }

    public boolean temPdfDisponivel(RelatorioMensalArquivado arquivado) {
        return arquivado != null && arquivado.temPdfDisponivel();
    }

    /** Pode baixar se o relatorio foi arquivado e ainda tem os dados salvos (JSON). */
    public boolean podeExportarPdf(RelatorioMensalArquivado arquivado) {
        return arquivado != null
                && arquivado.getDadosJson() != null
                && !arquivado.getDadosJson().isBlank();
    }

    public boolean pdfRemovidoDoBanco(RelatorioMensalArquivado arquivado) {
        return arquivado != null && !arquivado.temPdfDisponivel() && podeExportarPdf(arquivado);
    }

    public byte[] obterPdfParaDownload(YearMonth mesReferencia) {
        RelatorioMensalArquivado arquivado = buscarArquivado(mesReferencia)
                .orElseThrow(() -> new RuntimeException("Relatorio nao encontrado para o periodo informado."));

        if (!podeExportarPdf(arquivado)) {
            throw new RuntimeException("Nao ha dados para gerar o PDF deste relatorio.");
        }

        if (arquivado.temPdfDisponivel()) {
            return arquivado.getPdf();
        }

        return regenerarESalvarPdf(arquivado);
    }

    /** Sempre gera PDF novo a partir do JSON (layout atualizado); atualiza bytes no banco se existir arquivo. */
    @Transactional
    public byte[] regenerarESalvarPdf(RelatorioMensalArquivado arquivado) {
        RelatorioMensalUsoSalasView relatorio = desserializarRelatorio(arquivado.getDadosJson());
        byte[] pdf = relatorioMensalPdfService.gerarPdf(relatorio);
        arquivado.setPdf(pdf);
        arquivado.setPdfRemovidoEm(null);
        relatorioMensalArquivadoRepository.save(arquivado);
        log.info("PDF do relatorio {} regenerado ({} bytes).", relatorio.getMesReferenciaLabel(), pdf.length);
        return pdf;
    }

    /**
     * PDF do relatorio de um mes fica disponivel ate o dia anterior ao diaRemocaoPdf
     * do mes seguinte (ex.: abril some no dia 10 de maio).
     */
    boolean pdfExpiradoParaRelatorio(int ano, int mes, LocalDate referencia) {
        LocalDate inicioRemocao = YearMonth.of(ano, mes).plusMonths(1).atDay(diaRemocaoPdf);
        return !referencia.isBefore(inicioRemocao);
    }

    /** Remove bytes do PDF expirados; mantem JSON com os numeros na tela. */
    @Transactional
    public int removerPdfsExpiradosSeDevido() {
        LocalDate hoje = LocalDate.now();
        if (hoje.getDayOfMonth() < diaRemocaoPdf) {
            return 0;
        }

        int removidos = 0;
        for (RelatorioMensalArquivado arquivado : relatorioMensalArquivadoRepository.findByPdfIsNotNull()) {
            if (!pdfExpiradoParaRelatorio(arquivado.getAno(), arquivado.getMes(), hoje)) {
                continue;
            }
            arquivado.setPdf(null);
            arquivado.setPdfRemovidoEm(LocalDateTime.now());
            relatorioMensalArquivadoRepository.save(arquivado);
            removidos++;
        }

        if (removidos > 0) {
            log.info("Removidos {} PDF(s) de relatorios mensais para liberar espaco no banco.", removidos);
        }
        return removidos;
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
            Optional<RelatorioMensalArquivado> existente = relatorioMensalArquivadoRepository.findByAnoAndMes(
                    mesReferencia.getYear(),
                    mesReferencia.getMonthValue()
            );
            existente.ifPresent(this::regenerarESalvarPdf);
            return existente;
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

    public List<RelatorioHistoricoResumo> listarHistoricoResumo() {
        return relatorioMensalArquivadoRepository.listarHistoricoMetadados().stream()
                .map(item -> new RelatorioHistoricoResumo(
                        item.getAno(),
                        item.getMes(),
                        item.getMesLabel(),
                        item.getDadosJson() != null && !item.getDadosJson().isBlank()
                ))
                .toList();
    }

    @Transactional
    public boolean removerArquivoMesPassadoSeExistir() {
        YearMonth mesPassado = mesPassadoReferencia();
        Optional<RelatorioMensalArquivado> arquivado = relatorioMensalArquivadoRepository.findByAnoAndMes(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        );
        if (arquivado.isEmpty()) {
            return false;
        }
        relatorioMensalArquivadoRepository.delete(arquivado.get());
        log.info("Arquivo de relatorio removido para teste: {}", mesPassado);
        return true;
    }

    public RelatorioMensalUsoSalasView carregarRelatorioParaExibicao(YearMonth mesReferencia) {
        return buscarArquivado(mesReferencia)
                .map(arquivado -> {
                    try {
                        return desserializarRelatorio(arquivado.getDadosJson());
                    } catch (RuntimeException e) {
                        log.warn(
                                "Relatorio arquivado de {} com JSON invalido; remontando a partir dos agendamentos.",
                                formatarMesReferencia(mesReferencia),
                                e
                        );
                        return agendamentoService.montarRelatorioMensalUsoSalas(mesReferencia);
                    }
                })
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
