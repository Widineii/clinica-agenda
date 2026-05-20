package com.clinica.sistema.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("prod")
public class PostgresDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${DATABASE_URL:}") String databaseUrl,
            @Value("${PGHOST:localhost}") String pgHost,
            @Value("${PGPORT:5432}") String pgPort,
            @Value("${PGDATABASE:railway}") String pgDatabase,
            @Value("${PGUSER:postgres}") String pgUser,
            @Value("${PGPASSWORD:}") String pgPassword
    ) {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(20000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1200000);
        config.setDriverClassName("org.postgresql.Driver");

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            PostgresUrlParser.ParsedDatasource parsed = PostgresUrlParser.parse(databaseUrl);
            config.setJdbcUrl(parsed.jdbcUrl());
            config.setUsername(parsed.username());
            config.setPassword(parsed.password());
        } else {
            config.setJdbcUrl("jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDatabase + "?sslmode=require");
            config.setUsername(pgUser);
            config.setPassword(pgPassword);
        }

        return new HikariDataSource(config);
    }
}
