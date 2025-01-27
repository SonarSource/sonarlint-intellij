/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.ui.ruledescription

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

// ObjectiveC file type is available in CLion, and C++ is available in IDEA
private val cFamilyLanguagesFileTypesByPriority = arrayOf("ObjectiveC", "C++")

// TypeScript file type is available in WebStorm, and JavaScript is available in IDEA
private val typescriptFileTypesByPriority = arrayOf("TypeScript", "JavaScript")

enum class RuleLanguages(private val language: Language, private vararg val fileTypesByPriorityOrder: String) {

    ANSIBLE(Language.ANSIBLE, "YAML"),
    C(Language.C, *cFamilyLanguagesFileTypesByPriority),
    // for now most code examples are written in YAML for CloudFormation
    CLOUD_FORMATION(Language.CLOUDFORMATION, "YAML"),
    CPP(Language.CPP, *cFamilyLanguagesFileTypesByPriority),
    CSH(Language.CS, "C#"),
    CSS(Language.CSS, "CSS"),
    // Dockerfile file type is available in WebStorm
    DOCKER(Language.DOCKER, "Dockerfile"),
    GO(Language.GO, "Go"),
    HTML(Language.HTML, "HTML"),
    JAVA(Language.JAVA, "JAVA"),
    JS(Language.JS, "JavaScript"),
    JSP(Language.JSP, "JAVA"),
    KOTLIN(Language.KOTLIN, "Kotlin"),
    KUBERNETES(Language.KUBERNETES, "YAML"),
    OBJC(Language.OBJC, *cFamilyLanguagesFileTypesByPriority),
    PHP(Language.PHP, "PHP"),
    PY(Language.PYTHON, "Python"),
    RUBY(Language.RUBY, "Ruby"),
    SCALA(Language.SCALA, "Scala"),
    // comes from the "Terraform and HCL" plugin from JetBrains (installable from the marketplace)
    TERRAFORM(Language.TERRAFORM, "Terraform"),
    TS(Language.TS, *typescriptFileTypesByPriority),
    XML(Language.XML, "XML"),
    YAML(Language.YAML, "YAML"),
    PLSQL(Language.PLSQL, "SQL");

    companion object {
        fun findFileTypeByRuleLanguage(language: Language): FileType {
            return values().find { it.language == language }?.fileTypesByPriorityOrder?.firstNotNullOfOrNull { fileType ->
                FileTypeRegistry.getInstance().findFileTypeByName(fileType)
            }
                ?: UnknownFileType.INSTANCE
        }
    }

}
