package org.sonarlint.intellij.common.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import javax.annotation.Nullable;

public interface NodeJsProvider {

  // Name is constructed from plugin-id.extension-point-name
  ExtensionPointName<NodeJsProvider> EP_NAME = ExtensionPointName.create("org.sonarlint.idea.nodeJsProvider");

  @Nullable
  Path getNodeJsPathFor(Project project);

}
