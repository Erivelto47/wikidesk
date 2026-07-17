import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "dev.erivelto.wikidesk"
version = "1.0.2"

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

    testImplementation(kotlin("test"))
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
