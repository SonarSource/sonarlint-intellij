/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.HttpConfigurable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

public class SonarLintUtils {

  private static final Logger LOG = Logger.getInstance(SonarLintUtils.class);
  public static final int CONNECTION_TIMEOUT_MS = 30_000;

  private SonarLintUtils() {
    // Utility class
  }

  public static <T> T get(ComponentManager container, Class<T> clazz) {
    T t = container.getComponent(clazz);
    if (t == null) {
      LOG.error("Could not find class in container: {}", clazz.getName());
      throw new IllegalArgumentException("Class not found: " + clazz.getName());
    }

    return t;
  }

  public static boolean isEmpty(@Nullable String str) {
    return str == null || str.isEmpty();
  }

  public static boolean isBlank(@Nullable String str) {
    return str == null || str.trim().isEmpty();
  }

  public static <T> T get(Class<T> clazz) {
    return get(ApplicationManager.getApplication(), clazz);
  }

  public static boolean saveFiles(final Collection<VirtualFile> virtualFiles) {
    boolean success = true;
    for (VirtualFile file : virtualFiles) {
      if (!saveFile(file)) {
        success = false;
      }
    }
    return success;
  }

  /**
   * FileEditorManager#getSelectedFiles does not work as expected. In split editors, the order of the files does not change depending
   * on which one of the split editors is selected.
   * This seems to work well with split editors.
   */
  @Nullable
  public static VirtualFile getSelectedFile(Project project) {
    FileEditorManager editorManager = FileEditorManager.getInstance(project);
    FileDocumentManager docManager = FileDocumentManager.getInstance();

    Editor editor = editorManager.getSelectedTextEditor();
    if (editor != null) {
      Document doc = editor.getDocument();
      return docManager.getFile(doc);
    }

    return null;
  }

  public static boolean saveFile(@NotNull final VirtualFile virtualFile) {
    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    if (fileDocumentManager.isFileModified(virtualFile)) {
      final Document document = fileDocumentManager.getDocument(virtualFile);
      if (document != null) {
        ApplicationManager.getApplication().invokeAndWait(() -> fileDocumentManager.saveDocument(document), ModalityState.any());
        return true;
      }
    }
    return false;
  }

  public static boolean shouldAnalyzeAutomatically(@Nullable VirtualFile file, @Nullable Module module) {
    if (!shouldAnalyze(file, module) || file == null || module == null) {
      return false;
    }

    // file and module not null here
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ContentEntry[] entries = moduleRootManager.getContentEntries();

    for (ContentEntry e : entries) {
      if (isExcludedOrUnderExcludedDirectory(file, e)) {
        SonarLintConsole.get(module.getProject()).debug("Not automatically analysing excluded file: " + file.getName());
        return false;
      }

      SourceFolder[] sourceFolders = e.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        if (sourceFolder.getFile() == null || sourceFolder.isSynthetic()) {
          continue;
        }

        if (VfsUtil.isAncestor(sourceFolder.getFile(), file, false)) {
          if (isJavaResource(sourceFolder)) {
            SonarLintConsole.get(module.getProject()).debug("Not automatically analysing file under resources: " + file.getName());
            return false;
          } else if (isJavaGeneratedSource(sourceFolder)) {
            SonarLintConsole.get(module.getProject()).debug("Not automatically analysing file belonging to generated source folder: " + file.getName());
            return false;
          }

          return true;
        }
      }
    }

