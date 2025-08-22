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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.java.JavaAnalysisConfigurator;

import static org.assertj.core.api.Assertions.assertThat;

class JavaAnalysisConfiguratorInvalidEntryTests extends AbstractSonarLintLightTests {

  private final JavaAnalysisConfigurator underTest = new JavaAnalysisConfigurator();

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new LightProjectDescriptor() {
      @Override
      public Sdk getSdk() {
        return null;
      }

      @Override
      public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        // Inheriting a null JDK entry
        model.inheritSdk();
      }

    };
  }

  @Test
  void testClasspathIgnoreInvalidJdkEntries() {
    final var props = underTest.configure(getModule(), Collections.emptyList()).extraProperties;
    assertThat(props).containsOnlyKeys("sonar.java.source", "sonar.java.target", "sonar.java.enablePreview");
  }

}
