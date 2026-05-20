package com.clinica.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "relatorios_mensais_arquivados",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ano", "mes"})
)
@Data
public class RelatorioMensalArquivado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int ano;

    private int mes;

    @Column(nullable = false)
    private String mesLabel;

    private long totalGeral;

    private long agendamentosRemovidos;

    @Column(nullable = false)
    private LocalDateTime geradoEm;

    @Lob
    @Column(nullable = false)
    private byte[] pdf;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String dadosJson;
}
