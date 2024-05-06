package org.sonarlint.intellij.core

import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

enum class FileLanguages(private val language: Language, private val ideLanguageName: String?) {

    C(Language.C, "C"),

    // for now most code examples are written in YAML for CloudFormation
    CLOUD_FORMATION(Language.CLOUDFORMATION, null),
    CPP(Language.CPP, "C++"),
    CSH(Language.CS, "C#"),
    CSS(Language.CSS, "CSS"),

    // Dockerfile file type is available in WebStorm
    DOCKER(Language.DOCKER, "Dockerfile"),
    GO(Language.GO, "Go"),
    HTML(Language.HTML, "HTML"),
    JAVA(Language.JAVA, "JAVA"),
    JS(Language.JS, "JavaScript"),
    JSP(Language.JSP, "JSP"),
    KOTLIN(Language.KOTLIN, "kotlin"),
    KUBERNETES(Language.KUBERNETES, null),
    OBJC(Language.OBJC, "ObjectiveC"),
    PHP(Language.PHP, "PHP"),
    PY(Language.PYTHON, "Python"),
    RUBY(Language.RUBY, "Ruby"),
    SCALA(Language.SCALA, "Scala"),

    // comes from the "Terraform and HCL" plugin from JetBrains (installable from the marketplace)
    TERRAFORM(Language.TERRAFORM, "Terraform"),
    TS(Language.TS, "TypeScript"),
    XML(Language.XML, "XML"),
    YAML(Language.YAML, "YAML"),
    PLSQL(Language.PLSQL, "SQL");

    companion object {
        fun findAssociatedLanguage(lang: com.intellij.lang.Language): Language? {
            val matchingLanguages = values().filter { it.ideLanguageName?.lowercase() == lang.id.lowercase() }
            return if (matchingLanguages.isEmpty() || matchingLanguages.size > 1) {
                null
            } else {
                matchingLanguages[0].language
            }
        }
    }

}