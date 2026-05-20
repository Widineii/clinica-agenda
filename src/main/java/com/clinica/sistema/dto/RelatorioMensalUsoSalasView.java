package com.clinica.sistema.dto;

import lombok.Data;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Data
public class RelatorioMensalUsoSalasView {
    private YearMonth mesReferencia;
    private String mesReferenciaLabel;
    private List<RelatorioUsoSalaProfissional> profissionais = new ArrayList<>();
    private long totalGeral;
}
