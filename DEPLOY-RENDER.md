# Deploy no Render (PostgreSQL)

## O que o `render.yaml` faz

- Cria o **Web Service** (Docker) e o **PostgreSQL**
- Liga o banco ao app (`PGHOST`, `PGPORT`, `DATABASE_URL`, etc.)
- Perfil `prod` — agendamentos **nao somem** ao reiniciar

## Passo a passo

1. Push do codigo no GitHub (`clinica-agenda`)
2. [dashboard.render.com](https://dashboard.render.com) → **New** → **Blueprint**
3. Conecte o repositorio `Widineii/clinica-agenda`
4. Antes de aplicar, em **Environment** do servico web, defina:
   - `ADMIN_PASSWORD` = senha forte do admin (obrigatorio)
5. **Apply** e aguarde deploy **Live**
6. Teste: `https://SEU-SERVICO.onrender.com/actuator/health` → `UP`
7. Crie agendamento → **Manual Deploy** ou reinicie → agendamento continua

## Acesso inicial

- Admin: `ADMIN_LOGIN` (padrao `admin`) + `ADMIN_PASSWORD` que voce definiu
- Profissionais seed: senha inicial `297b` — pedir para trocar em **Trocar senha**

## Se o deploy falhar

- Logs do Web Service
- Confirme `ADMIN_PASSWORD` preenchida
- Confirme variaveis `PGHOST` / `DATABASE_URL` no servico (vem do banco linkado)
- PostgreSQL e Web Service no **mesmo Blueprint** (mesmo `render.yaml`)
