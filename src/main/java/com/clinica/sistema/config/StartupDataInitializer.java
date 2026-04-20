package com.clinica.sistema.config;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public class StartupDataInitializer implements CommandLineRunner {
    private static final String SENHA_PROFISSIONAIS_PADRAO = "297b";
    private static final int SEMANAS_FIXAS_PADRAO = 12;
    private static final String PREFIXO_SERIE_FIXA_SEED = "seed-fixo-";

    private final SalaRepository salaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    @Value("${app.seed-admin-login:}")
    private String adminLogin;

    @Value("${app.seed-admin-password:}")
    private String adminPassword;

    @Value("${app.seed-admin-name:Administracao}")
    private String adminName;

    public StartupDataInitializer(
            SalaRepository salaRepository,
            UsuarioRepository usuarioRepository,
            AgendamentoRepository agendamentoRepository
    ) {
        this.salaRepository = salaRepository;
        this.usuarioRepository = usuarioRepository;
        this.agendamentoRepository = agendamentoRepository;
    }

    @Override
    public void run(String... args) {
        garantirSalas();

        if (deveSincronizarCargaInicial()) {
            sincronizarCargaInicialClinica();
        } else {
            garantirAdmin();
        }
    }

    public void sincronizarCargaInicialClinica() {
        sincronizarUsuariosPadrao();
        sincronizarAgendamentosFixosPadrao();
    }

    private boolean deveSincronizarCargaInicial() {
        if (seedDemoData) {
            return true;
        }

        long usuariosAtendentes = usuarioRepository.findAll().stream()
                .filter(usuario -> !"admin".equalsIgnoreCase(usuario.getLogin()))
                .count();

        return usuariosAtendentes == 0;
    }

    private void garantirSalas() {
        List<String> salas = List.of("Sala 1", "Sala 2", "Sala 3", "Sala 4");
        for (String nomeSala : salas) {
            boolean existe = salaRepository.findAllByOrderByNomeAsc().stream()
                    .anyMatch(sala -> sala.getNome().equalsIgnoreCase(nomeSala));
            if (!existe) {
                Sala sala = new Sala();
                sala.setNome(nomeSala);
                salaRepository.save(sala);
            }
        }
    }

    private void garantirAdmin() {
        if (adminLogin == null || adminLogin.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }

        salvarOuAtualizarUsuario(new UsuarioPadrao(
                adminName,
                adminLogin,
                adminPassword,
                "ROLE_ADMIN"
        ));
    }

    private void sincronizarUsuariosPadrao() {
        List<UsuarioPadrao> usuariosPadrao = List.of(
                new UsuarioPadrao("Administrador do Sistema", "admin", "Luquinha12@", "ROLE_ADMIN"),
                new UsuarioPadrao("Polyana", "polyana", SENHA_PROFISSIONAIS_PADRAO, "ROLE_ADMIN"),
                new UsuarioPadrao("Carol", "carol", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Itamara", "itamara", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Julia", "julia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Juliano", "juliano", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Jessica Mota", "jessicamota", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Jessica Houri", "jessicahouri", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Rosi", "rosi", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Maria Paula", "mariapaula", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Breno", "breno", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Tathiane", "tathiane", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Luiza", "luiza", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Andreia", "andreia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Cibele", "cibele", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Bruna", "bruna", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Claudia", "claudia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL")
        );

        removerUsuariosObsoletos(usuariosPadrao);
        usuariosPadrao.forEach(this::salvarOuAtualizarUsuario);
    }

    private void sincronizarAgendamentosFixosPadrao() {
        Map<String, Usuario> usuariosPorLogin = usuarioRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        usuario -> usuario.getLogin().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));
        Map<String, Sala> salasPorNome = salaRepository.findAllByOrderByNomeAsc().stream()
                .collect(java.util.stream.Collectors.toMap(
                        sala -> sala.getNome().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));

        List<AgendamentoFixoPadrao> padroes = listarAgendamentosFixosPadrao();
        List<Agendamento> novosAgendamentos = new ArrayList<>();
        LocalDate inicioBase = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        for (AgendamentoFixoPadrao padrao : padroes) {
            Usuario profissional = usuariosPorLogin.get(padrao.loginProfissional().toLowerCase(Locale.ROOT));
            Sala sala = salasPorNome.get(padrao.nomeSala().toLowerCase(Locale.ROOT));
            if (profissional == null || sala == null) {
                continue;
            }

            String serieFixaId = PREFIXO_SERIE_FIXA_SEED + padrao.chave();
            LocalDate primeiraData = inicioBase.with(TemporalAdjusters.nextOrSame(padrao.diaSemana()));

            for (int semana = 0; semana < SEMANAS_FIXAS_PADRAO; semana++) {
                LocalDateTime inicio = primeiraData.plusWeeks(semana).atTime(padrao.horario());
                Agendamento agendamento = new Agendamento();
                agendamento.setProfissional(profissional);
                agendamento.setSala(sala);
                agendamento.setNomeCliente(padrao.nomeCliente());
                agendamento.setDataHoraInicio(inicio);
                agendamento.setDataHoraFim(inicio.plusHours(1));
                agendamento.setFixo(true);
                agendamento.setSerieFixaId(serieFixaId);
                novosAgendamentos.add(agendamento);
            }
        }

        agendamentoRepository.deleteBySerieFixaIdStartingWith(PREFIXO_SERIE_FIXA_SEED);
        agendamentoRepository.saveAll(novosAgendamentos);
    }

    private void removerUsuariosObsoletos(List<UsuarioPadrao> usuariosPadrao) {
        Set<String> loginsPermitidos = usuariosPadrao.stream()
                .map(UsuarioPadrao::login)
                .collect(java.util.stream.Collectors.toSet());

        List<Usuario> usuariosObsoletos = usuarioRepository.findAll().stream()
                .filter(usuario -> !loginsPermitidos.contains(usuario.getLogin()))
                .toList();

        if (usuariosObsoletos.isEmpty()) {
            return;
        }

        List<Long> idsObsoletos = usuariosObsoletos.stream()
                .map(Usuario::getId)
                .toList();
        agendamentoRepository.deleteByProfissionalIdIn(idsObsoletos);
        usuarioRepository.deleteAllById(idsObsoletos);
    }

    private void salvarOuAtualizarUsuario(UsuarioPadrao usuarioPadrao) {
        Usuario profissional = usuarioRepository.findByLogin(usuarioPadrao.login())
                .orElseGet(Usuario::new);
        profissional.setNome(usuarioPadrao.nome());
        profissional.setLogin(usuarioPadrao.login());
        profissional.setSenha(usuarioPadrao.senha());
        profissional.setCargo(usuarioPadrao.cargo());
        usuarioRepository.save(profissional);
    }

    private List<AgendamentoFixoPadrao> listarAgendamentosFixosPadrao() {
        List<AgendamentoFixoPadrao> padroes = new ArrayList<>();

        padroes.add(new AgendamentoFixoPadrao("itamara", "Sala 1", DayOfWeek.MONDAY, LocalTime.of(13, 0), "FIXO novo", "s1-itamara-seg-13"));
        padroes.add(new AgendamentoFixoPadrao("breno", "Sala 1", DayOfWeek.TUESDAY, LocalTime.of(14, 0), "FIXO", "s1-breno-ter-14"));
        padroes.add(new AgendamentoFixoPadrao("itamara", "Sala 1", DayOfWeek.FRIDAY, LocalTime.of(15, 0), "FIXO", "s1-itamara-sex-15"));
        padroes.add(new AgendamentoFixoPadrao("mariapaula", "Sala 1", DayOfWeek.TUESDAY, LocalTime.of(20, 0), "FIXO", "s1-mariapaula-ter-20"));

        padroes.add(new AgendamentoFixoPadrao("carol", "Sala 2", DayOfWeek.TUESDAY, LocalTime.of(9, 0), "FIXO", "s2-carol-ter-9"));
        padroes.add(new AgendamentoFixoPadrao("julia", "Sala 2", DayOfWeek.MONDAY, LocalTime.of(10, 0), "Bernardo", "s2-julia-seg-10-bernardo"));
        padroes.add(new AgendamentoFixoPadrao("andreia", "Sala 2", DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), "FIXO", "s2-andreia-qua-8"));
        padroes.add(new AgendamentoFixoPadrao("luiza", "Sala 2", DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), "FIXO", "s2-luiza-qua-9"));
        padroes.add(new AgendamentoFixoPadrao("luiza", "Sala 2", DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), "FIXO", "s2-luiza-qua-10"));
        padroes.add(new AgendamentoFixoPadrao("andreia", "Sala 2", DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), "FIXO", "s2-andreia-qua-14"));
        padroes.add(new AgendamentoFixoPadrao("luiza", "Sala 2", DayOfWeek.TUESDAY, LocalTime.of(18, 0), "FIXO", "s2-luiza-ter-18"));
        padroes.add(new AgendamentoFixoPadrao("luiza", "Sala 2", DayOfWeek.FRIDAY, LocalTime.of(10, 0), "FIXO", "s2-luiza-sex-10"));
        padroes.add(new AgendamentoFixoPadrao("julia", "Sala 2", DayOfWeek.SATURDAY, LocalTime.of(11, 0), "Lucas", "s2-julia-sab-11-lucas"));

        padroes.add(new AgendamentoFixoPadrao("andreia", "Sala 3", DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), "FIXO", "s3-andreia-qua-10"));
        padroes.add(new AgendamentoFixoPadrao("jessicahouri", "Sala 3", DayOfWeek.TUESDAY, LocalTime.of(16, 0), "FIXO", "s3-jhouri-ter-16"));
        padroes.add(new AgendamentoFixoPadrao("jessicahouri", "Sala 3", DayOfWeek.TUESDAY, LocalTime.of(17, 0), "FIXO", "s3-jhouri-ter-17"));
        padroes.add(new AgendamentoFixoPadrao("itamara", "Sala 3", DayOfWeek.WEDNESDAY, LocalTime.of(16, 0), "FIXO", "s3-itamara-qua-16"));
        padroes.add(new AgendamentoFixoPadrao("jessicahouri", "Sala 3", DayOfWeek.WEDNESDAY, LocalTime.of(17, 0), "FIXO", "s3-jhouri-qua-17"));

        padroes.add(new AgendamentoFixoPadrao("andreia", "Sala 4", DayOfWeek.TUESDAY, LocalTime.of(8, 0), "FIXO", "s4-andreia-ter-8"));
        padroes.add(new AgendamentoFixoPadrao("andreia", "Sala 4", DayOfWeek.THURSDAY, LocalTime.of(8, 0), "FIXO", "s4-andreia-qui-8"));
        padroes.add(new AgendamentoFixoPadrao("andreia", "Sala 4", DayOfWeek.MONDAY, LocalTime.of(16, 0), "FIXO", "s4-andreia-seg-16"));
        padroes.add(new AgendamentoFixoPadrao("andreia", "Sala 4", DayOfWeek.WEDNESDAY, LocalTime.of(16, 0), "FIXO", "s4-andreia-qua-16"));

        return padroes.stream()
                .sorted(Comparator.comparing(AgendamentoFixoPadrao::nomeSala)
                        .thenComparing(AgendamentoFixoPadrao::diaSemana)
                        .thenComparing(AgendamentoFixoPadrao::horario))
                .toList();
    }

    private record UsuarioPadrao(String nome, String login, String senha, String cargo) {
    }

    private record AgendamentoFixoPadrao(
            String loginProfissional,
            String nomeSala,
            DayOfWeek diaSemana,
            LocalTime horario,
            String nomeCliente,
            String chave
    ) {
    }
}
