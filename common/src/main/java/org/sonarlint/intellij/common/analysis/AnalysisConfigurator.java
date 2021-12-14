/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.common.analysis;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.sonarsource.sonarlint.core.commons.Language;

public interface AnalysisConfigurator {
  // Name is constructed from plugin-id.extension-point-name
  ExtensionPointName<AnalysisConfigurator> EP_NAME = ExtensionPointName.create("org.sonarlint.idea.analysisConfiguration");

  AnalysisConfiguration configure(Module module, Collection<VirtualFile> filesToAnalyze);


  class AnalysisConfiguration {
    /**
     * Additional analysis properties that will be passed to analyzers
     */
    public final Map<String, String> extraProperties = new HashMap<>();

    /**
     * Force the language of a file, instead of relying on default language detection mechanism (based on file suffixes)
     */
    public final Map<VirtualFile, Language> forcedLanguages = new HashMap<>();


  }
}
