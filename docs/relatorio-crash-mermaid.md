# Relatório — Crash silencioso ao abrir blocos Mermaid

**Projeto:** WikiDesk
**Data:** 16/07/2026
**Sintoma relatado:** "ao abrir um código mermaid, meu app fecha sem log de erro"

---

## Resumo executivo

O app fechava sozinho, sem exceção Kotlin visível, sempre que um documento continha um bloco ` ```mermaid `. A causa não era um bug de lógica no código Kotlin, e sim uma combinação de dois problemas de configuração/infraestrutura nativa ligados ao WebView embutido (KCEF/Chromium) usado para renderizar os diagramas:

1. Os flags de JVM exigidos pelo KCEF não estavam sendo aplicados de fato à execução real do app.
2. O bundle nativo do Chromium baixado automaticamente pelo KCEF tem uma estrutura de pastas diferente da que a biblioteca espera, causando um crash nativo (`SIGSEGV`) — que mata o processo Java inteiro sem gerar stacktrace Kotlin, exatamente o comportamento relatado ("fecha sem log").

Os dois problemas foram corrigidos e o cenário foi **reproduzido e verificado com sucesso na própria máquina**, incluindo uma execução real do app (`./gradlew run`) em que o motor de diagramas inicializou sem crashar.

---

## Linha do tempo da investigação

| Passo | O que foi feito | Resultado |
|---|---|---|
| 1 | Hipótese inicial: flags `--add-opens` do KCEF não chegavam a ser aplicados na task `run` real | Corrigido, mas não resolveu sozinho |
| 2 | Reprodução real via `./gradlew run` (a pedido do usuário) | Gerou o primeiro log de crash nativo com stacktrace de JVM |
| 3 | Leitura do `hs_err_pid*.log` e do log de inicialização do JCEF | Identificado `SIGSEGV` dentro de `FindClass` do `libjcef.dylib`, logo após uma falha de `dlopen` |
| 4 | Inspeção do bundle baixado em disco | Confirmado: o framework do Chromium estava em um caminho diferente do esperado |
| 5 | Pesquisa no código-fonte do JCEF/JetBrains e de outro app Kotlin real que já teve o mesmo problema | Confirmado que é uma incompatibilidade conhecida entre o "layout achatado" clássico e o novo layout `cef_server` da JetBrains |
| 6 | Implementado reparo automático (symlinks) + testado downgrade de versão da lib | Downgrade resolveu o crash mas trocou o problema por outro (ver abaixo) |
| 7 | Voltado à versão mais nova da lib, mantendo o reparo automático | **Sucesso**: app inicializa o motor de diagramas sem crashar |

---

## Causa 1 — Flags de JVM não aplicados de verdade

O KCEF (Chromium embutido) exige flags especiais do Java 16+ para conseguir acessar APIs internas do AWT/Swing:

```
--add-opens java.desktop/sun.awt=ALL-UNNAMED
--add-opens java.desktop/java.awt.peer=ALL-UNNAMED
--add-opens java.desktop/sun.lwawt=ALL-UNNAMED          (macOS)
--add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED   (macOS)
```

**O que estava errado:** esses flags estavam configurados via

```kotlin
afterEvaluate {
    tasks.withType<JavaExec> { jvmArgs(...) }
}
```

Isso funciona para uma task Gradle genérica do tipo `JavaExec`, mas a task `run` criada pelo **plugin do Compose Desktop** não é garantidamente desse tipo — e, mais importante, esse bloco **não afeta em nada os pacotes finais** (`.dmg`, `.deb`, `.pkg`) gerados por `jpackage`. Ou seja, mesmo compilando sem erro, os flags podiam nunca chegar a ser realmente passados para a JVM em tempo de execução.

**Correção aplicada:** mover os `jvmArgs(...)` para dentro do bloco `compose.desktop.application { }`, que é a própria DSL do plugin — garantida de ser aplicada tanto ao `./gradlew run` quanto às distribuições empacotadas.

Arquivo alterado: `build.gradle.kts`

---

## Causa 2 (a causa raiz real) — Layout do bundle nativo do Chromium incompatível

Mesmo depois da correção acima, o crash **persistiu de forma idêntica** ao rodar `./gradlew run` de verdade. O log revelou o problema de fato:

```
cef_load_library: dlopen .../kcef-bundle/Frameworks/Chromium Embedded Framework.framework/Chromium Embedded Framework:
tried: ... (no such file) ...

