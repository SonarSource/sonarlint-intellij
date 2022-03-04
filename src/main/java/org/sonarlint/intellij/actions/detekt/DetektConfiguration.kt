package org.sonarlint.intellij.actions.detekt

import io.github.detekt.tooling.api.spec.ProcessingSpec
import io.github.detekt.tooling.api.spec.RulesSpec
import org.sonar.api.config.Configuration
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

const val PATH_INPUT_DIRS_KEY = "sonar.sources"
const val CONFIG_PATH_NAME = "Detekt yaml configuration path"
const val CONFIG_PATH_KEY = "detekt.sonar.kotlin.config.path"
const val CONFIG_PATH_DESCRIPTION = "Path to the detekt yaml config." +
    " Path may be absolute or relative to the project base directory."
const val CONFIG_PATH_DEFAULT = ""

const val PATH_FILTERS_NAME = "Detekt path filters"
const val PATH_FILTERS_KEY = "detekt.sonar.kotlin.filters"
const val PATH_FILTERS_DESCRIPTIONS = "Regex based path filters eg. '**/test/**'. " +
    "All paths like '/my/custom/test/path' will be filtered."
const val PATH_FILTERS_DEFAULTS = "**/resources/**,**/build/**,**/target/**"

const val BASELINE_NAME = "Detekt baseline configuration path"
const val BASELINE_KEY = "detekt.sonar.kotlin.baseline.path"
const val BASELINE_DESCRIPTION = "Path to the detekt baseline xml file. " +
    "A baseline file is used to white- or blacklist code smells."
const val BASELINE_DEFAULT = ""

//fun createSpec(context: SensorContext): ProcessingSpec {
//    val baseDir = context.fileSystem().baseDir().toPath()
//    val settings = context.config()
//    return createSpec(baseDir, settings)
//}

fun createSpec(baseDir: Path, configuration: Configuration): ProcessingSpec {
    val configPath = tryFindDetektConfigurationFile(baseDir, configuration)
    val baselineFile = tryFindBaselineFile(baseDir, configuration)

    return ProcessingSpec {
        project {
            basePath = baseDir
            inputPaths = getInputPaths(configuration, baseDir)
            excludes = getProjectExcludeFilters(configuration)
        }
        rules {
            activateAllRules = true // publish all; quality profiles will filter
            maxIssuePolicy = RulesSpec.MaxIssuePolicy.AllowAny
            autoCorrect = false // never change user files and conflict with sonar's reporting
        }
        config {
            useDefaultConfig = true
            configPaths = listOfNotNull(configPath)
        }
        baseline {
            path = baselineFile
        }
    }
}

fun createSpec(baseDir: Path, pathList: List<Path>): ProcessingSpec {

    return ProcessingSpec {
        project {
            basePath = baseDir
            inputPaths = pathList
        }
        rules {
            activateAllRules = true // publish all; quality profiles will filter
            maxIssuePolicy = RulesSpec.MaxIssuePolicy.AllowAny
            autoCorrect = false // never change user files and conflict with sonar's reporting
        }
        config {
            useDefaultConfig = true
        }
//        baseline {
//            path = pathList.get(0)
//        }
    }
}

fun getInputPaths(configuration: Configuration, basePath: Path): List<Path> =
    configuration.get(PATH_INPUT_DIRS_KEY).map { sources ->
        sources.split(",").map { File(it.trim()).toPath() }
    }.orElse(listOf(basePath))

fun getProjectExcludeFilters(configuration: Configuration): List<String> =
    configuration.get(PATH_FILTERS_KEY)
        .orElse(PATH_FILTERS_DEFAULTS)
        .splitToSequence(",", ";")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

fun tryFindBaselineFile(baseDir: Path, config: Configuration): Path? {
    fun createBaselineFacade(path: Path): Path? {
//        logger.info("Registered baseline path: $path")
        var baselinePath = path

        if (Files.notExists(baselinePath)) {
            baselinePath = baseDir.resolve(path)
        }

        if (Files.notExists(baselinePath)) {
            val parentFile = baseDir.parent
            if (parentFile != null) {
                baselinePath = parentFile.resolve(path)
            } else {
                return null
            }
        }
        return baselinePath
    }
    return config.get(BASELINE_KEY)
        .map { Paths.get(it) }
        .map(::createBaselineFacade)
        .orElse(null)
}

private val supportedYamlEndings = setOf(".yaml", ".yml")

fun tryFindDetektConfigurationFile(baseDir: Path, configuration: Configuration): Path? =
    configuration.get(CONFIG_PATH_KEY).map { path ->
//        logger.info("Registered config path: $path")
        var configFile = Paths.get(path)

        if (Files.notExists(configFile) || supportedYamlEndings.any { path.toString().endsWith(it) }) {
            configFile = baseDir.resolve(path)
        }
        if (Files.notExists(configFile) || supportedYamlEndings.any { path.toString().endsWith(it) }) {
            val parentFile = baseDir.parent
            if (parentFile != null) {
                configFile = parentFile.resolve(path)
            } else {
                return@map null
            }
        }
        configFile
    }.orElse(null)
