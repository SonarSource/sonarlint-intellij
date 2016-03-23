package org.sonarlint.intellij.core;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable;

public class SonarLintProjectNotifications extends AbstractProjectComponent {
  private volatile boolean shown = false;

  protected SonarLintProjectNotifications(Project project) {
    super(project);
  }

  public static SonarLintProjectNotifications get(Project project) {
    return project.getComponent(SonarLintProjectNotifications.class);
  }

  public void reset() {
    shown = false;
  }

  public void notifyServerIdInvalid() {
    if(shown) {
      return;
    }
    Notification notification = new Notification("SonarLint - Invalid SonarQube Server ID",
      "SonarLint - Project bound to invalid SonarQube server",
      "Please check the <a href='#'>SonarLint project configuration</a>",
      NotificationType.ERROR, new OpenProjectSettingsNotificationListener(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

  public void errorBalloon(MouseEvent event) {
    final JComponent label = HintUtil.createErrorLabel("Some error text");
    label.setBorder(IdeBorderFactory.createEmptyBorder(2, 7, 2, 7));
    JBPopupFactory.getInstance().createBalloonBuilder(label)
      .setFadeoutTime(3000)
      .setFillColor(HintUtil.ERROR_COLOR)
      .createBalloon()
      .show(new RelativePoint(event), Balloon.Position.above);
  }

  public void notifyServerNotUpdated() {
    if(shown) {
      return;
    }
    Notification notification = new Notification("SonarLint - Update SonarQube Server",
      "SonarLint - No data for SonarQube server",
      "Please update the SonarQube server in the <a href='#'>SonarLint project configuration</a>",
      NotificationType.ERROR, new OpenProjectSettingsNotificationListener(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

  private static class OpenProjectSettingsNotificationListener extends NotificationListener.Adapter {
    private final Project project;

    public OpenProjectSettingsNotificationListener(Project project) {
      this.project = project;
    }
    @Override protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      if(project != null && !project.isDisposed()) {
        SonarLintProjectConfigurable configurable = new SonarLintProjectConfigurable(project);
        ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
      } else {
        notification.expire();
      }
    }
  }
}