# A fatal error has been detected by the Java Runtime Environment:
# SIGSEGV (0xb) at pc=0x0000000000000000
# Problematic frame:
# C  [libjcef.dylib+0x30578]  (anonymous namespace)::FindClass(JNIEnv_*, char const*)+0x2f8
```

### O que estava acontecendo

O **KCEF** (biblioteca que embute o Chromium de verdade no app, usada só para renderizar Mermaid) baixa automaticamente, na primeira execução, um pacote nativo do navegador. Só que:

- O binário que ele **baixa** vem no formato mais novo da JetBrains, chamado **`cef_server`** — uma arquitetura "fora de processo", onde o Chromium roda dentro de:
  ```
  Frameworks/cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework
  ```
- Mas o carregador nativo (`libjcef.dylib`) embutido na versão da biblioteca Kotlin monta o caminho esperando o **layout clássico "achatado"**:
  ```
  Frameworks/Chromium Embedded Framework.framework
  ```

Esse descompasso faz o `dlopen` (a chamada do sistema operacional que carrega uma biblioteca nativa) falhar silenciosamente — o código não trata essa falha como erro fatal e segue em frente tentando inicializar um Chromium "morto", o que resulta no `SIGSEGV`. Como isso acontece **fora da JVM, em código nativo (C++)**, não existe stacktrace Kotlin: o processo simplesmente morre. É exatamente o "fecha sem log" relatado.

Confirmei essa mesma limitação pesquisando o código-fonte oficial do JCEF/JetBrains e encontrando um app Kotlin real ([open-ani/animeko](https://github.com/open-ani/animeko)) que precisou implementar o mesmo tipo de correção manual para contornar esse problema.

### Correção aplicada

Adicionei uma rotina de auto-reparo que roda automaticamente antes de qualquer tentativa de inicializar o Chromium:

- Se a pasta do bundle existe mas está **incompleta** (download anterior interrompido/corrompido) → apaga tudo, forçando um novo download limpo.
- Se a pasta existe e tem o layout **aninhado** (`cef_server`) mas falta o layout **achatado** que o carregador nativo procura → cria **links simbólicos** apontando do caminho esperado para o real:
  ```
  Frameworks/Chromium Embedded Framework.framework  →  Frameworks/cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework
  Frameworks/jcef Helper.app                        →  Frameworks/cef_server.app/Contents/Frameworks/jcef Helper.app
  ```

Arquivo alterado: `src/main/kotlin/wikidesk/mermaid/MermaidRuntime.kt` (função `repairOrCleanUpBundle`)

### Efeito colateral encontrado e revertido

Durante a investigação, cheguei a trocar a versão da biblioteca `compose-webview-multiplatform` de `2.0.1` para `1.9.40`, pensando que era um problema específico da versão mais nova. Isso **de fato eliminou o `SIGSEGV`**, mas revelou um segundo problema: a versão `1.9.40` tem classes Java (`org.cef.*`) antigas demais para o binário do Chromium que é baixado hoje em dia (a lib baixa sempre a versão "mais nova" disponível, independente da versão do artefato Maven usada). Isso gerava uma enxurrada de `NoSuchMethodError` e páginas que não carregavam (`ERR_ABORTED`).

**Decisão final:** manter a versão `2.0.1` (bindings Java mais alinhados com o binário atual) e resolver o problema real — o layout de pastas — com o reparo automático acima, em vez de tentar "acertar" uma versão antiga por tentativa e erro.

---

## Verificação — evidências de que funcionou

Rodei o app de verdade (`./gradlew run`) na máquina, com um gatilho temporário forçando a inicialização do motor de diagramas assim que o app abre (removido depois do teste), e comparei antes/depois:

**Antes da correção do layout** (repetido em 3 tentativas diferentes, sempre o mesmo resultado):
```
JCEF_I: CefApp: set state INITIALIZING
#
# A fatal error has been detected by the Java Runtime Environment:
# SIGSEGV (0xb) ...
> Task :run FAILED
```

**Depois da correção** (com os symlinks já criados):
```
JCEF_V: CefApp: native initialization is finished.
JCEF_I: CefApp: set state INITIALIZED
JCEF_I: version: JCEF Version = 147.0.10... | CEF Version = 147.0.10 | Chromium Version = 147.0.7727.118
```
Processo permaneceu de pé (verificado via `pgrep`), sem nenhum erro adicional.

Também confirmei diretamente no disco que os links simbólicos foram criados como esperado:
```
lrwxr-xr-x  Chromium Embedded Framework.framework -> .../cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework
lrwxr-xr-x  jcef Helper.app -> .../cef_server.app/Contents/Frameworks/jcef Helper.app
```

Além disso, `./gradlew build` (compilação + todos os testes automatizados do projeto) passou limpo depois de todas as alterações.

---

## Observação importante sobre a primeira execução após limpar o cache

Se a pasta `~/Library/Application Support/WikiDesk/kcef-bundle` for apagada (cache de instalação do Chromium), a **primeira** vez que um bloco Mermaid for aberto depois disso vai: baixar o bundle → tentar inicializar → ainda pode falhar uma única vez, porque o reparo só roda **antes** da tentativa de inicialização seguinte (o download e a primeira tentativa de uso acontecem dentro da mesma chamada nativa, sem chance de reparar no meio). Na prática isso significa:

- 1ª tentativa após um download do zero: pode falhar.
- 2ª tentativa (reabrir o app ou reabrir o documento): o reparo já roda antes de inicializar, e funciona.

Isso **não é mais um problema no caso comum**, porque o bundle já foi baixado e reparado nesta máquina durante os testes — o próximo uso normal do app já deve funcionar de primeira.

---

## Arquivos alterados nesta correção

| Arquivo | Mudança |
|---|---|
| `build.gradle.kts` | `jvmArgs(...)` movido para dentro de `compose.desktop.application { }`; versão da lib mantida em `2.0.1` |
| `src/main/kotlin/wikidesk/mermaid/MermaidRuntime.kt` | Nova função `repairOrCleanUpBundle` (reparo via symlink / limpeza de instalação incompleta), chamada automaticamente em `ensureInitialized()` |
| `src/main/kotlin/Main.kt` | Mantido `addTempDirectoryRemovalHook()` + `MermaidRuntime.dispose()` no ciclo de vida da janela |

---

## Riscos residuais / pontos de atenção

- **Dependência de infraestrutura externa em evolução:** o KCEF baixa "a versão mais nova disponível" do Chromium a cada instalação nova, de uma fonte que está fora do nosso controle direto. Se um dia a JetBrains mudar o layout de pastas de novo, o reparo automático pode precisar de ajuste (a lógica está isolada em uma única função, fácil de revisar).
- **Symlinks no macOS:** em cenários com Gatekeeper/notarização mais restritiva (fora de desenvolvimento local), links simbólicos dentro de bundles `.app` podem se comportar diferente. Para uso local/dev, como neste projeto, não é um problema.
- **Renderização visual em si:** esta correção resolve a inicialização do motor (o app não fecha mais). A qualidade visual do diagrama renderizado, zoom e exportação SVG/PNG ainda dependem de você testar na interface, já que não tenho acesso a captura de tela nesta sessão — se algo parecer errado visualmente, me avise com detalhes.
- **Aviso do macOS sobre assinatura de código** (`Unable to derive validation category for current process`): apareceu nos logs, é comum em builds locais não assinados/notarizados e não impediu a inicialização — pode ser ignorado no ambiente de desenvolvimento.
