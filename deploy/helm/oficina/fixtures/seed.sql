-- Optional synthetic acceptance fixtures. Preserve existing valid records.
BEGIN;
INSERT INTO clientes(id, documento, nome, status) VALUES
('00000000-0000-4000-8000-000000000001', '52998224725', 'DEMO ACCEPTANCE ACTIVE', 'ACTIVE'),
('00000000-0000-4000-8000-000000000002', '11144477735', 'DEMO ACCEPTANCE BLOCKED', 'BLOCKED'),
('00000000-0000-4000-8000-000000000003', '12345678909', 'DEMO ACCEPTANCE OTHER', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;
INSERT INTO veiculos(id, cliente_id, placa, marca, modelo, ano) VALUES
('00000000-0000-4000-8000-000000000101', '00000000-0000-4000-8000-000000000001', 'DEM1001', 'DEMO', 'Synthetic', 2026)
ON CONFLICT (id) DO NOTHING;
INSERT INTO servicos_catalogo(id, nome, descricao, preco_centavos, tempo_estimado_minutos) VALUES
('00000000-0000-4000-8000-000000000201', 'DEMO ACCEPTANCE SERVICE', 'Synthetic fixture', 1000, 30)
ON CONFLICT (id) DO NOTHING;
INSERT INTO ordens_servico(id, codigo_acompanhamento, cliente_id, veiculo_id, status,
  valor_servicos_centavos, valor_total_centavos, diagnosticado_em, orcamento_enviado_em)
SELECT code, code, '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000101',
  'PENDING_APPROVAL', 1000, 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES ('00000000-0000-4000-8000-000000001001'), ('00000000-0000-4000-8000-000000001002'),
             ('00000000-0000-4000-8000-000000001003')) AS fixture(code)
ON CONFLICT (id) DO NOTHING;
-- Repair only the invalid status written by the previous synthetic fixture.
-- Re-running the seed must never reset an approval/rejection already performed.
UPDATE ordens_servico SET status = 'PENDING_APPROVAL'
WHERE status = 'AWAITING_CUSTOMER_APPROVAL'
  AND cliente_id = '00000000-0000-4000-8000-000000000001'
  AND veiculo_id = '00000000-0000-4000-8000-000000000101'
  AND id IN ('00000000-0000-4000-8000-000000001001',
             '00000000-0000-4000-8000-000000001002',
             '00000000-0000-4000-8000-000000001003');
INSERT INTO ordem_servico_linhas_servico(id, ordem_servico_id, servico_catalogo_id, quantidade, preco_unitario_centavos)
SELECT line, code, '00000000-0000-4000-8000-000000000201', 1, 1000
FROM (VALUES ('00000000-0000-4000-8000-000000002001', '00000000-0000-4000-8000-000000001001'),
             ('00000000-0000-4000-8000-000000002002', '00000000-0000-4000-8000-000000001002'),
             ('00000000-0000-4000-8000-000000002003', '00000000-0000-4000-8000-000000001003')) AS fixture(line, code)
ON CONFLICT (id) DO NOTHING;
COMMIT;
