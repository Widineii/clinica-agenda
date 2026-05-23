package com.clinica.sistema.repository;

import com.clinica.sistema.dto.RelatorioHistoricoResumo;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelatorioMensalArquivadoRepository extends JpaRepository<RelatorioMensalArquivado, Long> {

    Optional<RelatorioMensalArquivado> findByAnoAndMes(int ano, int mes);

    boolean existsByAnoAndMes(int ano, int mes);

    List<RelatorioMensalArquivado> findAllByOrderByAnoDescMesDesc();

    @Query("""
            SELECT new com.clinica.sistema.dto.RelatorioHistoricoResumo(
                r.ano, r.mes, r.mesLabel,
                CASE WHEN r.dadosJson IS NOT NULL AND LENGTH(TRIM(r.dadosJson)) > 0 THEN true ELSE false END
            )
            FROM RelatorioMensalArquivado r
            ORDER BY r.ano DESC, r.mes DESC
            """)
    List<RelatorioHistoricoResumo> listarHistoricoResumo();

    List<RelatorioMensalArquivado> findByPdfIsNotNull();
}
