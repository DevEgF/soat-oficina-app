# Jornada da ordem de serviço

```mermaid
sequenceDiagram
  participant Atendente
  participant Tecnico
  participant Admin
  participant Cliente
  participant Estoque as Almoxarife
  participant App as Aplicação
  Atendente->>App: Criar OS
  App-->>Atendente: RECEIVED + trackingCode
  Tecnico->>App: Iniciar diagnóstico
  App-->>Tecnico: IN_DIAGNOSIS
  Tecnico->>App: Submeter plano e reservar peças
  App-->>Admin: PENDING_INTERNAL_APPROVAL
  Admin->>App: Aprovar plano interno
  Atendente->>App: Enviar orçamento ao cliente
  App-->>Cliente: AWAITING_CUSTOMER_APPROVAL
  Cliente->>App: Bearer CUSTOMER + código + decisão
  alt APROVADO
    App-->>Cliente: IN_EXECUTION
    Estoque->>App: Confirmar saída das peças reservadas
    Tecnico->>App: Concluir serviços
    Atendente->>App: Registrar entrega
    App-->>Atendente: DELIVERED
  else RECUSADO
    App-->>Cliente: CANCELLED e liberação de reservas
  end
```

A aplicação mede criação, duração das etapas DIAGNOSIS/APPROVAL/EXECUTION e falhas inesperadas por operação. Status e operações usam listas fechadas; CPF, UUID, placa, código de OS e correlation ID não são dimensões de métricas. Rejeição interna também cancela a OS e libera as reservas aplicáveis.
