# Deploy no Railway com PostgreSQL (entrega ao cliente)

## Por que PostgreSQL (obrigatório para o cliente)

**Sem PostgreSQL**, o sistema usa banco em memória (H2): ao reiniciar o site, **todos os agendamentos somem**.

**Com PostgreSQL ligado ao app no Railway**, os agendamentos ficam no disco do banco — reinício, deploy ou queda do app **não apagam** nada.

- Vários profissionais podem usar **ao mesmo tempo** sem perder dados
- Backup automático no Railway

---

## Passo a passo (amanhã)

### 1. Subir código no GitHub

```bash
cd clinica-agenda-main
git init
git add .
git commit -m "Producao com PostgreSQL e seguranca"
git remote add origin https://github.com/Widineii/clinica-agenda.git
git push -u origin main
```

### 2. No Railway

1. Acesse [railway.app](https://railway.app)
2. **New Project** → **Deploy from GitHub** → escolha `clinica-agenda`
3. No projeto, clique **+ New** → **Database** → **PostgreSQL**
4. Clique no serviço do **app** (não no banco) → **Variables**
5. Adicione ou confira:

| Variável | Valor |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `ADMIN_LOGIN` | `admin` |
| `ADMIN_PASSWORD` | senha forte (anote em lugar seguro) |
| `ADMIN_NAME` | `Administracao` |

6. **Conectar o banco ao app:** no serviço PostgreSQL → **Connect** → **Add to service** (selecione o app web)  
   O Railway preenche automaticamente: `DATABASE_URL`, `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`

7. **Deploy** → aguarde ficar **Active**
8. Abra o domínio público (ex.: `clinica-agenda-production.up.railway.app`)

### 3. Primeiro acesso

- Login: valor de `ADMIN_LOGIN` (ex.: `admin`)
- Senha: valor de `ADMIN_PASSWORD`
- Profissionais (Polyana, Julia, etc.): login de cada um, senha inicial `297b` — **pedir para trocar** em Trocar senha

### 4. Teste rápido antes de entregar

- [ ] Login admin
- [ ] Login Polyana (`polyana` / `297b`)
- [ ] Criar 1 agendamento fixo
- [ ] Cancelar 1 dia
- [ ] Recarregar a página — agendamento continua correto
- [ ] Trocar senha de teste

---

## Testar PostgreSQL no seu PC (opcional)

```bash
docker compose up -d
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=postgres-local
```

Acesse http://localhost:8081

---

## Se o deploy falhar

1. **Logs** do serviço no Railway (últimas linhas)
2. Confirme que o **PostgreSQL está ligado** ao app (variáveis `PGHOST` ou `DATABASE_URL` aparecem)
3. Confirme `SPRING_PROFILES_ACTIVE=prod`
4. Health: abra `https://SEU-DOMINIO/actuator/health` — deve retornar `{"status":"UP"}`

---

## Manutenção

- **Backup:** Railway → PostgreSQL → Backups (ativar se disponível no plano)
- **Não apagar** o serviço PostgreSQL sem exportar dados antes
- Atualizações: `git push` → Railway redeploy automático
