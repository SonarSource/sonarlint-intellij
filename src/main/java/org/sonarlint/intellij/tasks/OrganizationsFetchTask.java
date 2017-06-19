package org.sonarlint.intellij.tasks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;

public class OrganizationsFetchTask  extends Task.Modal {
  private static final Logger LOGGER = Logger.getInstance(ConnectionTestTask.class);
  private final SonarQubeServer server;
  private Exception exception;
  private List<RemoteOrganization> result;

  public OrganizationsFetchTask(SonarQubeServer server) {
    super(null, "Fetch Organizations From SonarQube Server", true);
    this.server = server;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Connecting to " + server.getHostUrl() + "...");
    indicator.setIndeterminate(true);

    try {
      ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
      WsHelper wsHelper = new WsHelperImpl();
      result = wsHelper.listOrganizations(serverConfiguration, new TaskProgressMonitor(indicator));
    } catch(UnsupportedServerException e) {
      result = Collections.emptyList();
    } catch (Exception e) {
      LOGGER.info("Failed to fetch organizations", e);
      exception = e;
    }
  }

  public Exception getException() {
    return exception;
  }

  public List<RemoteOrganization> result() {
    return result;
  }

}
