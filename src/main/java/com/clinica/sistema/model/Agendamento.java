package com.clinica.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "agendamentos")
@Data
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario profissional;

    @ManyToOne
    @JoinColumn(name = "id_sala")
    private Sala sala; // O Repository usa esse nome 'sala' para o 'BySalaId'

    private String nomeCliente;

    // O Repository usa esse nome 'dataHoraInicio' para o 'AndDataHoraInicio'
    private LocalDateTime dataHoraInicio;

    private LocalDateTime dataHoraFim;

    private Boolean fixo;

    private String serieFixaId;
}
