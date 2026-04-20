package com.clinica.sistema.repository;

import com.clinica.sistema.model.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {
    List<Agendamento> findAllByOrderByDataHoraInicioAsc();

    List<Agendamento> findByProfissionalIdOrderByDataHoraInicioAsc(Long profissionalId);

    List<Agendamento> findBySalaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
            Long salaId,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    List<Agendamento> findTop20BySalaIdOrderByDataHoraInicioDesc(Long salaId);

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
}
