package com.clinica.sistema.repository;

import com.clinica.sistema.model.RelatorioMensalArquivado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelatorioMensalArquivadoRepository extends JpaRepository<RelatorioMensalArquivado, Long> {

    Optional<RelatorioMensalArquivado> findByAnoAndMes(int ano, int mes);

    boolean existsByAnoAndMes(int ano, int mes);

    List<RelatorioMensalArquivado> findAllByOrderByAnoDescMesDesc();

    List<RelatorioMensalArquivado> findByPdfIsNotNull();
}
