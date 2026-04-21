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
import org.springframework.transaction.annotation.Transactional;

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
    private static final String RECORRENCIA_AVULSO = "AVULSO";
    private static final String RECORRENCIA_SEMANAL = "SEMANAL";
    private static final String RECORRENCIA_QUINZENAL = "QUINZENAL";

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

        if (seedDemoData) {
            resetarBaseDemonstracao();
            return;
        }

        if (deveSincronizarCargaInicial()) {
            sincronizarCargaInicialClinica();
            return;
        }

        garantirAdmin();
    }

    public void sincronizarCargaInicialClinica() {
        sincronizarUsuariosPadrao();
        sincronizarAgendamentosFixosPadrao();
    }

    @Transactional
    public Usuario resetarBaseDemonstracao() {
        agendamentoRepository.deleteAllInBatch();
        salaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();

        garantirSalas();
        sincronizarUsuariosPadrao();
        garantirAdmin();
        sincronizarAgendamentosFixosPadrao();

        String loginAdmin = (adminLogin != null && !adminLogin.isBlank()) ? adminLogin : "admin";
        return usuarioRepository.findByLogin(loginAdmin)
                .orElseThrow(() -> new RuntimeException("Nao foi possivel recriar o admin da demonstracao."));
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
                new UsuarioPadrao("Claudia", "claudia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Ana Paula", "anapaula", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Leticia", "leticia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Juliana Cristina", "julianacristina", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL"),
                new UsuarioPadrao("Larissa", "larissa", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL")
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

            String serieFixaId = PREFIXO_SERIE_FIXA_SEED + padrao.recorrencia().toLowerCase(Locale.ROOT) + "-" + padrao.chave();
            LocalDate primeiraData = padrao.dataInicial() != null
                    ? padrao.dataInicial()
                    : inicioBase.with(TemporalAdjusters.nextOrSame(padrao.diaSemana()));

            int repeticoes = obterRepeticoesSeed(padrao.recorrencia());
            int saltoSemanas = obterSaltoSeed(padrao.recorrencia());

            for (int semana = 0; semana < repeticoes; semana++) {
                LocalDateTime inicio = primeiraData.plusWeeks((long) semana * saltoSemanas).atTime(padrao.horario());
                Agendamento agendamento = new Agendamento();
                agendamento.setProfissional(profissional);
                agendamento.setSala(sala);
                agendamento.setNomeCliente(padrao.nomeCliente());
                agendamento.setDataHoraInicio(inicio);
                agendamento.setDataHoraFim(inicio.plusHours(1));
                agendamento.setFixo(!RECORRENCIA_AVULSO.equals(padrao.recorrencia()));
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

        adicionar(padroes, "rosi", "Sala 1", DayOfWeek.THURSDAY, 8, "Horario da semana", RECORRENCIA_AVULSO, "s1-qui-8-rosi");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.SATURDAY, 8, "Horario da semana", RECORRENCIA_AVULSO, "s1-sab-8-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.MONDAY, 9, "Horario da semana", RECORRENCIA_AVULSO, "s1-seg-9-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.TUESDAY, 9, "Horario da semana", RECORRENCIA_AVULSO, "s1-ter-9-polyana");
        adicionar(padroes, "juliano", "Sala 1", DayOfWeek.FRIDAY, 9, "Horario da semana", RECORRENCIA_AVULSO, "s1-sex-9-juliano");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.SATURDAY, 9, "Horario da semana", RECORRENCIA_AVULSO, "s1-sab-9-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.MONDAY, 10, "Horario da semana", RECORRENCIA_AVULSO, "s1-seg-10-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.TUESDAY, 10, "Horario da semana", RECORRENCIA_AVULSO, "s1-ter-10-polyana");
        adicionar(padroes, "julia", "Sala 1", DayOfWeek.WEDNESDAY, 10, "Bernardo", RECORRENCIA_AVULSO, "s1-qua-10-julia-bernardo");
        adicionar(padroes, "jessicamota", "Sala 1", DayOfWeek.THURSDAY, 10, "Horario da semana", RECORRENCIA_AVULSO, "s1-qui-10-jmota");
        adicionar(padroes, "juliano", "Sala 1", DayOfWeek.FRIDAY, 10, "Horario da semana", RECORRENCIA_AVULSO, "s1-sex-10-juliano");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.SATURDAY, 10, "Horario da semana", RECORRENCIA_AVULSO, "s1-sab-10-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.MONDAY, 11, "Horario da semana", RECORRENCIA_AVULSO, "s1-seg-11-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.TUESDAY, 11, "Horario da semana", RECORRENCIA_AVULSO, "s1-ter-11-polyana");
        adicionar(padroes, "juliano", "Sala 1", DayOfWeek.FRIDAY, 11, "Horario da semana", RECORRENCIA_AVULSO, "s1-sex-11-juliano");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.SATURDAY, 11, "Horario da semana", RECORRENCIA_AVULSO, "s1-sab-11-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.MONDAY, 12, "Horario da semana", RECORRENCIA_AVULSO, "s1-seg-12-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.TUESDAY, 12, "Horario da semana", RECORRENCIA_AVULSO, "s1-ter-12-polyana");
        adicionar(padroes, "juliano", "Sala 1", DayOfWeek.FRIDAY, 12, "Horario da semana", RECORRENCIA_AVULSO, "s1-sex-12-juliano");
        adicionar(padroes, "itamara", "Sala 1", DayOfWeek.MONDAY, 13, "FIXO novo", RECORRENCIA_SEMANAL, "s1-seg-13-itamara");
        adicionar(padroes, "jessicamota", "Sala 1", DayOfWeek.THURSDAY, 13, "Jessica Mota", RECORRENCIA_AVULSO, "s1-qui-13-jmota");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.FRIDAY, 13, "Ana Paula", RECORRENCIA_AVULSO, "s1-sex-13-polyana-anapaula");
        adicionar(padroes, "breno", "Sala 1", DayOfWeek.TUESDAY, 14, "FIXO", RECORRENCIA_SEMANAL, "s1-ter-14-breno");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.WEDNESDAY, 14, "Horario da semana", RECORRENCIA_AVULSO, "s1-qua-14-polyana");
        adicionar(padroes, "jessicamota", "Sala 1", DayOfWeek.THURSDAY, 14, "Horario da semana", RECORRENCIA_AVULSO, "s1-qui-14-jmota");
        adicionar(padroes, "anapaula", "Sala 1", DayOfWeek.FRIDAY, 14, "10/04", RECORRENCIA_AVULSO, "s1-sex-14-anapaula");
        adicionar(padroes, "leticia", "Sala 1", DayOfWeek.TUESDAY, 15, "Horario da semana", RECORRENCIA_AVULSO, "s1-ter-15-leticia");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.WEDNESDAY, 15, "Horario da semana", RECORRENCIA_AVULSO, "s1-qua-15-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.THURSDAY, 15, "Horario da semana", RECORRENCIA_AVULSO, "s1-qui-15-polyana");
        adicionar(padroes, "itamara", "Sala 1", DayOfWeek.FRIDAY, 15, "FIXO", RECORRENCIA_SEMANAL, "s1-sex-15-itamara");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.THURSDAY, 16, "Horario da semana", RECORRENCIA_AVULSO, "s1-qui-16-polyana");
        adicionar(padroes, "julia", "Sala 1", DayOfWeek.WEDNESDAY, 17, "Pedro", RECORRENCIA_AVULSO, "s1-qua-17-julia-pedro");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.THURSDAY, 17, "Horario da semana", RECORRENCIA_AVULSO, "s1-qui-17-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.FRIDAY, 17, "Horario da semana", RECORRENCIA_AVULSO, "s1-sex-17-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.MONDAY, 18, "Horario da semana", RECORRENCIA_AVULSO, "s1-seg-18-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.TUESDAY, 18, "Horario da semana", RECORRENCIA_AVULSO, "s1-ter-18-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.WEDNESDAY, 18, "Horario da semana", RECORRENCIA_AVULSO, "s1-qua-18-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.THURSDAY, 18, "Horario da semana", RECORRENCIA_AVULSO, "s1-qui-18-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.TUESDAY, 19, "Horario da semana", RECORRENCIA_AVULSO, "s1-ter-19-polyana");
        adicionar(padroes, "polyana", "Sala 1", DayOfWeek.WEDNESDAY, 19, "Horario da semana", RECORRENCIA_AVULSO, "s1-qua-19-polyana");
        adicionar(padroes, "mariapaula", "Sala 1", DayOfWeek.TUESDAY, 20, "FIXO", RECORRENCIA_SEMANAL, "s1-ter-20-mpaula");

        adicionar(padroes, "andreia", "Sala 2", DayOfWeek.WEDNESDAY, 8, "FIXO", RECORRENCIA_SEMANAL, "s2-qua-8-andreia");
        adicionar(padroes, "jessicamota", "Sala 2", DayOfWeek.THURSDAY, 8, "Lucas", RECORRENCIA_AVULSO, "s2-qui-8-jmota");
        adicionar(padroes, "carol", "Sala 2", DayOfWeek.TUESDAY, 9, "FIXO", RECORRENCIA_SEMANAL, "s2-ter-9-carol");
        adicionar(padroes, "luiza", "Sala 2", DayOfWeek.WEDNESDAY, 9, "FIXO", RECORRENCIA_SEMANAL, "s2-qua-9-luiza");
        adicionar(padroes, "jessicamota", "Sala 2", DayOfWeek.THURSDAY, 9, "Davi", RECORRENCIA_AVULSO, "s2-qui-9-jmota");
        adicionar(padroes, "carol", "Sala 2", DayOfWeek.TUESDAY, 10, "Troca de horario", RECORRENCIA_AVULSO, "s2-ter-10-carol");
        adicionar(padroes, "luiza", "Sala 2", DayOfWeek.WEDNESDAY, 10, "FIXO", RECORRENCIA_SEMANAL, "s2-qua-10-luiza");
        adicionar(padroes, "luiza", "Sala 2", DayOfWeek.FRIDAY, 10, "FIXO", RECORRENCIA_SEMANAL, "s2-sex-10-luiza");
        adicionar(padroes, "itamara", "Sala 2", DayOfWeek.SATURDAY, 10, "Horario da semana", RECORRENCIA_AVULSO, "s2-sab-10-itamara");
        adicionar(padroes, "jessicamota", "Sala 2", DayOfWeek.THURSDAY, 11, "Heitor", RECORRENCIA_AVULSO, "s2-qui-11-jmota");
        adicionar(padroes, "carol", "Sala 2", DayOfWeek.FRIDAY, 11, "Horario da semana", RECORRENCIA_AVULSO, "s2-sex-11-carol");
        adicionar(padroes, "julia", "Sala 2", DayOfWeek.SATURDAY, 11, "Lucas", RECORRENCIA_SEMANAL, "s2-sab-11-julia");
        adicionar(padroes, "julianacristina", "Sala 2", DayOfWeek.MONDAY, 13, "Horario da semana", RECORRENCIA_AVULSO, "s2-seg-13-julianacristina");
        adicionar(padroes, "itamara", "Sala 2", DayOfWeek.FRIDAY, 13, "10/4", RECORRENCIA_AVULSO, "s2-sex-13-itamara");
        adicionar(padroes, "andreia", "Sala 2", DayOfWeek.WEDNESDAY, 14, "FIXO", RECORRENCIA_SEMANAL, "s2-qua-14-andreia");
        adicionar(padroes, "jessicamota", "Sala 2", DayOfWeek.THURSDAY, 15, "Flavia", RECORRENCIA_AVULSO, "s2-qui-15-jmota");
        adicionar(padroes, "anapaula", "Sala 2", DayOfWeek.FRIDAY, 15, "Horario da semana", RECORRENCIA_AVULSO, "s2-sex-15-anapaula");
        adicionar(padroes, "cibele", "Sala 2", DayOfWeek.TUESDAY, 16, "Horario da semana", RECORRENCIA_AVULSO, "s2-ter-16-cibele");
        adicionar(padroes, "julia", "Sala 2", DayOfWeek.WEDNESDAY, 16, "Enzo", RECORRENCIA_AVULSO, "s2-qua-16-julia");
        adicionar(padroes, "anapaula", "Sala 2", DayOfWeek.FRIDAY, 16, "Horario da semana", RECORRENCIA_AVULSO, "s2-sex-16-anapaula");
        adicionar(padroes, "carol", "Sala 2", DayOfWeek.MONDAY, 17, "Horario da semana", RECORRENCIA_AVULSO, "s2-seg-17-carol");
        adicionar(padroes, "luiza", "Sala 2", DayOfWeek.WEDNESDAY, 17, "FIXO", RECORRENCIA_SEMANAL, "s2-qua-17-luiza");
        adicionar(padroes, "anapaula", "Sala 2", DayOfWeek.FRIDAY, 17, "Horario da semana", RECORRENCIA_AVULSO, "s2-sex-17-anapaula");
        adicionar(padroes, "carol", "Sala 2", DayOfWeek.MONDAY, 18, "Horario da semana", RECORRENCIA_AVULSO, "s2-seg-18-carol");
        adicionar(padroes, "luiza", "Sala 2", DayOfWeek.TUESDAY, 18, "FIXO", RECORRENCIA_SEMANAL, "s2-ter-18-luiza");
        adicionar(padroes, "luiza", "Sala 2", DayOfWeek.WEDNESDAY, 18, "FIXO", RECORRENCIA_SEMANAL, "s2-qua-18-luiza");
        adicionar(padroes, "luiza", "Sala 2", DayOfWeek.THURSDAY, 18, "Somente 16/04", RECORRENCIA_AVULSO, "s2-qui-18-luiza");
        adicionar(padroes, "julia", "Sala 2", DayOfWeek.FRIDAY, 18, "Joao", RECORRENCIA_AVULSO, "s2-sex-18-julia");
        adicionar(padroes, "luiza", "Sala 2", DayOfWeek.TUESDAY, 19, "Arthur Azevedo - 1o C", RECORRENCIA_AVULSO, "s2-ter-19-luiza");
        adicionar(padroes, "luiza", "Sala 2", DayOfWeek.WEDNESDAY, 19, "FIXO", RECORRENCIA_SEMANAL, "s2-qua-19-luiza");
        adicionar(padroes, "julia", "Sala 2", DayOfWeek.THURSDAY, 19, "Giovanna - uso 30/04", RECORRENCIA_AVULSO, "s2-qui-19-julia-giovanna-3004", LocalDate.of(2026, 4, 30));

        adicionar(padroes, "bruna", "Sala 3", DayOfWeek.SATURDAY, 8, "Horario da semana", RECORRENCIA_AVULSO, "s3-sab-8-bruna");
        adicionar(padroes, "andreia", "Sala 3", DayOfWeek.TUESDAY, 9, "Maria Helena", RECORRENCIA_AVULSO, "s3-ter-9-andreia");
        adicionar(padroes, "rosi", "Sala 3", DayOfWeek.THURSDAY, 9, "Horario da semana", RECORRENCIA_AVULSO, "s3-qui-9-rosi");
        adicionar(padroes, "anapaula", "Sala 3", DayOfWeek.TUESDAY, 10, "Horario da semana", RECORRENCIA_AVULSO, "s3-ter-10-anapaula");
        adicionar(padroes, "andreia", "Sala 3", DayOfWeek.WEDNESDAY, 10, "FIXO", RECORRENCIA_SEMANAL, "s3-qua-10-andreia");
        adicionar(padroes, "rosi", "Sala 3", DayOfWeek.THURSDAY, 10, "Horario da semana", RECORRENCIA_AVULSO, "s3-qui-10-rosi");
        adicionar(padroes, "bruna", "Sala 3", DayOfWeek.SATURDAY, 10, "Horario da semana", RECORRENCIA_AVULSO, "s3-sab-10-bruna");
        adicionar(padroes, "anapaula", "Sala 3", DayOfWeek.TUESDAY, 11, "A partir 07/04", RECORRENCIA_AVULSO, "s3-ter-11-anapaula");
        adicionar(padroes, "rosi", "Sala 3", DayOfWeek.THURSDAY, 11, "Horario da semana", RECORRENCIA_AVULSO, "s3-qui-11-rosi");
        adicionar(padroes, "cibele", "Sala 3", DayOfWeek.TUESDAY, 12, "Horario da semana", RECORRENCIA_AVULSO, "s3-ter-12-cibele");
        adicionar(padroes, "cibele", "Sala 3", DayOfWeek.FRIDAY, 12, "Horario da semana", RECORRENCIA_AVULSO, "s3-sex-12-cibele");
        adicionar(padroes, "rosi", "Sala 3", DayOfWeek.THURSDAY, 14, "Horario da semana", RECORRENCIA_AVULSO, "s3-qui-14-rosi");
        adicionar(padroes, "jessicahouri", "Sala 3", DayOfWeek.TUESDAY, 15, "Horario da semana", RECORRENCIA_AVULSO, "s3-ter-15-jhouri");
        adicionar(padroes, "jessicahouri", "Sala 3", DayOfWeek.WEDNESDAY, 15, "Brenner", RECORRENCIA_AVULSO, "s3-qua-15-jhouri");
        adicionar(padroes, "rosi", "Sala 3", DayOfWeek.THURSDAY, 15, "Horario da semana", RECORRENCIA_AVULSO, "s3-qui-15-rosi");
        adicionar(padroes, "jessicahouri", "Sala 3", DayOfWeek.FRIDAY, 15, "Regina", RECORRENCIA_AVULSO, "s3-sex-15-jhouri");
        adicionar(padroes, "jessicahouri", "Sala 3", DayOfWeek.TUESDAY, 16, "FIXO", RECORRENCIA_SEMANAL, "s3-ter-16-jhouri");
        adicionar(padroes, "itamara", "Sala 3", DayOfWeek.WEDNESDAY, 16, "FIXO", RECORRENCIA_SEMANAL, "s3-qua-16-itamara");
        adicionar(padroes, "jessicamota", "Sala 3", DayOfWeek.THURSDAY, 16, "Flavia", RECORRENCIA_AVULSO, "s3-qui-16-jmota");
        adicionar(padroes, "jessicahouri", "Sala 3", DayOfWeek.TUESDAY, 17, "FIXO", RECORRENCIA_SEMANAL, "s3-ter-17-jhouri");
        adicionar(padroes, "jessicahouri", "Sala 3", DayOfWeek.WEDNESDAY, 17, "FIXO", RECORRENCIA_SEMANAL, "s3-qua-17-jhouri");
        adicionar(padroes, "jessicahouri", "Sala 3", DayOfWeek.TUESDAY, 18, "Horario da semana", RECORRENCIA_AVULSO, "s3-ter-18-jhouri");
        adicionar(padroes, "anapaula", "Sala 3", DayOfWeek.FRIDAY, 18, "Horario da semana", RECORRENCIA_AVULSO, "s3-sex-18-anapaula");
        adicionar(padroes, "jessicahouri", "Sala 3", DayOfWeek.TUESDAY, 19, "Regina", RECORRENCIA_AVULSO, "s3-ter-19-jhouri");

        adicionar(padroes, "andreia", "Sala 4", DayOfWeek.TUESDAY, 8, "FIXO", RECORRENCIA_SEMANAL, "s4-ter-8-andreia");
        adicionar(padroes, "andreia", "Sala 4", DayOfWeek.THURSDAY, 8, "FIXO", RECORRENCIA_SEMANAL, "s4-qui-8-andreia");
        adicionar(padroes, "andreia", "Sala 4", DayOfWeek.THURSDAY, 9, "Horario da semana", RECORRENCIA_AVULSO, "s4-qui-9-andreia");
        adicionar(padroes, "julia", "Sala 4", DayOfWeek.SATURDAY, 11, "Sublocacao - Lucas", RECORRENCIA_AVULSO, "s4-sab-11-julia");
        adicionar(padroes, "larissa", "Sala 4", DayOfWeek.THURSDAY, 13, "Horario da semana", RECORRENCIA_AVULSO, "s4-qui-13-larissa");
        adicionar(padroes, "larissa", "Sala 4", DayOfWeek.THURSDAY, 15, "Horario da semana", RECORRENCIA_AVULSO, "s4-qui-15-larissa");
        adicionar(padroes, "andreia", "Sala 4", DayOfWeek.MONDAY, 16, "FIXO", RECORRENCIA_SEMANAL, "s4-seg-16-andreia");
        adicionar(padroes, "andreia", "Sala 4", DayOfWeek.WEDNESDAY, 16, "FIXO", RECORRENCIA_SEMANAL, "s4-qua-16-andreia");
        adicionar(padroes, "larissa", "Sala 4", DayOfWeek.THURSDAY, 16, "Horario da semana", RECORRENCIA_AVULSO, "s4-qui-16-larissa");
        adicionar(padroes, "claudia", "Sala 4", DayOfWeek.THURSDAY, 18, "Dia 26", RECORRENCIA_AVULSO, "s4-qui-18-claudia");
        adicionar(padroes, "claudia", "Sala 4", DayOfWeek.TUESDAY, 19, "Dia 24", RECORRENCIA_AVULSO, "s4-ter-19-claudia");
        adicionar(padroes, "claudia", "Sala 4", DayOfWeek.THURSDAY, 19, "Dia 26", RECORRENCIA_AVULSO, "s4-qui-19-claudia");

        return padroes.stream()
                .sorted(Comparator.comparing(AgendamentoFixoPadrao::nomeSala)
                        .thenComparing(AgendamentoFixoPadrao::diaSemana)
                        .thenComparing(AgendamentoFixoPadrao::horario))
                .toList();
    }

    private int obterRepeticoesSeed(String recorrencia) {
        return switch (recorrencia) {
            case RECORRENCIA_SEMANAL -> SEMANAS_FIXAS_PADRAO;
            case RECORRENCIA_QUINZENAL -> 6;
            default -> 1;
        };
    }

    private int obterSaltoSeed(String recorrencia) {
        return RECORRENCIA_QUINZENAL.equals(recorrencia) ? 2 : 1;
    }

    private void adicionar(
            List<AgendamentoFixoPadrao> padroes,
            String loginProfissional,
            String nomeSala,
            DayOfWeek diaSemana,
            int hora,
            String nomeCliente,
            String recorrencia,
            String chave
    ) {
        adicionar(padroes, loginProfissional, nomeSala, diaSemana, hora, nomeCliente, recorrencia, chave, null);
    }

    private void adicionar(
            List<AgendamentoFixoPadrao> padroes,
            String loginProfissional,
            String nomeSala,
            DayOfWeek diaSemana,
            int hora,
            String nomeCliente,
            String recorrencia,
            String chave,
            LocalDate dataInicial
    ) {
        padroes.add(new AgendamentoFixoPadrao(
                loginProfissional,
                nomeSala,
                diaSemana,
                LocalTime.of(hora, 0),
                nomeCliente,
                recorrencia,
                chave,
                dataInicial
        ));
    }

    private record UsuarioPadrao(String nome, String login, String senha, String cargo) {
    }

    private record AgendamentoFixoPadrao(
            String loginProfissional,
            String nomeSala,
            DayOfWeek diaSemana,
            LocalTime horario,
            String nomeCliente,
            String recorrencia,
            String chave,
            LocalDate dataInicial
    ) {
    }
}
