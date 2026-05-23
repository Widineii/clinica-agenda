package com.clinica.sistema.dto;

import lombok.Getter;

@Getter
public class SerieAgendamentoOcorrencia {

    private final Long agendamentoId;
    private final String dataRotulo;

    public SerieAgendamentoOcorrencia(Long agendamentoId, String dataRotulo) {
        this.agendamentoId = agendamentoId;
        this.dataRotulo = dataRotulo;
    }
}
