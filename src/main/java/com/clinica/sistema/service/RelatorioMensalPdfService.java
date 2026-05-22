package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelatorioUsoSalaItem;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class RelatorioMensalPdfService {

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 11);

    public byte[] gerarPdf(RelatorioMensalUsoSalasView relatorio) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Document documento = new Document();
            PdfWriter.getInstance(documento, saida);
            documento.open();

            documento.add(new Paragraph("Relatorio mensal de uso de salas", TITULO));
            documento.add(new Paragraph("Clinica Afetto", TEXTO));
            documento.add(new Paragraph(" "));
            documento.add(new Paragraph("Periodo: " + relatorio.getMesReferenciaLabel(), SUBTITULO));
            documento.add(new Paragraph(
                    "Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    TEXTO
            ));
            documento.add(new Paragraph(
                    "Total de horarios no mes: " + relatorio.getTotalGeral(),
                    TEXTO
            ));
            documento.add(new Paragraph(" "));
            documento.add(new Paragraph(
                    "Agendamentos cancelados nao entram neste relatorio.",
                    TEXTO
            ));
            documento.add(new Paragraph(" "));

            if (relatorio.getProfissionais().isEmpty()) {
                documento.add(new Paragraph("Nenhum agendamento registrado neste mes.", TEXTO));
            } else {
                for (RelatorioUsoSalaProfissional profissional : relatorio.getProfissionais()) {
                    documento.add(new Paragraph(profissional.getProfissionalNome(), SUBTITULO));
                    for (RelatorioUsoSalaItem sala : profissional.getSalas()) {
                        documento.add(new Paragraph(
                                "  - " + sala.getSalaNome() + ": " + sala.getQuantidade() + " vez(es)",
                                TEXTO
                        ));
                    }
                    documento.add(new Paragraph(
                            "  Total do profissional: " + profissional.getTotalHorarios() + " horario(s)",
                            TEXTO
                    ));
                    documento.add(new Paragraph(" "));
                }
            }

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Nao foi possivel gerar o PDF do relatorio.", e);
        }
    }

    public String nomeArquivo(RelatorioMensalUsoSalasView relatorio) {
        return "relatorio-salas-"
                + relatorio.getAnoReferencia()
                + "-"
                + String.format("%02d", relatorio.getMesReferencia())
                + ".pdf";
    }
}
