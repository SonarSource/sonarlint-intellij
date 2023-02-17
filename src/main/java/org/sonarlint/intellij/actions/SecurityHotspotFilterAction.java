package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import java.util.ArrayList;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilterSettings;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class SecurityHotspotFilterAction extends ActionGroup {

  private final AnAction[] myChildren;

  public SecurityHotspotFilterAction(String title, String description, @Nullable Icon icon) {
    super(title, description, icon);
    setPopup(true);

    final ArrayList<AnAction> kids = new ArrayList<>(3);
    var settings = new SecurityHotspotFilterSettings();
    kids.add(new SetSecurityHotspotFilterAction(SecurityHotspotFilters.SHOW_ALL, settings));
    kids.add(new SetSecurityHotspotFilterAction(SecurityHotspotFilters.LOCAL_ONLY, settings));
    kids.add(new SetSecurityHotspotFilterAction(SecurityHotspotFilters.MATCHED_ON_SONARQUBE, settings));
    myChildren = kids.toArray(AnAction.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myChildren;
  }

  private static class SetSecurityHotspotFilterAction extends ToggleAction {

    private final SecurityHotspotFilters filter;
    private final SecurityHotspotFilterSettings settings;

    SetSecurityHotspotFilterAction(SecurityHotspotFilters filter, SecurityHotspotFilterSettings settings)  {
      super(filter.getTitle());
      this.filter = filter;
      this.settings = settings;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return settings.getCurrentlySelectedFilter() == filter;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean enabled) {
      var project = e.getProject();
      if (project == null) {
        return;
      }

      if (enabled && settings.getCurrentlySelectedFilter() != filter) {
        getService(project, SonarLintToolWindow.class).filterSecurityHotspotTab(filter);
        settings.setCurrentlySelectedFilter(filter);
      }
    }

  }

}
