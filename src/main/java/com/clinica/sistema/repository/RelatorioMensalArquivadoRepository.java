package com.clinica.sistema.repository;

import com.clinica.sistema.dto.RelatorioHistoricoMetadadosProjection;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelatorioMensalArquivadoRepository extends JpaRepository<RelatorioMensalArquivado, Long> {

    Optional<RelatorioMensalArquivado> findByAnoAndMes(int ano, int mes);

    boolean existsByAnoAndMes(int ano, int mes);

    List<RelatorioMensalArquivado> findAllByOrderByAnoDescMesDesc();

    @Query("""
            SELECT r.ano AS ano, r.mes AS mes, r.mesLabel AS mesLabel, r.dadosJson AS dadosJson
            FROM RelatorioMensalArquivado r
            ORDER BY r.ano DESC, r.mes DESC
            """)
    List<RelatorioHistoricoMetadadosProjection> listarHistoricoMetadados();

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM RelatorioMensalArquivado r
            WHERE r.ano = :ano
              AND r.mes = :mes
              AND r.dadosJson IS NOT NULL
              AND r.dadosJson <> ''
            """)
    boolean existsComDadosJson(@Param("ano") int ano, @Param("mes") int mes);

    List<RelatorioMensalArquivado> findByPdfIsNotNull();
}
