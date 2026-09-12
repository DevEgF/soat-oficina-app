ALTER TABLE clientes
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE clientes
  ADD CONSTRAINT ck_clientes_status CHECK (status IN ('ACTIVE', 'BLOCKED'));

CREATE INDEX idx_clientes_documento_status ON clientes (documento, status);
