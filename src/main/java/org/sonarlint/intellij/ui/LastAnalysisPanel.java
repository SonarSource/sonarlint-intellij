package org.sonarlint.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.time.LocalDateTime;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.util.ResourceLoader;
import org.sonarlint.intellij.util.SonarLintUtils;

public class LastAnalysisPanel implements Disposable {
  private static final String NO_ANALYSIS_LABEL = "Trigger the analysis to find issues on the files in the change set";
  private final ChangedFilesIssues changedFileIssues;
  private GridBagConstraints gc;
  private Timer lastAnalysisTimeUpdater;
  private JLabel lastAnalysisLabel;
  private JLabel icon;
  private JPanel panel;

  public LastAnalysisPanel(ChangedFilesIssues changedFileIssues, Project project) {
    this.changedFileIssues = changedFileIssues;
    createComponents();
    setLastAnalysisTime();
    setTimer();
    Disposer.register(project, this);
  }

  public JPanel getPanel() {
    return panel;
  }

  public void update() {
    setLastAnalysisTime();
  }

  private void setLastAnalysisTime() {
    LocalDateTime lastAnalysis = changedFileIssues.lastAnalysisDate();
    panel.removeAll();
    if (lastAnalysis == null) {
      lastAnalysisLabel.setText(NO_ANALYSIS_LABEL);
      panel.add(icon);
      panel.add(lastAnalysisLabel, gc);
    } else {
      lastAnalysisLabel.setText("Analysis done " + SonarLintUtils.age(System.currentTimeMillis()));
      panel.add(lastAnalysisLabel, gc);
    }

    panel.add(Box.createHorizontalBox(), gc);
  }

  private void createComponents() {
    panel = new JPanel(new GridBagLayout());
    try {
      icon = new JLabel(ResourceLoader.getIcon("info.png"));
    } catch (IOException e) {
      // ignore
    }
    lastAnalysisLabel = new JLabel("");
    gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 2, 2, 2), 0, 0);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
  }

  @Override
  public void dispose() {
    if (lastAnalysisTimeUpdater != null) {
      lastAnalysisTimeUpdater.stop();
      lastAnalysisTimeUpdater = null;
    }
  }

  private void setTimer() {
    lastAnalysisTimeUpdater = new Timer(5000, e -> setLastAnalysisTime());
  }
}
