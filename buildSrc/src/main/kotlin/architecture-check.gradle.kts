// ---------------------------------------------------------------------------
// Architecture rule enforcement
//
// Detekt's built-in rules can't express "forbidden here, except in that one
// file" (visibility boundaries, dispatcher injection exceptions, etc.), so
// these four checks are a small regex-based task instead of a custom Detekt
// rule set. They exist because the same mistakes repeated across
// audit/reports/*.md: prose-only rules in CLAUDE.md/ADRs kept getting
// violated in freshly written code.
//
// Pre-existing violations are grandfathered in config/architecture/baseline.txt
// (same boy-scout policy as the Detekt baseline: shrink it, never grow it).
// ---------------------------------------------------------------------------

data class ArchViolation(
    val rule: String,
    val path: String,
    val line: Int,
) {
    override fun toString() = "$rule|$path|$line"
}

fun findArchViolations(srcDir: File): List<ArchViolation> {
    val violations = mutableListOf<ArchViolation>()
    val basePath = srcDir.toPath()

    fun relPath(f: File) = basePath.relativize(f.toPath()).toString().replace('\\', '/')

    fun ktFiles(dir: File) = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }

    // Rule 1: visibility — repository impls / DAOs / entities / mappers must be `internal`.
    val visibilityDirs =
        listOf(
            "dev/gaferneira/notificapp/core/data/repository",
            "dev/gaferneira/notificapp/core/data/local/dao",
            "dev/gaferneira/notificapp/core/data/local/entity",
            "dev/gaferneira/notificapp/core/data/local/mapper",
        )
    val declRegex = Regex("""^(?!.*\binternal\b)(?!.*\bprivate\b).*\b(class|interface|object)\s+\w""")
    val implFileRegex = Regex(""".*Impl\.kt$""")
    visibilityDirs.forEach { dir ->
        val root = File(srcDir, dir)
        if (!root.exists()) return@forEach
        ktFiles(root).forEach { f ->
            if (dir.endsWith("/repository") && !implFileRegex.matches(f.name)) return@forEach
            f.readLines().forEachIndexed { idx, line ->
                if (declRegex.containsMatchIn(line)) {
                    violations += ArchViolation("visibility", relPath(f), idx + 1)
                }
            }
        }
    }

    // Rule 2: dispatcher — no hardcoded Dispatchers.IO/Default/Main outside the documented seams.
    val dispatcherExceptions =
        setOf(
            "dev/gaferneira/notificapp/core/di/DispatchersModule.kt",
            "dev/gaferneira/notificapp/core/ui/mvi/MviViewModel.kt",
            "dev/gaferneira/notificapp/core/ui/mvi/CollectOneOffEffects.kt",
            "dev/gaferneira/notificapp/core/ui/utils/LocalIoDispatcher.kt",
        )
    val dispatcherRegex = Regex("""Dispatchers\.(IO|Default|Main)\b""")
    ktFiles(srcDir).forEach { f ->
        val rel = relPath(f)
        if (rel in dispatcherExceptions) return@forEach
        f.readLines().forEachIndexed { idx, line ->
            if (dispatcherRegex.containsMatchIn(line)) {
                violations += ArchViolation("dispatcher", rel, idx + 1)
            }
        }
    }

    // Rule 3: effect-collect — one-off effects must go through CollectOneOffEffects.
    val effectCollectRegex = Regex("""\.effect\.collect\b""")
    ktFiles(srcDir).forEach { f ->
        val rel = relPath(f)
        if (rel.endsWith("core/ui/mvi/CollectOneOffEffects.kt")) return@forEach
        f.readLines().forEachIndexed { idx, line ->
            if (effectCollectRegex.containsMatchIn(line)) {
                violations += ArchViolation("effect-collect", rel, idx + 1)
            }
        }
    }

    // Rule 4: platform-static — no PackageManager / Settings.Secure in ViewModels or domain.
    val platformStaticRegex = Regex("""PackageManager|Settings\.Secure""")
    val featuresDir = File(srcDir, "dev/gaferneira/notificapp/features")
    if (featuresDir.exists()) {
        ktFiles(featuresDir)
            .filter { it.path.replace('\\', '/').contains("/viewmodel/") }
            .forEach { f ->
                f.readLines().forEachIndexed { idx, line ->
                    if (platformStaticRegex.containsMatchIn(line)) {
                        violations += ArchViolation("platform-static", relPath(f), idx + 1)
                    }
                }
            }
    }
    val domainDir = File(srcDir, "dev/gaferneira/notificapp/domain")
    if (domainDir.exists()) {
        ktFiles(domainDir).forEach { f ->
            f.readLines().forEachIndexed { idx, line ->
                if (platformStaticRegex.containsMatchIn(line)) {
                    violations += ArchViolation("platform-static", relPath(f), idx + 1)
                }
            }
        }

        // Rule 5: domain-purity — domain/ must never depend on features/ (dependency-inversion).
        val domainPurityRegex = Regex("""^import dev\.gaferneira\.notificapp\.features\.""")
        ktFiles(domainDir).forEach { f ->
            f.readLines().forEachIndexed { idx, line ->
                if (domainPurityRegex.containsMatchIn(line)) {
                    violations += ArchViolation("domain-purity", relPath(f), idx + 1)
                }
            }
        }
    }

    // Rule 6: raw-exception-leak — repository/data-source catch blocks must not hand a raw
    // exception back through Result.failure(...); it must be mapped to the Failure hierarchy
    // (ADR 006) before crossing the data->UI boundary. Every current call site predates that
    // mapping helper, so this rule freezes the count until the helper exists.
    val rawExceptionRegex = Regex("""Result\.failure\(""")
    val dataDir = File(srcDir, "dev/gaferneira/notificapp/core/data")
    if (dataDir.exists()) {
        ktFiles(dataDir).forEach { f ->
            f.readLines().forEachIndexed { idx, line ->
                if (rawExceptionRegex.containsMatchIn(line)) {
                    violations += ArchViolation("raw-exception-leak", relPath(f), idx + 1)
                }
            }
        }
    }

    // Rule 7: contract-purity — a feature's contract/ (its public UiState/Event/Effect surface)
    // must not leak an engine-internal type like core.extraction.ExtractionResult; map to a
    // feature-owned model at the ViewModel boundary instead.
    val contractPurityRegex = Regex("""^import dev\.gaferneira\.notificapp\.core\.extraction\.""")
    ktFiles(srcDir)
        .filter { it.path.replace('\\', '/').contains("/contract/") }
        .forEach { f ->
            f.readLines().forEachIndexed { idx, line ->
                if (contractPurityRegex.containsMatchIn(line)) {
                    violations += ArchViolation("contract-purity", relPath(f), idx + 1)
                }
            }
        }

    return violations.distinct().sortedBy { it.toString() }
}

