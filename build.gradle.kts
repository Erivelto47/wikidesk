import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
    id("app.cash.sqldelight") version "2.1.0"
}

group = "dev.erivelto.wikidesk"
version = "1.2.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    // Exigido pela compose-webview-multiplatform no alvo desktop (dependência
    // transitiva do KCEF/Java CEF Browser usado para embutir um Chromium real).
    maven("https://jogamp.org/deployment/maven")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation(compose.ui)

    // Clonar/atualizar repositórios Git como fonte de documentação (implementação
    // pura Java, sem depender do binário `git` do sistema — ver wikidesk.git.GitClient).
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.0.202606012155-r")
    // Transporte SSH (Apache MINA sshd), usado para URLs git@host:.../ssh://.
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:7.7.0.202606012155-r")

    // WebView embutido (Chromium real via KCEF) usado só para renderizar
    // diagramas Mermaid localmente/offline (mermaid.js é bundled em
    // src/main/resources/mermaid) — ver wikidesk.mermaid.*. É uma dependência
    // pesada (baixa um bundle nativo do Chromium na primeira execução), decisão
    // consciente discutida com o usuário: é a única forma de ter renderização,
    // zoom e exportação SVG/PNG reais, usando o próprio parser do Mermaid para
    // relatar erros com número de linha.
    // Versão mais recente disponível. O KCEF baixa em tempo de execução um
    // bundle nativo do Chromium separado deste artefato Maven (formato
    // "cef_server" da JetBrains) — em vez de tentar "acertar" a versão certa
    // por tentativa e erro (1.9.40 tem bindings Java antigos demais para o
    // binário nativo atual, causando NoSuchMethodError nos callbacks JNI),
    // ficamos na mais nova (bindings mais alinhados com o que é baixado hoje)
    // e corrigimos o único problema estrutural real do lado nosso — o layout
    // de pastas do bundle baixado — em `MermaidRuntime.repairOrCleanUpBundle`.
    implementation("io.github.kevinnzou:compose-webview-multiplatform:2.0.1")

    // Persistência local (ver wikidesk.persistence): SQLite via SQLDelight, sem
    // ORM baseado em reflexão — APIs tipadas geradas a partir de arquivos .sq.
    // O driver JVM (sqlite-driver) embute o xerial sqlite-jdbc, cujo binário
    // nativo é usado também para o índice de busca textual (FTS5, quando
    // suportado) e para operações de manutenção (VACUUM INTO, integrity_check)
    // via uma segunda conexão JDBC crua — ver wikidesk.persistence.database.
    implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
    implementation("app.cash.sqldelight:coroutines-extensions:2.1.0")
    // sqlite-driver traz o xerial sqlite-jdbc só como dependência de runtime
    // (não aparece no classpath de compilação dos módulos que o usam) — como
    // DatabaseFactory.kt e Fts5SearchIndex.kt referenciam tipos dele
    // diretamente (org.sqlite.SQLiteConfig, DriverManager/Connection), é
    // preciso declarar explicitamente aqui. Versão travada na mesma que o
    // sqlite-driver:2.1.0 já resolve transitivamente, para não haver risco de
    // duas versões do driver JDBC coexistindo no classpath.
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")

    // EXPERIMENTAL (branch experiment/native-file-chooser): seletor de
    // arquivo/pasta usando o diálogo nativo do SO (SystemFileChooser) em vez
    // do JFileChooser puro do Swing — ver wikidesk.platform.DesktopFileChooser.
    // Motivo: JFileChooser sob o look-and-feel GTK do Linux tem um bug antigo
    // e documentado (JDK-5073778) onde selecionar uma pasta duplica o último
    // segmento do caminho (ex.: "solid/solid"). SystemFileChooser chama a API
    // nativa de verdade (NSOpenPanel no macOS, diálogo comum do Win32 no
    // Windows, GTK 3 real no Linux via binding nativo) em vez do Swing
    // repintando um tema — evita essa classe de bug por completo. Cai de
    // volta pro JFileChooser sozinho se o GTK 3 não estiver presente.
    implementation("com.formdev:flatlaf:3.7.1")

    testImplementation(kotlin("test"))
}

sqldelight {
    databases {
        create("WikiDeskDatabase") {
            packageName.set("wikidesk.persistence.database")
            // O dialeto padrão do plugin (sqlite-3-18) não entende
            // `INSERT ... ON CONFLICT DO UPDATE` (upsert), usado em
            // Wiki.sq/WikiGitState.sq/AppSettings.sq/Document.sq —
            // esse dialeto só descreve o que o COMPILADOR do SQLDelight
            // aceita ao gerar código; o SQLite real embutido no driver
            // (xerial, via sqlite-driver) já suporta upsert há muito tempo,
            // então isto não muda nada em runtime.
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.1.0")
        }
    }
}

kotlin {
    // Mantido em 21 para alinhar o bytecode compilado ao runtime empacotado
    // pelo jpackage/GitHub Actions. Usar um toolchain mais novo aqui gera
    // classes que o runtime nativo não consegue carregar ao abrir o app.
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        // Flags exigidas pelo KCEF (Chromium embutido via compose-webview-multiplatform,
        // usado só para renderizar diagramas Mermaid) em JDK 16+ — sem elas, o
        // CEF trava/derruba a JVM inteira ao tentar acessar APIs internas do
        // AWT/Swing (crash nativo, sem stacktrace Kotlin: é exatamente o "app
        // fecha sem log" ao abrir um documento com bloco Mermaid).
        //
        // Importante: isto precisa estar aqui dentro de `application { }` (a
        // própria DSL do plugin Compose Desktop), e não em
        // `afterEvaluate { tasks.withType<JavaExec> { ... } }` — a task `run`
        // deste plugin não é garantidamente uma JavaExec, e distribuições
        // empacotadas (dmg/deb/pkg) só recebem os jvmArgs configurados aqui.
        // Ver https://github.com/KevinnZou/compose-webview-multiplatform/blob/main/README.desktop.md#flags
        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")

        if (System.getProperty("os.name").contains("Mac")) {
            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WikiDesk"
            packageVersion = project.version.toString()
            description = "Visualizador de documentação Markdown local"
            copyright = "© 2026 Erivelto Muller"

            // O runtime empacotado (jlink, via a task createRuntimeImage) só
            // inclui os módulos do JDK que o jdeps consegue detectar
            // automaticamente a partir dos jars — e ele não detecta o uso de
            // java.sql.DriverManager feito diretamente em
            // wikidesk.persistence.database.DatabaseFactory/Fts5SearchIndex
            // (fora do SQLDelight, para PRAGMA/FTS5/manutenção). Sem isto, o
            // app empacotado (.dmg/.msi/.deb) crasha ao abrir com
            // `NoClassDefFoundError: java/sql/DriverManager` — só aparece no
            // build empacotado, nunca em `./gradlew run` (que usa o JDK
            // completo do sistema, não a imagem reduzida). java.logging é
            // usado por PersistenceLog (java.util.logging.Logger).
            modules("java.sql", "java.logging", "java.naming")

            macOS {
                bundleID = "dev.erivelto.wikidesk"
                iconFile.set(project.file("src/main/resources/icons/wikidesk-icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/wikidesk-icon.ico"))
            }
            linux {
                packageName = "wikidesk"
                iconFile.set(project.file("src/main/resources/icons/wikidesk-icon-linux.png"))
            }
        }
    }
}
