package wikidesk.persistence.repository

import java.io.File

/**
 * Normaliza um caminho de pasta para fins de deduplicação de wikis: resolve
 * links simbólicos e segmentos relativos (`.`/`..`) quando o sistema de
 * arquivos permite (`File.canonicalFile`), com fallback para o caminho
 * absoluto simples se a canonicalização falhar (ex.: pasta temporariamente
 * inacessível, permissão negada, link quebrado) — nesse caso é preferível
 * uma comparação um pouco menos precisa a impedir o usuário de registrar a
 * wiki.
 *
 * `File.canonicalPath` já trata as particularidades de cada SO (case-
 * insensitividade do NTFS/APFS por padrão, separador de caminho, etc.), então
 * o mesmo código funciona em macOS, Linux e Windows sem branch por SO.
 */
fun normalizeWikiPath(path: String): String =
    runCatching { File(path).canonicalFile.path }.getOrElse { File(path).absoluteFile.path }
