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

package org.sonarlint.intellij.config.project;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import icons.SonarLintIcons;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NonNls;

import static org.sonarlint.intellij.config.project.ExclusionItem.Type.DIRECTORY;
import static org.sonarlint.intellij.config.project.ExclusionItem.Type.FILE;
import static org.sonarlint.intellij.config.project.ExclusionItem.Type.GLOB;

/**
 * Based on IgnoreUnversionedDialog
 */
public class AddEditExclusionDialog extends DialogWrapper {
  private final Project project;
  private JPanel panel;

  private JRadioButton fileRadioButton;
  private JRadioButton directoryRadioButton;
  private JRadioButton globRadioButton;

  private TextFieldWithBrowseButton directoryTextField;
  private JBTextField globTextField;
  private TextFieldWithBrowseButton fileTextField;
  private HyperlinkLabel patternHelp;
  private JBLabel helpLabel;

  public AddEditExclusionDialog(Project project) {
    super(project, false);
    this.project = project;
    setTitle("Add SonarLint File Exclusion");
    init();

    FileChooserDescriptor fileChooser = new FileChooserDescriptor(true, false, false,
      true, false, false);
    fileChooser.setRoots(project.getBaseDir());
    fileTextField.addBrowseFolderListener("Select File to Exclude",
      "Select the file which will be excluded from SonarLint analysis",
      project, fileChooser);

    FileChooserDescriptor directoryChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    directoryChooser.setRoots(project.getBaseDir());
    directoryTextField.addBrowseFolderListener("Select Directory to Exclude",
      "Select the directory which will be excluded from SonarLint analysis",
      project, directoryChooser);

    DocumentListener docListener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateOk();
      }
    };

    fileTextField.getTextField().getDocument().addDocumentListener(docListener);
    directoryTextField.getTextField().getDocument().addDocumentListener(docListener);
    globTextField.getDocument().addDocumentListener(docListener);

    ActionListener listener = e -> updateControls();
    directoryRadioButton.addActionListener(listener);
    globRadioButton.addActionListener(listener);
    fileRadioButton.addActionListener(listener);
    updateControls();
  }

  private void updateControls() {
    directoryTextField.setEnabled(directoryRadioButton.isSelected());
    globTextField.setEnabled(globRadioButton.isSelected());
    fileTextField.setEnabled(fileRadioButton.isSelected());
    helpLabel.setEnabled(globRadioButton.isSelected());

    updateOk();
  }

  private void updateOk() {
    boolean valid = StringUtils.trimToNull(getSelectedText().getText()) != null;
    myOKAction.setEnabled(valid);
  }

  private JTextField getSelectedText() {
    if (directoryRadioButton.isSelected()) {
      return directoryTextField.getTextField();
    } else if (fileRadioButton.isSelected()) {
      return fileTextField.getTextField();
    } else {
      return globTextField;
    }
  }

  @CheckForNull
  protected JComponent createCenterPanel() {
    return panel;
  }

  @CheckForNull
  public ExclusionItem getExclusion() {
    if (directoryRadioButton.isSelected()) {
      return new ExclusionItem(DIRECTORY, relative(directoryTextField.getText()));
    } else if (fileRadioButton.isSelected()) {
      return new ExclusionItem(FILE, relative(fileTextField.getText()));
    } else {
      return new ExclusionItem(GLOB, relative(globTextField.getText()));
    }
  }

  public void setExclusion(@Nullable ExclusionItem item) {
    setTitle("Edit SonarLint File Exclusion");

    if (item != null) {
      switch (item.type()) {
        case DIRECTORY:
          directoryTextField.setText(item.item());
          directoryRadioButton.setSelected(true);
          break;
        case FILE:
          fileTextField.setText(item.item());
          fileRadioButton.setSelected(true);
          break;
        case GLOB:
          globTextField.setText(item.item());
          globRadioButton.setSelected(true);
          break;
      }
      updateControls();
    }
  }

  private void createUIComponents() {
    patternHelp = new HyperlinkLabel("");
    Icon infoIcon = SonarLintIcons.INFO;
    patternHelp.setIcon(infoIcon);
    patternHelp.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        final JLabel label = new JLabel("<<html><body style=\"margin-bottom:1pt\">"
          + "Configure GLOB patterns to be applied to files in all projects.<br/><br/><b>Examples:</b><br/>"
          + "<table border=\"1\" cellpadding=\"1\" cellspacing=\"0\"><tr><td><pre>**/*.js</pre></td>"
          + "<td>Exclude all javascript files</td></tr><tr><td><pre>src/main/test/**</pre></td>"
          + "<td>Exclude all test sources</td></tr><tr><td><pre>**/*{!.java}</pre></td>"
          + "<td>Exclude everything except Java files</td></tr></table></body></html>");
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.INFORMATION_COLOR);
        label.setOpaque(true);
        HintManager.getInstance().showHint(label, RelativePoint.getSouthWestOf(patternHelp),
          HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        HintManager.getInstance().hideAllHints();
      }
    });
  }

  private String relative(String path) {
    if (project == null) {
      return path;
    }
    final File file = new File(path);
    String relativePath = path;
    if (file.isAbsolute()) {
      relativePath = ChangesUtil.getProjectRelativePath(project, file);
      if (relativePath == null) {
        relativePath = path;
      }
    }
    return FileUtil.toSystemIndependentName(relativePath);
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "AddEditExclusionDialog";
  }
}
