package org.sonarlint.intellij.util;

import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import javax.swing.CellRendererPane;
import javax.swing.ComboBoxEditor;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.event.ListDataEvent;

public class ComboBoxWidePopup<T> extends JComboBox<T> {
  private boolean myLayingOut = false;

  public ComboBoxWidePopup() {
    if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) {
      setMaximumRowCount(25);
    }
  }

  public void doLayout() {
    try {
      myLayingOut = true;
      super.doLayout();
    }
    finally {
      myLayingOut = false;
    }
  }

  /**
   * The goal is to offer the ability to provide to the ComboBox PopUp a wider dimension than the actual size of the ComboBox.
   */
  public Dimension getSize() {
    Dimension size = super.getSize();
    if (!myLayingOut) {
      // being called by the popup!
      size.width = Math.max(size.width, super.getPreferredSize().width);
    }
    return size;
  }

  private void recalculatePreferredSize() {
    ComboBoxModel model = getModel();
    int modelSize = model.getSize();
    int baseline = -1;
    Dimension d;



  }


  public void contentsChanged(ListDataEvent e) {
    super.contentsChanged(e);
    recalculatePreferredSize();
  }

  public void intervalAdded(ListDataEvent e) {
    super.intervalAdded(e);
    recalculatePreferredSize();
  }

  public void intervalRemoved(ListDataEvent e) {
    super.intervalRemoved(e);
    recalculatePreferredSize();
  }
}
