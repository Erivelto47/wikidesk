# Começando

Guia rápido de exemplo. Volte para a [página inicial](../README.md) a
qualquer momento — este é um link relativo "subindo" um nível de pasta.

## Instalação

Pré-requisitos antes de começar:

- JDK 17 ou superior instalado
- Acesso de leitura à pasta de documentação
- Uma xícara de café (opcional, mas recomendado)

```bash
./gradlew run
```

## Estrutura de pastas

A wiki de teste está organizada assim:

```text
test-wiki/
├── README.md
├── guias/
│   ├── comecando.md
│   └── configuracao-avancada.md
├── api/
│   └── referencia.md
├── arquivados/
│   └── 2023/
│       └── notas-antigas.md
└── assets/
    └── diagrama.png
```

## Exemplo de configuração

```json
{
  "tema": "escuro",
  "sidebarLargura": 272,
  "fontesAtivas": ["local-docs", "wiki-teste"]
}
```

## Próximos passos

Veja também a [referência de API](../api/referencia.md) e a
[configuração avançada](configuracao-avancada.md), que é um documento
propositalmente longo para testar o sumário lateral.
