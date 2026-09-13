CREATE SCHEMA hml;
CREATE TABLE hml.ordens_servico (
  criado_em timestamp NOT NULL,
  diagnosticado_em timestamp,
  orcamento_enviado_em timestamp,
  aprovado_em timestamp,
  execucao_iniciada_em timestamp,
  finalizada_em timestamp,
  entregue_em timestamp,
  status varchar(40) NOT NULL
);

WITH clock AS (
  SELECT date_trunc('day', CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')
         + interval '15 hours' AS base
)
INSERT INTO hml.ordens_servico
  (criado_em, diagnosticado_em, orcamento_enviado_em, aprovado_em,
   execucao_iniciada_em, finalizada_em, entregue_em, status)
SELECT base, base + interval '1 minute', base + interval '2 minutes',
       base + interval '3 minutes', base + interval '4 minutes',
       base + interval '5 minutes', base + interval '6 minutes', 'DELIVERED'
FROM clock
UNION ALL
SELECT base, base - interval '1 minute', base + interval '1 minute', base,
       base + interval '1 minute', base, base - interval '1 minute', 'FINALIZED'
FROM clock
UNION ALL
SELECT base, NULL, NULL, NULL, NULL, NULL, NULL, 'RECEIVED'
FROM clock
UNION ALL
SELECT base - interval '8 days', base - interval '8 days' + interval '1 minute',
       base - interval '8 days' + interval '2 minutes',
       base - interval '8 days' + interval '3 minutes',
       base - interval '8 days' + interval '4 minutes',
       base - interval '8 days' + interval '5 minutes',
       base - interval '8 days' + interval '6 minutes', 'DELIVERED'
FROM clock;
