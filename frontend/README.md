# Oficina frontend

Run `npm ci`, then `npm run dev`. The staff API uses Vite's `/api` proxy to Spring at `http://localhost:8080` by default.

Customer authentication is served by the separate auth Lambda through API Gateway. Spring does not expose `/auth/token`. Copy `.env.example` to `.env.local` and set `VITE_CUSTOMER_API_BASE_URL` to the gateway origin serving both `/auth/token` and `/api/customer/**`. The gateway must allow the frontend origin in CORS. The auth repository currently has no local HTTP runner, so the complete customer browser flow requires the gateway after deployment; local unit tests mock that boundary. Do not deploy AWS just to run these tests.

`VITE_API_BASE_URL` optionally changes the staff API origin and is also the customer fallback when `VITE_CUSTOMER_API_BASE_URL` is unset. Leave both unset when hosting the frontend behind the same API origin. These are public build-time URLs, never credentials.

Customer tokens stay in memory and expire after the authentication response's `expiresIn`. Customer errors do not clear staff login or redirect to the staff login page.

Validation: `npm run lint`, `npm test`, `npm run build`.
