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
package org.sonarlint.intellij;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.fixtures.SonarLintToolWindowFixture;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public abstract class AbstractSonarLintLightTests extends LightPlatformCodeInsightFixture4TestCase {

  private SonarLintGlobalSettings globalSettings;
  private SonarLintProjectSettings projectSettings;
  private SonarLintModuleSettings moduleSettings;
  private Disposable disposable;
  private SonarLintToolWindowFixture toolWindowFixture;

  @Override
  protected final String getTestDataPath() {
    return Paths.get("src/test/testData/" + this.getClass().getSimpleName()).toAbsolutePath().toString();
  }

  @Before
  public final void init() {
    globalSettings = Settings.getGlobalSettings();
    projectSettings = getSettingsFor(getProject());
    moduleSettings = getSettingsFor(getModule());
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
    if (toolWindowFixture != null) {
      toolWindowFixture.cleanUp();
    }
    if (!getProject().isDisposed()) {
      SonarLintStatus.get(getProject()).stopRun();
    }
    Disposer.dispose(disposable);
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

  public VirtualFile createTestFile(String fileName, Language language, String text) {
    return createTestPsiFile(fileName, language, text).getVirtualFile();
  }

  public VirtualFile createAndOpenTestVirtualFile(String fileName, Language language, String text) {
    VirtualFile file = createTestFile(fileName, language, text);
    FileEditorManager.getInstance(getProject()).openFile(file, false);
    return file;
  }

  public PsiFile createAndOpenTestPsiFile(String fileName, Language language, String text) {
    PsiFile file = createTestPsiFile(fileName, language, text);
    FileEditorManager.getInstance(getProject()).openFile(file.getVirtualFile(), false);
    return file;
  }

  public PsiFile createTestPsiFile(String fileName, Language language, String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, language, text, true, true);
  }

  public void loadToolWindow() {
    toolWindowFixture = SonarLintToolWindowFixture.createFor(getProject());
    replaceProjectService(ToolWindowManager.class, toolWindowFixture.getManager());
  }
}
