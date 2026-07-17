package wikidesk.mermaid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiniJsonTest {

    @Test
    fun `parseObject decodifica sucesso de renderizacao com svg escapado`() {
        val json = """{"success":true,"svg":"<svg><text>a \"b\"</text></svg>","width":123.5,"height":64}"""
        val fields = MiniJson.parseObject(json)

        assertEquals(true, fields.boolOrNull("success"))
        assertEquals("<svg><text>a \"b\"</text></svg>", fields.stringOrNull("svg"))
        assertEquals(123.5, fields.doubleOrNull("width"))
        assertEquals(64, fields.intOrNull("height"))
    }

    @Test
    fun `parseObject decodifica falha com linha do erro`() {
        val json = """{"success":false,"error":"Parse error on line 4","line":4}"""
        val result = parseMermaidRenderResult(json)

        assertTrue(result is MermaidRenderResult.Failure)
        assertEquals(4, result.line)
        assertEquals("Parse error on line 4", result.message)
    }

    @Test
    fun `parseObject decodifica falha sem linha`() {
        val json = """{"success":false,"error":"algo deu errado","line":null}"""
        val result = parseMermaidRenderResult(json)

        assertTrue(result is MermaidRenderResult.Failure)
        assertNull(result.line)
    }

    @Test
    fun `parseObject retorna mapa vazio para texto em branco ou invalido`() {
        assertEquals(emptyMap(), MiniJson.parseObject(""))
        assertEquals(emptyMap(), MiniJson.parseObject("nao e json"))
    }

    @Test
    fun `unwrapJsEvalResult desfaz string JS empacotada com aspas e escapes`() {
        val wrapped = "\"{\\\"success\\\":true,\\\"svg\\\":\\\"<svg\\/>\\\",\\\"width\\\":10,\\\"height\\\":5}\""
        val unwrapped = unwrapJsEvalResult(wrapped)
        val fields = MiniJson.parseObject(unwrapped)

        assertEquals(true, fields.boolOrNull("success"))
        assertEquals(10.0, fields.doubleOrNull("width"))
    }

    @Test
    fun `encodeJsStringLiteral escapa aspas quebras de linha e barras invertidas`() {
        val encoded = encodeJsStringLiteral("linha1\nlinha2 \"citado\" \\barra")
        assertEquals("\"linha1\\nlinha2 \\\"citado\\\" \\\\barra\"", encoded)
    }
}
