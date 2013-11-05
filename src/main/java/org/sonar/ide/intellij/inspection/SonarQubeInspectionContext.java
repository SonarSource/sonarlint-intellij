package org.sonar.ide.intellij.inspection;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.config.SonarQubeSettings;
import org.sonar.ide.intellij.model.ISonarIssue;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.wsclient.ISonarWSClientFacade;
import org.sonar.ide.intellij.wsclient.WSClientFactory;

import java.util.List;

public class SonarQubeInspectionContext implements GlobalInspectionContextExtension<SonarQubeInspectionContext> {

  List<ISonarIssue> issues;

  public static final Key<SonarQubeInspectionContext> KEY = Key.create("SonarQubeInspectionContext");

  @Override
  public Key<SonarQubeInspectionContext> getID() {
    return KEY;
  }

  public List<ISonarIssue> getIssues() {
    return issues;
  }

  @Override
  public void performPreRunActivities(List<Tools> globalTools, List<Tools> localTools, GlobalInspectionContext context) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    Project p = context.getProject();
    if (p != null) {
      ProjectSettings projectSettings = p.getComponent(ProjectSettings.class);
      String serverId = projectSettings.getServerId();
      if (serverId == null) {
        System.out.println("Project is not associated to SonarQube");
        return;
      }
      SonarQubeSettings settings = SonarQubeSettings.getInstance();
      SonarQubeServer server = settings.getServer(serverId);
      if (server == null) {
        System.out.println("Project was associated to a server that is not configured: " + serverId);
        return;
      }
      ISonarWSClientFacade sonarClient = WSClientFactory.getInstance().getSonarClient(server);
      issues = sonarClient.getRemoteIssuesRecursively(projectSettings.getModuleKey());
    }


  }

  @Override
  public void performPostRunActivities(List<InspectionProfileEntry> inspections, GlobalInspectionContext context) {
  }

  @Override
  public void cleanup() {
  }
}
