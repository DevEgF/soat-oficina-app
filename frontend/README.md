# Oficina frontend

## Contexto atual

O frontend React continua disponível para execução e build local; não está publicado pelo chart OCI. A API e o serviço de autenticação estão acessíveis por HTTPS nos hosts hml/prod registrados no README principal.

O [README principal](../README.md) reúne RFCs, justificativa AWS → Oracle, escolha PostgreSQL/Neon, arquitetura, dashboards, alertas e situação do vídeo.

Run `npm ci`, then `npm run dev`. The staff API uses Vite's `/api` proxy to Spring at `http://localhost:8080` by default.

Customer authentication is served by the separate auth service: Lambda/API Gateway in the preserved AWS deployment, or the HTTP adapter behind Traefik in OCI. Spring does not expose `/auth/token`. Copy `.env.example` to `.env.local` and set `VITE_CUSTOMER_API_BASE_URL` to the gateway origin serving both `/auth/token` and `/api/customer/**`. The gateway must allow the frontend origin in CORS. The auth repository now provides an HTTP adapter under deploy/oci; local unit tests can still mock that boundary. For browser access across origins, validate the allowed frontend origin and CORS before using a live endpoint. Do not deploy AWS just to run these tests.

`VITE_API_BASE_URL` optionally changes the staff API origin and is also the customer fallback when `VITE_CUSTOMER_API_BASE_URL` is unset. Leave both unset when hosting the frontend behind the same API origin. These are public build-time URLs, never credentials.

Customer tokens stay in memory and expire after the authentication response's `expiresIn`. Customer errors do not clear staff login or redirect to the staff login page.

Validation: `npm run lint`, `npm test`, `npm run build`.
