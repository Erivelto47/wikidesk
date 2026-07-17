# Referência de API (exemplo)

Documentação fictícia de uma API, só para ter conteúdo técnico realista
com tabelas e âncoras que podem ser referenciadas por outras páginas —
veja o link vindo da [página inicial](../README.md).

## Endpoint de autenticação

```
POST /auth/token
```

| Campo       | Tipo   | Obrigatório | Descrição                    |
| ----------- | ------ | :---------: | ------------------------------ |
| `usuario`   | string |      ✅      | Nome de usuário ou e-mail      |
| `senha`     | string |      ✅      | Senha em texto (via HTTPS)     |
| `client_id` | string |      ❌      | Identificador da aplicação     |

Exemplo de resposta:

```json
{
  "access_token": "exemplo-token",
  "expires_in": 3600
}
```

## Endpoint de documentos

```
GET /docs?fonte=wiki-teste
```

Retorna a lista de documentos indexados de uma fonte específica.

| Campo    | Tipo   | Descrição                  |
| -------- | ------ | ---------------------------- |
| `id`     | string | Id composto `fonte::caminho`  |
| `titulo` | string | Título extraído do H1         |
| `caminho`| string | Caminho relativo dentro da fonte |

## Endpoint de atualização (fontes Git)

```
POST /fontes/{id}/atualizar
```

Dispara um `git pull` na fonte informada. Equivalente a clicar no botão
"↻" no cabeçalho da fonte na sidebar.

> Este endpoint é apenas ilustrativo — a wiki de teste não tem um backend
> real, é só conteúdo para validar renderização.
