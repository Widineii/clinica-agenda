package com.clinica.sistema.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class PostgresUrlParser {

    private PostgresUrlParser() {
    }

    record ParsedDatasource(String jdbcUrl, String username, String password) {
    }

    static ParsedDatasource parse(String databaseUrl) {
        String normalizada = databaseUrl.trim();
        if (normalizada.startsWith("postgres://")) {
            normalizada = "postgresql://" + normalizada.substring("postgres://".length());
        }
        if (!normalizada.startsWith("postgresql://")) {
            throw new IllegalArgumentException("DATABASE_URL invalida para PostgreSQL.");
        }

        URI uri = URI.create(normalizada);
        String username = "";
        String password = "";
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            String[] credenciais = uri.getUserInfo().split(":", 2);
            username = decode(credenciais[0]);
            if (credenciais.length > 1) {
                password = decode(credenciais[1]);
            }
        }

        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String database = uri.getPath() != null ? uri.getPath().replaceFirst("^/", "") : "railway";
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode=require";

        return new ParsedDatasource(jdbcUrl, username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
