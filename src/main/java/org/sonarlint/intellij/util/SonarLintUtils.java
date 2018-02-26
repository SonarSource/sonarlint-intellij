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
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.ssl.CertificateManager;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

public class SonarLintUtils {

  private static final Logger LOG = Logger.getInstance(SonarLintUtils.class);
  static final int CONNECTION_TIMEOUT_MS = 30_000;
  static final int READ_TIMEOUT_MS = 10 * 60_000;
  static final String PATH_SEPARATOR_PATTERN = Pattern.quote(File.separator);
  private static final String[] SONARCLOUD_ALIAS = {"https://sonarqube.com", "https://www.sonarqube.com",
    "https://www.sonarcloud.io", "https://sonarcloud.io"};

  private SonarLintUtils() {
    // Utility class
  }

  public static <T> T get(ComponentManager container, Class<T> clazz) {
    T t = container.getComponent(clazz);
    if (t == null) {
      LOG.error("Could not find class in container: " + clazz.getName());
      throw new IllegalArgumentException("Class not found: " + clazz.getName());
    }

    return t;
  }

  public static boolean isSonarCloudAlias(@Nullable String url) {
    return Arrays.asList(SONARCLOUD_ALIAS).contains(url);
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

  public static Image iconToImage(Icon icon) {
    if (icon instanceof ImageIcon) {
      return ((ImageIcon) icon).getImage();
    } else {
      int w = icon.getIconWidth();
      int h = icon.getIconHeight();
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice gd = ge.getDefaultScreenDevice();
      GraphicsConfiguration gc = gd.getDefaultConfiguration();
      BufferedImage image = gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
      Graphics2D g = image.createGraphics();
      icon.paintIcon(null, g, 0, 0);
      g.dispose();
      return image;
    }
  }

  /**
   * Must be called from EDT
   */
  public static boolean saveFiles(final Collection<VirtualFile> virtualFiles) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean[] success = new boolean[] {true};

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (VirtualFile file : virtualFiles) {
        if (!saveFile(file)) {
          success[0] = false;
        }
      }
    });
    return success[0];
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
    FileEditorManager editorManager = FileEditorManager.getInstance(project);

    Editor editor = editorManager.getSelectedTextEditor();
    if (editor != null) {
      Document doc = editor.getDocument();
      FileDocumentManager docManager = FileDocumentManager.getInstance();
      return docManager.getFile(doc);
    }

    return null;
  }

  /**
   * Must be called from EDT
   */
  private static boolean saveFile(final VirtualFile virtualFile) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    if (fileDocumentManager.isFileModified(virtualFile)) {
      final Document document = fileDocumentManager.getDocument(virtualFile);
      if (document != null) {
        fileDocumentManager.saveDocument(document);
        return true;
      }
    }
    return false;
  }

  public static boolean isExcludedOrUnderExcludedDirectory(final VirtualFile file, ContentEntry contentEntry) {
    for (VirtualFile excludedDir : contentEntry.getExcludeFolderFiles()) {
      if (VfsUtil.isAncestor(excludedDir, file, false)) {
        return true;
      }
    }
    return false;
  }

  public static void configureProxy(String host, ServerConfiguration.Builder builder) {
    HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
    if (!isHttpProxyEnabledForUrl(httpConfigurable, host)) {
      return;
    }
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
    return getServerConfiguration(server, CONNECTION_TIMEOUT_MS, READ_TIMEOUT_MS);
  }

  public static ServerConfiguration getServerConfiguration(SonarQubeServer server, int connectTimeout, int readTimeout) {
    CertificateManager certificateManager = get(CertificateManager.class);
    SonarApplication sonarlint = get(SonarApplication.class);
    ServerConfiguration.Builder serverConfigBuilder = ServerConfiguration.builder()
      .userAgent("SonarLint IntelliJ " + sonarlint.getVersion())
      .connectTimeoutMilliseconds(connectTimeout)
      .readTimeoutMilliseconds(readTimeout)
      .sslSocketFactory(certificateManager.getSslContext().getSocketFactory())
      .trustManager(certificateManager.getCustomTrustManager())
      .url(server.getHostUrl());
    if (!isBlank(server.getOrganizationKey())) {
      serverConfigBuilder.organizationKey(server.getOrganizationKey());
    }
    if (!isBlank(server.getToken())) {
      serverConfigBuilder.token(server.getToken());
    } else {
      serverConfigBuilder.credentials(server.getLogin(), server.getPassword());
    }

    if (server.enableProxy()) {
      configureProxy(server.getHostUrl(), serverConfigBuilder);
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

  public static String getRelativePath(Project project, VirtualFile virtualFile) {
    if (project.getBasePath() == null) {
      throw new IllegalStateException("The project has no base path");
    }
    return Paths.get(project.getBasePath()).relativize(Paths.get(virtualFile.getPath())).toString();
  }

  /**
   * Convert relative path to SonarQube file key
   *
   * @param relativePath relative path string in the local OS
   * @return SonarQube file key
   */
  public static String toFileKey(String relativePath) {
    if (File.separatorChar != '/') {
      return relativePath.replaceAll(PATH_SEPARATOR_PATTERN, "/");
    }
    return relativePath;
  }

}
