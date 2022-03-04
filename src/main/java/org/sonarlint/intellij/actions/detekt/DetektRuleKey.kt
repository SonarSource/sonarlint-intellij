package org.sonarlint.intellij.actions.detekt

import io.github.detekt.tooling.api.DefaultConfigurationProvider
import io.gitlab.arturbosch.detekt.api.BaseRule
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.MultiRule
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import java.util.ServiceLoader

internal val defaultConfig: Config = DefaultConfigurationProvider.load().get()

/**
 * Exclude similar or duplicated rule implementations from other rule sets than the default one.
 */
internal val excludedDuplicates = setOf(
    "Filename", // MatchingDeclarationName
    "FinalNewline", // NewLineAtEndOfFile
    "MaximumLineLength", // MaxLineLength
    "NoUnitReturn", // OptionalUnit
    "NoWildcardImports", // WildcardImport
    "MultiLineIfElse" // MandatoryBracesIfStatements
)

public val allLoadedRules: List<Rule> =
    ServiceLoader.load(RuleSetProvider::class.java, Config::class.java.classLoader)
        .asSequence()
        .flatMap { loadRules(it).asSequence() }
        .flatMap { (it as? MultiRule)?.rules?.asSequence() ?: sequenceOf(it) }
        .filterIsInstance<Rule>()
        .filterNot { it.ruleId in excludedDuplicates }
        .toList()

private fun loadRules(provider: RuleSetProvider): List<BaseRule> {
    val subConfig = defaultConfig.subConfig(provider.ruleSetId)
    return provider.instance(subConfig).rules
}


