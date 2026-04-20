package com.clinica.sistema.repository;

import com.clinica.sistema.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLogin(String login);

    List<Usuario> findByCargoOrderByNomeAsc(String cargo);
}
