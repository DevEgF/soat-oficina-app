# RFC 0003 - Autenticação do cliente

Status: Accepted - AWS Lambda com entrada exclusivamente CPF.

`POST /auth/token` consulta um cliente ACTIVE no schema do ambiente e emite JWT de 900 segundos: `iss=oficina`, `aud=oficina-api`, `scope=CUSTOMER`, `env=hml|prod`, `sub=UUID`, `iat` e `exp`. HS256 usa SHA-256 dos bytes UTF-8 exatos do segredo compartilhado, garantindo compatibilidade entre Node e Spring.

O authorizer Lambda e o decoder Spring validam o token; a aplicação verifica a propriedade da OS pelo UUID. O CPF não segue na identidade do token nem nas chamadas de acompanhamento/decisão. CPF inexistente e cliente BLOCKED recebem o mesmo 401. O frontend mantém o Bearer em memória sem misturá-lo ao login staff.

CPF é identificador público, não segredo nem segundo fator. Esta autenticação atende à restrição acadêmica e não comprova posse de identidade para um produto público. Uma evolução real precisa de prova de posse e mecanismos de recuperação próprios. A API administrativa pode continuar cadastrando CNPJ; a autenticação do cliente aceita somente CPF.

Decisão anterior: validação customizada por JWKS no GCP. Status: Superseded. Staff continua com login Spring e escopos separados; as cinco senhas implantadas são externas e sem valores padrão.
