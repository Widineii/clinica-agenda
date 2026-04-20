package com.clinica.sistema.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AgendamentoForm {
    private Long profissionalId;
    private Long salaId;
    private String nomeCliente;
    private LocalDate dataAtendimento;
    private LocalTime horarioAtendimento;
}
