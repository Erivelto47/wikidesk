# Configuração Avançada

Este documento é propositalmente longo, com várias seções `##`, para
validar o comportamento do sumário (TOC) lateral: destaque da seção ativa
conforme a rolagem, clique para pular direto a uma seção e o
comportamento do painel quando o texto é bem mais alto que a tela.

Volte para [Começando](comecando.md) ou para a [página inicial](../README.md)
quando terminar de rolar esta página.

## Visão geral

Esta seção introduz o assunto antes de entrar nos detalhes de cada
subsistema. O texto aqui é só preenchimento realista, para simular uma
página de documentação de verdade em vez de um "lorem ipsum" genérico.

## Variáveis de ambiente

| Variável              | Padrão   | Descrição                          |
| ----------------------- | -------- | ------------------------------------ |
| `DOCVIEWER_THEME`        | `system` | Tema inicial (`light`, `dark`, `system`) |
| `DOCVIEWER_DATA_DIR`     | (auto)   | Pasta usada para clones Git          |
| `DOCVIEWER_LOG_LEVEL`    | `info`   | Nível de log em desenvolvimento      |

## Autenticação em fontes Git

Ao adicionar uma fonte Git privada, dois caminhos de autenticação são
suportados: token de acesso pessoal via HTTPS, ou chave SSH já configurada
no sistema (`~/.ssh`). Nenhuma credencial é persistida em disco — elas
vivem apenas em memória durante a sessão do aplicativo.

## Atualização de fontes Git

O botão "↻" no cabeçalho de uma fonte Git executa um `git pull` no
repositório clonado e reescaneia os documentos, sem precisar reabrir o
aplicativo.

## Múltiplas fontes (multi-workspace)

Cada fonte aberta vira um bloco independente na sidebar, com seu próprio
cabeçalho, badge de tipo (`LOCAL` ou `GIT`) e árvore de arquivos. Fontes
podem ser recolhidas individualmente para reduzir a poluição visual quando
várias estão abertas ao mesmo tempo.

## Atalhos de teclado

- `⌘K` / `Ctrl+K` — abrir busca global
- `Esc` — fechar busca ou modal ativo
- `⌘B` / `Ctrl+B` — alternar sidebar

## Temas

O tema claro e escuro usam a mesma estrutura de cores (`AppColors`), com
duas camadas de borda: uma "suave" para divisórias estruturais entre
painéis, e uma mais definida para divisórias de conteúdo.

## Blocos de código

```kotlin
data class ConfiguracaoAvancada(
    val tema: String = "system",
    val logLevel: String = "info"
)
```

```bash
export DOCVIEWER_THEME=dark
export DOCVIEWER_LOG_LEVEL=debug
```

## Renderização de tabelas

Tabelas seguem a sintaxe GFM (`| coluna | coluna |` com linha separadora
`---|---`), sem suporte real a alinhamento por coluna — a linha
separadora aceita `:---`, `:-:` e `---:`, mas o alinhamento em si é
ignorado no momento.

## Renderização de imagens

Imagens só são reconhecidas quando ocupam uma linha inteira sozinhas,
no formato `![alt](caminho)`. Imagens dentro de um parágrafo de texto não
são destacadas como bloco de imagem.

## Busca global

A busca (`⌘K`) considera título, id do documento e o conteúdo textual de
cada bloco (parágrafos, listas, citações, código, tabelas e o texto
alternativo de imagens).

## Histórico de navegação

Voltar/avançar (`‹` `›`) na barra superior navegam pela pilha de
documentos abertos na sessão atual, de forma independente do sumário de
cada documento.

## Empacotamento

A distribuição nativa é gerada via `./gradlew packageDistributionForCurrentOS`,
produzindo um `.dmg`/`.pkg` no macOS ou um `.deb` no Linux, usando o
plugin de aplicação do Compose Multiplatform Desktop.

## Considerações finais

Se você rolou até aqui, o sumário lateral deveria ter passado por todas as
seções acima, destacando cada uma conforme ficava visível. Esse é o
comportamento esperado do scroll-sync.