val architectureCheck =
    tasks.register("architectureCheck") {
        group = "verification"
        description = "Fails on NEW violations of Notificapp architecture rules not covered by Detekt " +
            "(data-layer visibility, dispatcher injection, MVI effect collection, platform statics in " +
            "ViewModels/domain, domain/features dependency direction, unmapped repository exceptions, " +
            "contract purity). Pre-existing violations are grandfathered in config/architecture/baseline.txt."

        val srcDir = file("src/main/kotlin")
        val baselineFile = file("$rootDir/config/architecture/baseline.txt")
        inputs.dir(srcDir)
        inputs.file(baselineFile)

        doLast {
            val current = findArchViolations(srcDir)
            val baseline =
                if (baselineFile.exists()) {
                    baselineFile
                        .readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toSet()
                } else {
                    emptySet()
                }

            val currentKeys = current.map { it.toString() }.toSet()
            val newViolations = current.filter { it.toString() !in baseline }
            val resolvedBaselineEntries = baseline - currentKeys

            if (newViolations.isNotEmpty()) {
                val message =
                    buildString {
                        appendLine("Architecture check found ${newViolations.size} NEW violation(s) not present in the baseline:")
                        newViolations.forEach { v -> appendLine("  [${v.rule}] ${v.path}:${v.line}") }
                        appendLine()
                        appendLine("Fix these before committing. If this is intentionally accepted debt, add the")
                        appendLine("line(s) to config/architecture/baseline.txt and call it out in the PR description.")
                    }
                throw GradleException(message)
            }

            if (resolvedBaselineEntries.isNotEmpty()) {
                logger.lifecycle(
                    "Architecture check: ${resolvedBaselineEntries.size} baseline entr" +
                        (if (resolvedBaselineEntries.size == 1) "y" else "ies") +
                        " no longer reproduce — remove from config/architecture/baseline.txt to shrink the baseline:",
                )
                resolvedBaselineEntries.forEach { logger.lifecycle("  $it") }
            }
        }
    }

tasks.named("check") { dependsOn(architectureCheck) }
