/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package org.sonarlint.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;

public abstract class AbstractSonarLintLightTests extends LightPlatformCodeInsightFixture4TestCase {

  private SonarLintGlobalSettings globalSettings;
  private SonarLintProjectSettings projectSettings;
  private SonarLintModuleSettings moduleSettings;
  private Disposable disposable;

  @Override
  protected final String getTestDataPath() {
    return Paths.get("src/test/testData/" + this.getClass().getSimpleName()).toAbsolutePath().toString();
  }

  @Before
  public final void init() {
    globalSettings = ApplicationManager.getApplication().getComponent(SonarLintGlobalSettings.class);
    projectSettings = getProject().getComponent(SonarLintProjectSettings.class);
    moduleSettings = getModule().getComponent(SonarLintModuleSettings.class);
    disposable = Disposer.newDisposable();
  }

  @After
  public final void restore() {
    globalSettings.setRules(Collections.emptyList());
    projectSettings.setProjectKey(null);
    projectSettings.setBindingEnabled(false);
    projectSettings.setFileExclusions(Collections.emptyList());
    moduleSettings.setIdePathPrefix("");
    moduleSettings.setSqPathPrefix("");
    if (!getProject().isDisposed()) {
      SonarLintStatus.get(getProject()).stopRun();
    }
    Disposer.dispose(disposable);
  }

  protected <T> void replaceProjectComponent(Class<T> clazz, T newInstance) {
    ((ComponentManagerImpl) getProject()).replaceComponentInstance(clazz, newInstance, disposable);
  }

  protected <T> void replaceProjectService(Class<T> clazz, T newInstance) {
    ((ComponentManagerImpl) getProject()).replaceServiceInstance(clazz, newInstance, disposable);
  }

  public SonarLintGlobalSettings getGlobalSettings() {
    return globalSettings;
  }

  public SonarLintProjectSettings getProjectSettings() {
    return projectSettings;
  }

  public SonarLintModuleSettings getModuleSettings() {
    return moduleSettings;
  }
}
