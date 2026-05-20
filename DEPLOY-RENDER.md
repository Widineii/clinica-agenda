# Deploy no Render (producao — agendamentos salvos)

O cliente usa **Render**, nao Railway. O arquivo `render.yaml` na raiz do repo cria tudo automaticamente.

## O que o Blueprint faz

| Recurso | Funcao |
|---------|--------|
| **Web Service** (Docker) | App Agenda Affetto |
| **PostgreSQL** | Banco persistente — agendamentos **nao somem** ao reiniciar |
| Variaveis ligadas | `DATABASE_URL`, `PGHOST`, `PGPORT`, etc. |
| Perfil `prod` | Sem dados demo; admin via `ADMIN_PASSWORD` |

---

## Passo a passo (primeira vez)

### 1. Codigo no GitHub

Ja esta em: https://github.com/Widineii/clinica-agenda

### 2. Criar no Render

1. Acesse [dashboard.render.com](https://dashboard.render.com)
2. **New** → **Blueprint**
3. Conecte o repositorio **Widineii/clinica-agenda** (branch `main`)
4. O Render le o `render.yaml` e mostra:
   - Web Service `clinica-agenda`
   - Banco `clinica-agenda-db`
5. **Antes de Apply**, clique no Web Service e preencha:
   - **`ADMIN_PASSWORD`** = senha forte do administrador (obrigatorio)
6. Clique **Apply** e aguarde os dois ficarem **Live** (pode levar 5–10 min na primeira vez)

### 3. Dominio

No Web Service → **Settings** → copie a URL (ex.: `https://clinica-agenda.onrender.com`)

### 4. Teste antes de entregar ao cliente

- [ ] `https://SUA-URL.onrender.com/actuator/health` → `{"status":"UP"}`
- [ ] Login admin (`admin` + senha que voce definiu)
- [ ] Login Polyana (`polyana` / `297b`)
- [ ] Criar 1 agendamento
- [ ] **Manual Deploy** ou reiniciar o servico → agendamento **continua la**

---

## Acesso para o cliente

| Quem | Login | Senha inicial |
|------|-------|----------------|
| Admin | `admin` (ou `ADMIN_LOGIN`) | A que voce definiu em `ADMIN_PASSWORD` |
| Profissionais | `polyana`, `julia`, etc. | `297b` — pedir para **Trocar senha** no primeiro dia |

---

## Se ja existir um Web Service antigo no Render

Servico antigo **sem** PostgreSQL ligado → deploy falha (status 1).

**Opcao A (recomendada):** apagar o servico antigo e criar de novo pelo **Blueprint** (passos acima).

**Opcao B:** no servico existente:

1. **New** → **PostgreSQL** (mesmo projeto)
2. No **Web Service** → **Environment** → adicione referencias do banco:
   - `DATABASE_URL` ← connection string do Postgres
   - Ou `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`
3. Defina `SPRING_PROFILES_ACTIVE` = `prod`
4. Defina `ADMIN_PASSWORD`
5. **Manual Deploy**

---

## Se o deploy falhar

| Sintoma | Solucao |
|---------|---------|
| `PostgreSQL nao configurado` | Banco nao ligado ao app — use Blueprint ou referencias de env |
| `Connection refused localhost` | Mesmo problema — falta `DATABASE_URL` / `PGHOST` |
| Health check falha | Aguarde 5 min (primeiro build Docker e lento no plano free) |
| `ADMIN_PASSWORD` vazio | Preencha no Environment antes do deploy |

Veja **Logs** do Web Service (ultimas 50 linhas).

---

## Plano free (importante)

- Web Service free **dorme** apos ~15 min sem uso (primeiro acesso demora ~1 min)
- Postgres free **expira em 30 dias** — para cliente em producao longa, use plano pago do banco

---

## Atualizar o sistema depois

```bash
git push origin main
```

Render faz redeploy automatico (`autoDeploy: true` no `render.yaml`).
