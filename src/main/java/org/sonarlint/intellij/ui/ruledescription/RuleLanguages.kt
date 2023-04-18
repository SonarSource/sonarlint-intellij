/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import org.sonarsource.sonarlint.core.commons.Language

enum class RuleLanguages(val language: Language, val fileType: String?) {

    ABAP(Language.ABAP, null),
    APEX(Language.APEX, null),
    C(Language.C, "C++"),
    CLOUD_FORMATION(Language.CLOUDFORMATION, "YAML"),
    COBOL(Language.COBOL, null),
    CPP(Language.CPP, "C++"),
    CSH(Language.CS, "C#"),
    CSS(Language.CSS, "CSS"),
    DOCKER(Language.DOCKER, null),
    GO(Language.GO, "Go"),
    HTML(Language.HTML, "HTML"),
    IPYTHON(Language.IPYTHON, "Python"),
    JAVA(Language.JAVA, "JAVA"),
    JS(Language.JS, "JavaScript"),
    JSP(Language.JSP, "JAVA"),
    KOTLIN(Language.KOTLIN, "Kotlin"),
    KUBERNETES(Language.KUBERNETES, "YAML"),
    OBJC(Language.OBJC, "C++"),
    PHP(Language.PHP, "PHP"),
    PLI(Language.PLI, "SQL"),
    PLSQL(Language.PLSQL, "SQL"),
    PY(Language.PYTHON, "Python"),
    RPG(Language.RPG, null),
    RUBY(Language.RUBY, "Ruby"),
    SCALA(Language.SCALA, "Scala"),
    SECRETS(Language.SECRETS, null),
    SWIFT(Language.SWIFT, null),
    TERRAFORM(Language.TERRAFORM, "YAML"),
    TSQL(Language.TSQL, null),
    TS(Language.TS, "JavaScript"),
    VBNET(Language.VBNET, null),
    XML(Language.XML, "XML"),
    YAML(Language.YAML, "YAML");

    companion object {
        fun findFileTypeByRuleLanguage(value: String): FileType {
            val ideName = RuleLanguages.values().firstOrNull { it.language.languageKey == value }?.fileType
            return ideName?.let { FileTypeRegistry.getInstance().findFileTypeByName(it) ?: UnknownFileType.INSTANCE }
                ?: UnknownFileType.INSTANCE
        }
    }

}
