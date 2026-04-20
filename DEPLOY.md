# Publicar para cliente

## O que ja esta pronto

- Ambiente local: perfil `local` com H2 e usuarios de demonstracao
- Ambiente publico: perfil `prod` com PostgreSQL por variaveis de ambiente
- Dockerfile para deploy em plataformas como Render e Railway
- Porta configurada por `PORT`

## Variaveis obrigatorias em producao

- `SPRING_PROFILES_ACTIVE=prod`
- `PGHOST`
- `PGPORT`
- `PGDATABASE`
- `PGUSER`
- `PGPASSWORD`
- `ADMIN_LOGIN`
- `ADMIN_PASSWORD`
- `ADMIN_NAME` opcional

## Exemplo de acesso inicial

- `ADMIN_LOGIN=admin`
- `ADMIN_PASSWORD=troque-essa-senha`

## Render

1. Suba este projeto para um repositorio GitHub.
2. Crie um banco PostgreSQL no Render.
3. Crie um Web Service apontando para este repositorio.
4. Use Docker ou build nativo Maven.
5. Configure as variaveis acima.
6. Gere o dominio publico.

## Railway

1. Suba este projeto para um repositorio GitHub.
2. Crie um projeto no Railway.
3. Adicione um PostgreSQL ao projeto.
4. Publique este repositorio no mesmo projeto.
5. Configure `SPRING_PROFILES_ACTIVE=prod`, `ADMIN_LOGIN` e `ADMIN_PASSWORD`.
6. Gere o dominio publico.

## Observacoes

- Em producao nao entram usuarios demo automaticamente.
- As 4 salas sao criadas automaticamente na primeira execucao.
- O admin inicial so e criado se `ADMIN_LOGIN` e `ADMIN_PASSWORD` forem informados.
