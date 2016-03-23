package org.sonarlint.intellij.core;

import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.net.HttpConfigurable;
import java.net.InetSocketAddress;
import java.net.Proxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

public class ServerUpdateTask extends com.intellij.openapi.progress.Task.Modal {
  private static final Logger LOGGER = Logger.getInstance(ServerUpdateTask.class);
  private final ConnectedSonarLintEngine engine;
  private final SonarQubeServer server;
  private final String projectKey;
  private final boolean onlyModules;

  public ServerUpdateTask(ConnectedSonarLintEngine engine, SonarQubeServer server, @Nullable String projectKey, boolean onlyModules) {
    super(null, "Updating SonarQube server '" + server.getName() + "'", true);
    this.engine = engine;
    this.server = server;
    this.projectKey = projectKey;
    this.onlyModules = onlyModules;
  }

  public ServerUpdateTask(ConnectedSonarLintEngine engine, SonarQubeServer server) {
    this(engine, server, null, false);
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    SonarApplication sonarlint = ApplicationManager.getApplication().getComponent(SonarApplication.class);
    indicator.setIndeterminate(true);
    indicator.setText("Fetching data...");

    try {
      ServerConfiguration.Builder serverConfigBuilder = ServerConfiguration.builder()
        .userAgent("SonarLint IntelliJ " + sonarlint.getVersion())
        .connectTimeoutMilliseconds(5000)
        .readTimeoutMilliseconds(5000)
        .url(server.getHostUrl())
        .credentials(server.getLogin(), server.getPassword());
      configureProxy(serverConfigBuilder);
      ServerConfiguration serverConfig = serverConfigBuilder.build();
      if (!onlyModules) {
        engine.update(serverConfig);
      }

      if (projectKey != null) {
        engine.updateModule(serverConfig, projectKey);
      }
      return;
    } catch (final Exception e) {
      LOGGER.error("Error updating server " + server.getName(), e);
      final String msg = (e.getMessage() != null) ? e.getMessage() :
        (e.getClass().getSimpleName() + " - please check the logs to see the stack trace");
      ApplicationManager.getApplication().invokeAndWait(new RunnableAdapter() {
        @Override public void doRun() throws Exception {
          Messages.showErrorDialog((Project) null, msg, "Update failed");
        }
      }, ModalityState.any());
    }
  }

  private void configureProxy(ServerConfiguration.Builder builder) {
    if (server.enableProxy()) {
      HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
      if (httpConfigurable.isHttpProxyEnabledForUrl(server.getHostUrl())) {
        Proxy.Type type = httpConfigurable.PROXY_TYPE_IS_SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP;

        Proxy proxy = new Proxy(type, new InetSocketAddress(httpConfigurable.PROXY_HOST, httpConfigurable.PROXY_PORT));
        builder.proxy(proxy);

        if (httpConfigurable.PROXY_AUTHENTICATION) {
          builder.proxyCredentials(httpConfigurable.PROXY_LOGIN, httpConfigurable.getPlainProxyPassword());
        }
      }
    }
  }
}
