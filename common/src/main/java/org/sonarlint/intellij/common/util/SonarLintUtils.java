/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.common.util;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformUtils;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Transparency;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

public class SonarLintUtils {

  private static final Logger LOG = Logger.getInstance(SonarLintUtils.class);
  private static final String[] SONARCLOUD_ALIAS = {"https://sonarqube.com", "https://www.sonarqube.com",
    "https://www.sonarcloud.io", "https://sonarcloud.io"};

  private SonarLintUtils() {
    // Utility class
  }

  public static <T> T getService(Class<T> clazz) {
    var t = ApplicationManager.getApplication().getService(clazz);
    logAndThrowIfServiceNotFound(t, clazz.getName());

    return t;
  }

  public static <T> T getService(@NotNull Project project, Class<T> clazz) {
    var t = project.getService(clazz);
    logAndThrowIfServiceNotFound(t, clazz.getName());

    return t;
  }

  public static <T> T getService(@NotNull Module module, Class<T> clazz) {
    var t = ModuleServiceManager.getService(module, clazz);
    logAndThrowIfServiceNotFound(t, clazz.getName());

    return t;
  }

  private static <T> void logAndThrowIfServiceNotFound(T t, String name) {
    if (t == null) {
      LOG.error("Could not find service: " + name);
      throw new IllegalArgumentException("Class not found: " + name);
    }
  }

  public static boolean isSonarCloudAlias(@Nullable String url) {
    return url != null ? List.of(SONARCLOUD_ALIAS).contains(url) : false;
  }

  public static boolean isEmpty(@Nullable String str) {
    return str == null || str.isEmpty();
  }

  public static boolean isBlank(@Nullable String str) {
    return str == null || str.trim().isEmpty();
  }

  public static boolean equalsIgnoringTrailingSlash(String aString, String anotherString) {
    return withTrailingSlash(aString).equals(withTrailingSlash(anotherString));
  }

  private static String withTrailingSlash(String str) {
    if (!str.endsWith("/")) {
      return str + '/';
    }
    return str;
  }

  public static Image iconToImage(Icon icon) {
    if (icon instanceof ImageIcon) {
      return ((ImageIcon) icon).getImage();
    } else {
      var w = icon.getIconWidth();
      var h = icon.getIconHeight();
      var ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      var gd = ge.getDefaultScreenDevice();
      var gc = gd.getDefaultConfiguration();
      var image = gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
      var g = image.createGraphics();
      icon.paintIcon(null, g, 0, 0);
      g.dispose();
      return image;
    }
  }

  /**
   * FileEditorManager#getSelectedFiles does not work as expected. In split editors, the order of the files does not change depending
   * on which one of the split editors is selected.
   * This seems to work well with split editors.
   */
  @CheckForNull
  public static VirtualFile getSelectedFile(Project project) {
    if (project.isDisposed()) {
      return null;
    }
    var editorManager = FileEditorManager.getInstance(project);

    var editor = editorManager.getSelectedTextEditor();
    if (editor != null) {
      var doc = editor.getDocument();
      var docManager = FileDocumentManager.getInstance();
      return docManager.getFile(doc);
    }

    return null;
  }

  public static boolean isGeneratedSource(SourceFolder sourceFolder) {
    // copied from JavaProjectRootsUtil. Don't use that class because it's not available in other flavors of Intellij
    var properties = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
    var resourceProperties = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.RESOURCES);
    return (properties != null && properties.isForGeneratedSources()) || (resourceProperties != null && resourceProperties.isForGeneratedSources());
  }

  @Nullable
  public static SourceFolder getSourceFolder(@CheckForNull VirtualFile source, Module module) {
    if (source == null) {
      return null;
    }
    for (var entry : ModuleRootManager.getInstance(module).getContentEntries()) {
      for (var folder : entry.getSourceFolders()) {
        if (source.equals(folder.getFile())) {
          return folder;
        }
      }
    }
    return null;
  }

  public static boolean isJavaResource(SourceFolder source) {
    return JavaModuleSourceRootTypes.RESOURCES.contains(source.getRootType());
  }

  public static String pluralize(String str, long i) {
    if (i == 1) {
      return str;
    }
    return str + "s";
  }

  public static boolean isPhpLanguageRegistered() {
    return Language.findLanguageByID("PHP") != null;
  }

  public static boolean isPhpFile(@NotNull PsiFile file) {
    return "php".equalsIgnoreCase(file.getFileType().getName());
  }

  @CheckForNull
  public static String getIdeVersionForTelemetry() {
    String ideVersion = null;
    try {
      var appInfo = getAppInfo();
      ideVersion = appInfo.getVersionName() + " " + appInfo.getFullVersion();
      var edition = ApplicationNamesInfo.getInstance().getEditionName();
      if (edition != null) {
        ideVersion += " (" + edition + ")";
      }
    } catch (NullPointerException noAppInfo) {
      return null;
    }
    return ideVersion;
  }

  private static ApplicationInfo getAppInfo() {
    return ApplicationInfo.getInstance();
  }

  public static boolean isTaintVulnerabilitiesEnabled() {
    // No Taint Vulnerabilities in C/C++ for the time being
    return !PlatformUtils.isCLion();
  }

  public static boolean isModuleLevelBindingEnabled() {
    return !PlatformUtils.isRider() && !PlatformUtils.isCLion() && !PlatformUtils.isAppCode();
  }
}
