package com.clinica.sistema.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Ativa o perfil {@code prod} automaticamente quando o Railway (ou outro host)
 * injeta variáveis do PostgreSQL, para os agendamentos não ficarem em H2 em memória.
 */
public class ProdProfileEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getActiveProfiles().length > 0) {
            return;
        }

        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            environment.setActiveProfiles("prod");
            return;
        }

        String pgHost = environment.getProperty("PGHOST");
        if (pgHost != null && !pgHost.isBlank() && !"localhost".equalsIgnoreCase(pgHost)) {
            environment.setActiveProfiles("prod");
        }
    }
}
