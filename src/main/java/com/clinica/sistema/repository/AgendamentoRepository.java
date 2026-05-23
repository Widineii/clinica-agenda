package com.clinica.sistema.repository;

import com.clinica.sistema.model.Agendamento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByProfissionalIdOrderByDataHoraInicioAsc(Long profissionalId);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
            LocalDateTime inicio,
            LocalDateTime fim
    );

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
            Long profissionalId,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findBySalaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
            Long salaId,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    List<Agendamento> findTop20BySalaIdOrderByDataHoraInicioDesc(Long salaId);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findTop20BySalaIdAndDataHoraInicioBeforeOrderByDataHoraInicioDesc(Long salaId, LocalDateTime referencia);

    boolean existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
            Long salaId,
            LocalDateTime dataHoraFim,
            LocalDateTime dataHoraInicio
    );

    boolean existsByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
            Long profissionalId,
            LocalDateTime dataHoraFim,
            LocalDateTime dataHoraInicio
    );

    @EntityGraph(attributePaths = {"sala"})
    Optional<Agendamento> findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
            Long profissionalId,
            LocalDateTime dataHoraFim,
            LocalDateTime dataHoraInicio
    );

    @Transactional
    @Modifying
    void deleteByProfissionalIdIn(List<Long> profissionalIds);

    List<Agendamento> findBySerieFixaIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
            String serieFixaId,
            LocalDateTime dataHoraInicio
    );

    @Transactional
    @Modifying
    void deleteBySerieFixaIdStartingWith(String prefix);

    @Query("""
            SELECT a.profissional.nome, a.sala.nome, COUNT(a)
            FROM Agendamento a
            WHERE a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
              AND a.profissional.cargo = 'ROLE_PROFISSIONAL'
            GROUP BY a.profissional.id, a.profissional.nome, a.sala.id, a.sala.nome
            ORDER BY a.profissional.nome ASC, a.sala.nome ASC
            """)
    List<Object[]> contarUsoSalasPorProfissionalNoPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    /**
     * Igual ao relatorio por periodo, mas so conta horarios cujo inicio ja passou
     * da regra das 24h (inicio + 24h &lt;= agora).
     */
    @Query("""
            SELECT a.profissional.nome, a.sala.nome, COUNT(a)
            FROM Agendamento a
            WHERE a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
              AND a.dataHoraInicio <= :corteConfirmadoApos24h
              AND a.profissional.cargo = 'ROLE_PROFISSIONAL'
            GROUP BY a.profissional.id, a.profissional.nome, a.sala.id, a.sala.nome
            ORDER BY a.profissional.nome ASC, a.sala.nome ASC
            """)
    List<Object[]> contarUsoSalasPorProfissionalNoPeriodoAposRegra24h(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("corteConfirmadoApos24h") LocalDateTime corteConfirmadoApos24h
    );

    long countByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(
            LocalDateTime inicio,
            LocalDateTime fim
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM Agendamento a
            WHERE a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
              AND (a.fixo IS NULL OR a.fixo = false)
              AND (a.tipoRecorrencia IS NULL OR UPPER(a.tipoRecorrencia) NOT IN ('SEMANAL', 'QUINZENAL'))
              AND (
                    a.serieFixaId IS NULL
                    OR (
                        LOWER(a.serieFixaId) NOT LIKE 'semanal-%'
                        AND LOWER(a.serieFixaId) NOT LIKE '%-semanal-%'
                        AND LOWER(a.serieFixaId) NOT LIKE 'quinzenal-%'
                        AND LOWER(a.serieFixaId) NOT LIKE '%-quinzenal-%'
                    )
              )
            """)
    int deleteAvulsosByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM Agendamento a
            WHERE a.nomeCliente LIKE :prefixo
            """)
    int deleteByNomeClienteLike(@Param("prefixo") String prefixo);
}
