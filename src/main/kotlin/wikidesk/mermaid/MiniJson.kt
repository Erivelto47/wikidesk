package wikidesk.mermaid

/**
 * Parser JSON mínimo, escrito à mão para não introduzir uma dependência (ex.:
 * kotlinx.serialization) só para decodificar um payload simples e totalmente
 * controlado por nós (o harness Mermaid só devolve um objeto plano de
 * string/número/booleano/null — sem arrays, que não são necessários aqui).
 */
sealed class JsonValue {
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data object Null : JsonValue()
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()
}

object MiniJson {

    /** Faz o parse de um objeto JSON plano; retorna mapa vazio se [text] estiver em branco ou for inválido. */
    fun parseObject(text: String): Map<String, JsonValue> {
        if (text.isBlank()) return emptyMap()
        return runCatching {
            val parser = Parser(text)
            (parser.parseValue() as? JsonValue.Obj)?.fields ?: emptyMap()
        }.getOrElse { emptyMap() }
    }

    private class Parser(private val text: String) {
        var pos = 0

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            return when (val c = text.getOrNull(pos)) {
                '{' -> parseObject()
                '"' -> JsonValue.Str(parseString())
                't', 'f' -> parseBoolean()
                'n' -> { pos += 4; JsonValue.Null }
                else -> if (c != null && (c.isDigit() || c == '-')) parseNumber() else JsonValue.Null
            }
        }

        private fun parseObject(): JsonValue.Obj {
            val fields = mutableMapOf<String, JsonValue>()
            pos++ // consome '{'
            skipWhitespace()
            if (text.getOrNull(pos) == '}') {
                pos++
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                if (text.getOrNull(pos) == ':') pos++
                val value = parseValue()
                fields[key] = value
                skipWhitespace()
                when (text.getOrNull(pos)) {
                    ',' -> { pos++; continue }
                    '}' -> { pos++; break }
                    else -> break
                }
            }
            return JsonValue.Obj(fields)
        }

        private fun parseString(): String {
            if (text.getOrNull(pos) != '"') return ""
            pos++ // consome a aspa de abertura
            val sb = StringBuilder()
            while (pos < text.length && text[pos] != '"') {
                val c = text[pos]
                if (c == '\\' && pos + 1 < text.length) {
                    pos++
                    when (val esc = text[pos]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'u' -> {
                            val hex = text.substring(pos + 1, (pos + 5).coerceAtMost(text.length))
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> sb.append(esc)
                    }
                    pos++
                } else {
                    sb.append(c)
                    pos++
                }
            }
            pos++ // consome a aspa de fechamento
            return sb.toString()
        }

        private fun parseBoolean(): JsonValue.Bool {
            return if (text.startsWith("true", pos)) {
                pos += 4
                JsonValue.Bool(true)
            } else {
                pos += 5
                JsonValue.Bool(false)
            }
        }

        private fun parseNumber(): JsonValue.Num {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] in "-+.eE")) pos++
            return JsonValue.Num(text.substring(start, pos).toDoubleOrNull() ?: 0.0)
        }
    }
}

fun Map<String, JsonValue>.stringOrNull(key: String): String? = (this[key] as? JsonValue.Str)?.value
fun Map<String, JsonValue>.doubleOrNull(key: String): Double? = (this[key] as? JsonValue.Num)?.value
fun Map<String, JsonValue>.intOrNull(key: String): Int? = (this[key] as? JsonValue.Num)?.value?.toInt()
fun Map<String, JsonValue>.boolOrNull(key: String): Boolean? = (this[key] as? JsonValue.Bool)?.value
