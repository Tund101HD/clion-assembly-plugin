import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.GenerateLexerTask
import org.jetbrains.intellij.platform.gradle.tasks.GenerateParserTask
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.intellij.platform.grammarkit")
    id("org.jetbrains.changelog")
}

val secrets = Properties().apply {
    val f = rootProject.file(".secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String? = secrets.getProperty(key)?.takeIf { it.isNotBlank() }

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        clion("2026.1.1")
        bundledPlugin("com.intellij.cmake")
        bundledPlugin("com.intellij.nativeDebug")
        testFramework(TestFrameworkType.Platform)
    }
}

sourceSets["main"].java.srcDirs("src/main/gen")

tasks {
    val generateNasmLexer by registering(GenerateLexerTask::class) {
        sourceFile.set(file("src/main/jflex/Nasm.flex"))
        targetRootOutputDir.set(file("src/main/gen"))
        purgeOldFiles.set(false)
    }
    val generateNasmParser by registering(GenerateParserTask::class) {
        sourceFile.set(file("src/main/grammar/Nasm.bnf"))
        targetRootOutputDir.set(file("src/main/gen"))
        purgeOldFiles.set(false)
    }
    val generateMipsLexer by registering(GenerateLexerTask::class) {
        sourceFile.set(file("src/main/jflex/Mips.flex"))
        targetRootOutputDir.set(file("src/main/gen"))
        purgeOldFiles.set(false)
    }
    val generateMipsParser by registering(GenerateParserTask::class) {
        sourceFile.set(file("src/main/grammar/Mips.bnf"))
        targetRootOutputDir.set(file("src/main/gen"))
        purgeOldFiles.set(false)
    }

    compileKotlin {
        dependsOn(generateNasmLexer, generateNasmParser, generateMipsLexer, generateMipsParser)
    }
    compileJava {
        dependsOn(generateNasmLexer, generateNasmParser, generateMipsLexer, generateMipsParser)
    }

    // `gradle clean` wipes generated sources. Needed after BNF rule renames/removals,
    // otherwise stale *Impl.java files reference visitor methods that no longer exist.
    named("clean") {
        delete("src/main/gen")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(project.version.toString()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
    signing {
        secret("intellij.signing.cert.chain")?.let { certificateChainFile = rootProject.file(it) }
        secret("intellij.signing.private.key")?.let { privateKeyFile = rootProject.file(it) }
        secret("intellij.signing.password")?.let { password = it }
    }
    publishing {
        secret("intellij.publish.token")?.let { token = it }
    }
}

changelog {
    version.set(project.version.toString())
    repositoryUrl.set("https://github.com/Tund101HD/clion-nasm")
    groups.set(emptyList())
}
