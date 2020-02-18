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
package org.sonarlint.intellij.util;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
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

  public static void configureProxy(String host, ServerConfiguration.Builder builder) {
    configureProxy(host, builder::proxy, builder::proxyCredentials);
  }

  public static void configureProxy(String host, TelemetryClientConfig.Builder builder) {
    configureProxy(host, builder::proxy, (user, pwd) -> {
      builder.proxyLogin(user);
      builder.proxyPassword(pwd);
    });
  }

  private static void configureProxy(String host, Consumer<Proxy> proxyConsumer, BiConsumer<String, String> credentialsConsumer) {
    HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
    if (httpConfigurable == null) {
      // Unit tests
      return;
    }
    if (!isHttpProxyEnabledForUrl(httpConfigurable, host)) {
      return;
    }
    Proxy.Type type = httpConfigurable.PROXY_TYPE_IS_SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP;

    Proxy proxy = new Proxy(type, new InetSocketAddress(httpConfigurable.PROXY_HOST, httpConfigurable.PROXY_PORT));
    proxyConsumer.accept(proxy);

    if (httpConfigurable.PROXY_AUTHENTICATION) {
      String proxyLogin = httpConfigurable.getProxyLogin();
      if (proxyLogin != null) {
        credentialsConsumer.accept(proxyLogin, httpConfigurable.getPlainProxyPassword());
      }
    }
  }

  public static boolean isGeneratedSource(SourceFolder sourceFolder) {
    // copied from JavaProjectRootsUtil. Don't use that class because it's not available in other flavors of Intellij
    JavaSourceRootProperties properties = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
    JavaResourceRootProperties resourceProperties = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.RESOURCES);
    return (properties != null && properties.isForGeneratedSources()) || (resourceProperties != null && resourceProperties.isForGeneratedSources());
  }

  @Nullable
  public static SourceFolder getSourceFolder(@CheckForNull VirtualFile source, Module module) {
    if (source == null) {
      return null;
    }
    for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
      for (SourceFolder folder : entry.getSourceFolders()) {
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

  public static ServerConfiguration getServerConfiguration(SonarQubeServer server) {
    return getServerConfiguration(server, CONNECTION_TIMEOUT_MS, READ_TIMEOUT_MS);
  }

  public static ServerConfiguration getServerConfiguration(SonarQubeServer server, int connectTimeout, int readTimeout) {
    CertificateManager certificateManager = CertificateManager.getInstance();
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

  public static String pluralize(String str, long i) {
    if (i == 1) {
      return str;
    }
    return str + "s";
  }

  /**
   * Convert a relative path on a platform not using forward slashes as a path separator to a path
   * with forward slashes.
   */
  public static String toForwardSlashes(String relativePath) {
    if (File.separatorChar != '/') {
      return relativePath.replaceAll(PATH_SEPARATOR_PATTERN, "/");
    }
    return relativePath;
  }

  public static boolean isPhpFileInPhpStorm(FileType fileType) {
    return isPhpStorm() && "php".equalsIgnoreCase(fileType.getName());
  }

  private static boolean isPhpStorm() {
    return getApplicationVersionName().toLowerCase(Locale.US).contains("phpstorm");
  }

  private static String getApplicationVersionName() {
    try {
      return getAppInfo().getVersionName();
    } catch (NullPointerException noAppInfo) {
      return "<!unknown!>";
    }
  }

  @CheckForNull
  public static String getIdeVersionForTelemetry() {
    try {
      ApplicationInfo appInfo = getAppInfo();
      return appInfo.getVersionName() + " " + appInfo.getFullVersion();
    } catch (NullPointerException noAppInfo) {
      return null;
    }
  }

  private static ApplicationInfo getAppInfo() {
    return ApplicationInfo.getInstance();
  }
}
