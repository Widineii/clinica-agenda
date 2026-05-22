package com.clinica.sistema.controller;

import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.RelatorioMensalService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Endpoints de apoio para testes locais do relatorio (nao disponivel em producao).
 */
@Profile("local")
@Controller
@RequestMapping("/agendamentos/relatorio/dev")
public class RelatorioDevController {

    private final RelatorioMensalService relatorioMensalService;
    private final AgendamentoService agendamentoService;

    public RelatorioDevController(
            RelatorioMensalService relatorioMensalService,
            AgendamentoService agendamentoService
    ) {
        this.relatorioMensalService = relatorioMensalService;
        this.agendamentoService = agendamentoService;
    }

    @PostMapping("/resetar-arquivo-mes-passado")
    @ResponseBody
    public String resetarArquivoMesPassado() {
        boolean removido = relatorioMensalService.removerArquivoMesPassadoSeExistir();
        if (removido) {
            return "Arquivo do mes passado removido. Crie agendamentos avulsos e abra o relatorio.";
        }
        return "Nao havia arquivo do mes passado. Pode criar agendamentos e abrir o relatorio.";
    }

    @PostMapping("/regenerar-pdf-mes-passado")
    @ResponseBody
    public String regenerarPdfMesPassado() {
        var mes = relatorioMensalService.mesPassadoReferencia();
        var arquivado = relatorioMensalService.buscarArquivado(mes);
        if (arquivado.isEmpty()) {
            return "Nao ha relatorio arquivado para " + mes + ". Abra a pagina do relatorio primeiro.";
        }
        byte[] pdf = relatorioMensalService.regenerarESalvarPdf(arquivado.get());
        return "PDF regenerado para " + mes + " (" + pdf.length + " bytes). Baixe de novo com Ctrl+F5.";
    }

    @PostMapping("/semear-semana-atual")
    public String semearSemanaAtual(RedirectAttributes redirectAttributes) {
        int criados = agendamentoService.semearAvulsosSemanaAtualParaTesteRelatorio();
        redirectAttributes.addFlashAttribute(
                "sucesso",
                "Criados " + criados + " agendamentos de teste na semana atual. "
                        + "O item 'recente-menos-24h' nao deve aparecer no relatorio (regra 24h)."
        );
        return "redirect:/agendamentos/relatorio/semanal";
    }
}
