package wikidesk.mermaid

/** Carrega o mermaid.js (bundled localmente) uma única vez, em memória. */
private val mermaidJsSource: String by lazy {
    val resource = object {}.javaClass.getResourceAsStream("/mermaid/mermaid.min.js")
        ?: error("Recurso mermaid.min.js não encontrado — verifique src/main/resources/mermaid/")
    resource.bufferedReader(Charsets.UTF_8).use { it.readText() }
        // Proteção defensiva: se o bundle minificado contiver a sequência literal
        // "</script" dentro de alguma string, ela fecharia a tag <script> mais cedo
        // ao ser embutida inline no HTML. Barra escapada não muda o significado em JS.
        .replace("</script", "<\\/script")
}

/**
 * Monta a página HTML autocontida (mermaid.js embutido inline — a lib de
 * WebView usada ainda não suporta bem carregar recursos externos junto de
 * HTML fornecido como string no desktop) usada para renderizar diagramas
 * Mermaid dentro do WebView embutido (ver [MermaidRuntime]).
 *
 * Protocolo Kotlin <-> JS (via evaluateJavaScript, com polling):
 * - `startRender(source)`: inicia a renderização assíncrona; o resultado fica
 *   disponível em `window.__mermaidResult` (JSON) quando pronto.
 * - `getResult()`: devolve o JSON do último resultado (ou "" se ainda não pronto).
 * - `setZoom(fator)`: aplica um `transform: scale()` ao diagrama renderizado.
 * - `getSvgSource()`: devolve o SVG renderizado (outerHTML) para exportação.
 * - `exportPng(escala)`: inicia a conversão para PNG (assíncrona); resultado em
 *   `window.__pngResult` (data URL base64, ou "" em caso de falha).
 * - `getPngResult()`: devolve o data URL do PNG (ou null se ainda não pronto).
 */
fun buildMermaidHarnessHtml(): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <style>
          html, body {
            margin: 0;
            padding: 0;
            background: transparent;
            overflow: auto;
            height: 100%;
            width: 100%;
          }
          body {
            /* Centraliza o diagrama tanto na largura quanto na altura do
               bloco renderizado. `min-height: 100%` (em vez de `height`)
               deixa o corpo crescer normalmente quando o diagrama é maior
               que a área visível, para que a rolagem/zoom continuem
               funcionando em vez de cortar o conteúdo. */
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100%;
          }
          #stage {
            display: inline-block;
            /* Centro, não canto superior esquerdo: assim o zoom cresce/encolhe
               a partir do meio do diagrama, mantendo-o centralizado mesmo
               depois de aplicar `setZoom`. */
            transform-origin: center center;
          }
        </style>
        </head>
        <body>
        <div id="stage"></div>
        <script>
        // Captura QUALQUER erro de script da página (inclusive falha de
        // parse/execução do mermaid.min.js embutido logo abaixo) para que o
        // lado Kotlin receba uma mensagem de erro real em vez de um timeout.
        window.__pageLoadError = null;
        window.addEventListener('error', function (ev) {
          if (!window.__pageLoadError) {
            window.__pageLoadError = (ev && ev.message) ? ev.message : 'Erro desconhecido ao carregar a página.';
          }
        });
        </script>
        <script>$mermaidJsSource</script>
        <script>
        $HARNESS_JS
        </script>
        </body>
        </html>
    """.trimIndent()
}

private const val HARNESS_JS = """
// IMPORTANTE: este bloco não pode referenciar `mermaid` no nível superior —
// se o mermaid.min.js tiver falhado ao carregar (ex.: exigir um Chromium
// mais novo que o embutido), uma referência aqui derrubaria este script
// inteiro e as funções do protocolo (startRender/getResult/...) nunca
// existiriam, fazendo o lado Kotlin ver só um timeout sem explicação.
// Toda referência ao mermaid fica DENTRO de startRender, protegida.

window.__zoom = 1;
window.__mermaidResult = null;
window.__pngResult = null;
window.__mermaidInitialized = false;

function applyZoom() {
  var stage = document.getElementById('stage');
  stage.style.transform = 'scale(' + window.__zoom + ')';
}

window.setZoom = function(z) {
  window.__zoom = z;
  applyZoom();
};

window.startRender = async function(source) {
  window.__mermaidResult = null;
  window.__pngResult = null;
  var stage = document.getElementById('stage');
  stage.innerHTML = '';

  if (typeof window.mermaid === 'undefined') {
    var detail = window.__pageLoadError ? (' Detalhe: ' + window.__pageLoadError) : '';
    window.__mermaidResult = JSON.stringify({
      success: false,
      error: 'A biblioteca Mermaid não carregou no navegador embutido.' + detail,
      line: null
    });
    return;
  }

  try {
    if (!window.__mermaidInitialized) {
      mermaid.initialize({ startOnLoad: false, securityLevel: 'loose' });
      window.__mermaidInitialized = true;
    }
    var id = 'mmd-' + Date.now();
    var result = await mermaid.render(id, source);
    stage.innerHTML = result.svg;
    var svgEl = stage.querySelector('svg');
    var width = 0;
    var height = 0;
    if (svgEl) {
      if (svgEl.viewBox && svgEl.viewBox.baseVal && svgEl.viewBox.baseVal.width) {
        width = svgEl.viewBox.baseVal.width;
        height = svgEl.viewBox.baseVal.height;
      } else {
        width = svgEl.clientWidth;
        height = svgEl.clientHeight;
      }
    }
    window.__mermaidResult = JSON.stringify({
      success: true,
      svg: result.svg,
      width: width,
      height: height
    });
  } catch (err) {
    var line = null;
    try {
      if (err && err.hash && typeof err.hash.line === 'number') {
        line = err.hash.line + 1;
      } else if (err && err.message) {
        var m = /line\s+(\d+)/i.exec(err.message);
        if (m) line = parseInt(m[1], 10);
      }
    } catch (e2) {
      line = null;
    }
    var message = (err && err.message) ? err.message : String(err);
    window.__mermaidResult = JSON.stringify({
      success: false,
      error: message,
      line: line
    });
  }
  applyZoom();
};

window.getResult = function() {
  return window.__mermaidResult || '';
};

window.getSvgSource = function() {
  var svgEl = document.querySelector('#stage svg');
  return svgEl ? svgEl.outerHTML : '';
};

window.exportPng = function(scaleFactor) {
  window.__pngResult = null;
  var svgEl = document.querySelector('#stage svg');
  if (!svgEl) {
    window.__pngResult = '';
    return;
  }
  var svgText = svgEl.outerHTML;
  var svgBlob = new Blob([svgText], { type: 'image/svg+xml;charset=utf-8' });
  var url = URL.createObjectURL(svgBlob);
  var img = new Image();
  img.onload = function() {
    try {
      var scale = scaleFactor || 2;
      var canvas = document.createElement('canvas');
      canvas.width = Math.max(1, img.width * scale);
      canvas.height = Math.max(1, img.height * scale);
      var ctx = canvas.getContext('2d');
      ctx.scale(scale, scale);
      ctx.drawImage(img, 0, 0);
      window.__pngResult = canvas.toDataURL('image/png');
    } catch (e) {
      window.__pngResult = '';
    }
    URL.revokeObjectURL(url);
  };
  img.onerror = function() {
    window.__pngResult = '';
    URL.revokeObjectURL(url);
  };
  img.src = url;
};

window.getPngResult = function() {
  return window.__pngResult === null ? null : (window.__pngResult || '');
};
"""