    // java must be in a source root. For other files, we always analyse them.
    return !"java".equalsIgnoreCase(file.getFileType().getDefaultExtension());
  }

  public static boolean isExcludedOrUnderExcludedDirectory(final VirtualFile file, ContentEntry contentEntry) {
    for (VirtualFile excludedDir : contentEntry.getExcludeFolderFiles()) {
      if (VfsUtil.isAncestor(excludedDir, file, false)) {
        return true;
      }
    }
    return false;
  }

  public static boolean shouldAnalyze(@Nullable VirtualFile file, @Nullable Module module) {
    if (file == null || module == null) {
      return false;
    }

    if (module.isDisposed() || module.getProject().isDisposed()) {
      return false;
    }

    if (!file.isInLocalFileSystem() || file.getFileType().isBinary() || !file.isValid()
      || ".idea".equals(file.getParent().getName())) {
      return false;
    }

    // In PHPStorm the same PHP file is analyzed twice (once as PHP file and once as HTML file)
    if ("html".equalsIgnoreCase(file.getFileType().getName())) {
      return false;
    }

    return true;
  }

  public static void configureProxy(String host, ServerConfiguration.Builder builder) {
    HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
    if (isHttpProxyEnabledForUrl(httpConfigurable, host)) {
      Proxy.Type type = httpConfigurable.PROXY_TYPE_IS_SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP;

      Proxy proxy = new Proxy(type, new InetSocketAddress(httpConfigurable.PROXY_HOST, httpConfigurable.PROXY_PORT));
      builder.proxy(proxy);

      if (httpConfigurable.PROXY_AUTHENTICATION) {
        // Different ways to fetch login based on runtime version (SLI-95)
        try {
          Object proxyLogin = tryGetProxyLogin(httpConfigurable);
          if (proxyLogin != null) {
            builder.proxyCredentials(proxyLogin.toString(), httpConfigurable.getPlainProxyPassword());
          }
        } catch (Exception ex) {
          LOG.warn("Could not fetch value for proxy login", ex);
        }
      }
    }
  }

  private static Object tryGetProxyLogin(HttpConfigurable httpConfigurable) throws Exception {
    try {
      Field proxyLoginField = HttpConfigurable.class.getField("PROXY_LOGIN");
      return proxyLoginField.get(httpConfigurable);
    } catch (NoSuchFieldException ex) {
      // field doesn't exist -> we are in version >= 2016.2
      Method proxyLoginMethod = HttpConfigurable.class.getMethod("getProxyLogin");
      return proxyLoginMethod.invoke(httpConfigurable);
    }
  }

  public static boolean isJavaGeneratedSource(SourceFolder source) {
    // only return non-null if source has root type in JavaModuleSourceRootTypes.SOURCES
    JavaSourceRootProperties properties = source.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
    return properties != null && properties.isForGeneratedSources();
  }

  public static boolean isJavaResource(SourceFolder source) {
    return JavaModuleSourceRootTypes.RESOURCES.contains(source.getRootType());
  }

  public static ServerConfiguration getServerConfiguration(SonarQubeServer server) {
    SonarApplication sonarlint = get(SonarApplication.class);
    String version = sonarlint != null ? sonarlint.getVersion() : "";
    ServerConfiguration.Builder serverConfigBuilder = ServerConfiguration.builder()
      .userAgent("SonarLint IntelliJ " + version)
      .connectTimeoutMilliseconds(CONNECTION_TIMEOUT_MS)
      .readTimeoutMilliseconds(CONNECTION_TIMEOUT_MS)
      .url(server.getHostUrl());
    if (server.getToken() != null) {
      serverConfigBuilder.token(server.getToken());
    } else {
      serverConfigBuilder.credentials(server.getLogin(), server.getPassword());
    }

    if (server.enableProxy()) {
      SonarLintUtils.configureProxy(server.getHostUrl(), serverConfigBuilder);
    }
    return serverConfigBuilder.build();
  }

  /**
   * Copy of {@link HttpConfigurable#isHttpProxyEnabledForUrl(String)}, which doesn't exist in IDEA 14.
   */
  public static boolean isHttpProxyEnabledForUrl(HttpConfigurable httpConfigurable, @Nullable String url) {
    if (!httpConfigurable.USE_HTTP_PROXY) {
      return false;
    }
    URI uri = url != null ? VfsUtil.toUri(url) : null;
    return uri == null || !isProxyException(httpConfigurable, uri.getHost());
  }

  public static boolean isProxyException(HttpConfigurable httpConfigurable, @org.jetbrains.annotations.Nullable String uriHost) {
    if (StringUtil.isEmptyOrSpaces(uriHost) || StringUtil.isEmptyOrSpaces(httpConfigurable.PROXY_EXCEPTIONS)) {
      return false;
    }

    List<String> hosts = StringUtil.split(httpConfigurable.PROXY_EXCEPTIONS, ",");
    for (String hostPattern : hosts) {
      String regexpPattern = StringUtil.escapeToRegexp(hostPattern.trim()).replace("\\*", ".*");
      if (Pattern.compile(regexpPattern).matcher(uriHost).matches()) {
        return true;
      }
    }

    return false;
  }

  public static String age(long creationDate) {
    Date date = new Date(creationDate);
    Date now = new Date();
    long days = TimeUnit.MILLISECONDS.toDays(now.getTime() - date.getTime());
    if (days > 0) {
      return pluralize(days, "day", "days");
    }
    long hours = TimeUnit.MILLISECONDS.toHours(now.getTime() - date.getTime());
    if (hours > 0) {
      return pluralize(hours, "hour", "hours");
    }
    long minutes = TimeUnit.MILLISECONDS.toMinutes(now.getTime() - date.getTime());
    if (minutes > 0) {
      return pluralize(minutes, "minute", "minutes");
    }

    return "few seconds ago";
  }

  private static String pluralize(long strictlyPositiveCount, String singular, String plural) {
    if (strictlyPositiveCount == 1) {
      return "1 " + singular + " ago";
    }
    return strictlyPositiveCount + " " + plural + " ago";
  }

  public static String getRelativePath(Project project, VirtualFile virtualFile) {
    if (project.getBasePath() == null) {
      throw new IllegalStateException("no base path in default project");
    }
    return Paths.get(project.getBasePath()).relativize(Paths.get(virtualFile.getPath())).toString();
  }

}
