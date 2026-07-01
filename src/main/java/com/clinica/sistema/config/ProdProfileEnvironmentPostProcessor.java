package com.clinica.sistema.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Ativa perfil de producao conforme variaveis do host (PostgreSQL, MySQL/KingHost, Railway, Render).
 */
public class ProdProfileEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getActiveProfiles().length > 0) {
            return;
        }

        if (temMysqlHostRemoto(environment)) {
            environment.setActiveProfiles("mysql");
            return;
        }

        if (temVariavel(environment, "DATABASE_URL")
                || temVariavel(environment, "DATABASE_PUBLIC_URL")
                || temPgHostRemoto(environment)
                || temVariavel(environment, "RAILWAY_ENVIRONMENT")
                || temVariavel(environment, "RENDER")) {
            environment.setActiveProfiles("prod");
        }
    }

    private static boolean temVariavel(ConfigurableEnvironment environment, String nome) {
        String valor = environment.getProperty(nome);
        return valor != null && !valor.isBlank();
    }

    private static boolean temPgHostRemoto(ConfigurableEnvironment environment) {
        String pgHost = environment.getProperty("PGHOST");
        return pgHost != null && !pgHost.isBlank() && !"localhost".equalsIgnoreCase(pgHost);
    }

    private static boolean temMysqlHostRemoto(ConfigurableEnvironment environment) {
        String mysqlHost = environment.getProperty("MYSQL_HOST");
        return mysqlHost != null
                && !mysqlHost.isBlank()
                && !"localhost".equalsIgnoreCase(mysqlHost)
                && !"127.0.0.1".equals(mysqlHost);
    }
}
