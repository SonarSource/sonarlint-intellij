/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sonarlint.intellij.config.project;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.ui.DocumentAdapter;
import javax.annotation.CheckForNull;
import javax.swing.event.DocumentListener;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;
import java.io.File;

import static org.sonarlint.intellij.config.project.ExclusionItem.Type.DIRECTORY;
import static org.sonarlint.intellij.config.project.ExclusionItem.Type.FILE;
import static org.sonarlint.intellij.config.project.ExclusionItem.Type.GLOB;

/**
 * Based on IgnoreUnversionedDialog
 */
public class AddEditExclusionDialog extends DialogWrapper {
  @Nullable
  private final Project project;
  private JPanel panel;

  private JRadioButton fileRadioButton;
  private JRadioButton directoryRadioButton;
  private JRadioButton globRadioButton;

  private TextFieldWithBrowseButton directoryTextField;
  private JTextField globTextField;
  private TextFieldWithBrowseButton fileTextField;

  public AddEditExclusionDialog(@Nullable final Project project) {
    super(project, false);
    this.project = project;
    setTitle("Add SonarLint File Exclusion");
    init();

    FileChooserDescriptor fileChooser = new FileChooserDescriptor(true, false, false, true, false, false);
    if (project != null) {
      fileChooser.setRoots(project.getBaseDir());
    }
    fileTextField.addBrowseFolderListener("Select File to Exclude",
      "Select the file which will be excluded from SonarLint analysis",
      project, fileChooser);

    FileChooserDescriptor directoryChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    if (project != null) {
      directoryChooser.setRoots(project.getBaseDir());
    }
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

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[] {getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("ignoreUnversionedFilesDialog");
  }

  private void updateControls() {
    directoryTextField.setEnabled(directoryRadioButton.isSelected());
    globTextField.setEnabled(globRadioButton.isSelected());
    fileTextField.setEnabled(fileRadioButton.isSelected());
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

  @Nullable
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
