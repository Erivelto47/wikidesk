# Wiki de Teste — WikiDesk

Esta é uma wiki pequena, mas propositalmente cheia de elementos variados,
para validar manualmente qualquer alteração visual ou funcional feita no
WikiDesk. Adicione esta pasta (`test-wiki/`) como uma fonte local pelo botão
**"+ Adicionar fonte"** e navegue por ela.

## Sumário

- [Começando](guias/comecando.md) — link relativo para outro arquivo.
- [Diagramas e callouts](guias/diagramas-e-callouts.md) — diagramas Mermaid (válidos e um quebrado de propósito) e callouts nas sintaxes GitHub e MkDocs.
- [Configuração avançada](guias/configuracao-avancada.md) — documento longo, para testar o sumário (TOC) com scroll-sync.
- [Referência de API](api/referencia.md#endpoint-de-autenticacao) — link relativo **com âncora** para uma seção específica.
- [Notas antigas](arquivados/2023/notas-antigas.md) — pasta aninhada em dois níveis.
- [Página que não existe](guias/pagina-fantasma.md) — link quebrado de propósito; clicar não deve travar o app nem abrir nada.
- [Só uma âncora nesta mesma página](#links-externos) — âncora pura (sem arquivo); hoje o app ainda não implementa scroll para âncoras internas, então é esperado que clicar não faça nada.

## Links externos

Alguns links para fora do app, devem abrir no navegador padrão:

- [Markdown Guide](https://www.markdownguide.org)
- [Documentação do Kotlin](https://kotlinlang.org/docs/home.html)
- [Repositório do JGit](https://github.com/eclipse-jgit/jgit)

## Imagens

Uma imagem válida (gerada localmente só para teste):

![Diagrama de arquitetura de exemplo](assets/diagrama.png)

E uma imagem propositalmente quebrada, para validar o estado de erro:

![Captura que não existe](assets/nao-existe.png)

## Tabela

| Recurso            | Suportado | Observação                              |
| ------------------- | :-------: | ---------------------------------------- |
| Tabelas GFM          |     ✅     | Sem suporte a alinhamento por coluna real |
| Imagens em bloco     |     ✅     | Só linha inteira sozinha (`![alt](src)`)  |
| Links internos       |     ✅     | Resolvidos como caminho relativo          |
| Âncoras internas     |     ❌     | Ainda não implementado                    |
| Checklists (`- [ ]`) |     ⚠️    | Renderiza como texto puro, sem checkbox   |

## Código

```bash
# Clonar esta wiki via app usando o botão "+ Adicionar fonte"
git clone https://example.com/nao-e-um-repo-real.git
```

```kotlin
fun ola(nome: String): String {
    return "Olá, $nome!"
}
```

## Lista com sub-itens (achatada)

1. Primeiro passo
2. Segundo passo
   - Detalhe A
   - Detalhe B
3. Terceiro passo

## Checklist (teste de compatibilidade)

- [ ] Tarefa ainda não feita
- [x] Tarefa concluída

## HTML embutido

Documentação real mistura HTML no Markdown — o WikiDesk converte o que tem
equivalente (links, ênfase, imagens, listas) e descarta o resto das tags,
mantendo o texto legível:

<div class="text-center">
  <a href="guias/comecando.md" class="btn btn-primary" role="button">Getting Started</a>
</div>

<div class="row"><div class="card">
<h3 class="card-title">Um card Bootstrap</h3>
<p class="card-text">Com <strong>negrito</strong>, <em>itálico</em>, <code>código</code>
e uma quebra<br>de linha. A tag <code>&lt;div&gt;</code> em si é descartada.</p>
</div></div>

Dentro de código a marcação é preservada: `<br>` e o bloco abaixo ficam intactos.

```html
<div class="card">isto é conteúdo, não marcação</div>
```

## Citação

> Esta é uma citação de exemplo, só para checar o estilo do bloco de quote —
> inclusive quebrando em mais de uma linha.

---

Fim da página inicial. Use a barra lateral para navegar pelas outras seções.
