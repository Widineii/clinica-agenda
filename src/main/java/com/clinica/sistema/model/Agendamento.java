package com.clinica.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Locale;

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

    @Column(name = "tipo_recorrencia")
    private String tipoRecorrencia;

    @Transient
    private String recorrencia;

    @Transient
    public boolean isQuinzenal() {
        if ("QUINZENAL".equalsIgnoreCase(recorrencia) || "QUINZENAL".equalsIgnoreCase(tipoRecorrencia)) {
            return true;
        }
        return possuiMarcadorSerie("quinzenal");
    }

    @Transient
    public boolean isFixoSemanal() {
        if ("SEMANAL".equalsIgnoreCase(recorrencia) || "SEMANAL".equalsIgnoreCase(tipoRecorrencia)) {
            return true;
        }
        if (isQuinzenal()) {
            return false;
        }
        return Boolean.TRUE.equals(fixo) && possuiMarcadorSerie("semanal");
    }

    private boolean possuiMarcadorSerie(String marcador) {
        if (serieFixaId == null || serieFixaId.isBlank()) {
            return false;
        }
        String id = serieFixaId.toLowerCase(Locale.ROOT);
        return id.startsWith(marcador + "-") || id.contains("-" + marcador + "-");
    }

    @Transient
    public boolean isAvulso() {
        return !Boolean.TRUE.equals(fixo);
    }

    @Transient
    public String getRecorrenciaLabel() {
        if (isQuinzenal()) {
            return "Quinzenal";
        }
        if (isFixoSemanal()) {
            return "Fixo";
        }
        return "Avulso";
    }
}
