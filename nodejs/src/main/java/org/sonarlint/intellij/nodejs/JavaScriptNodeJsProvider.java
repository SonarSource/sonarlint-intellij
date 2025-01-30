package org.sonarlint.intellij.nodejs;

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.common.util.NodeJsProvider;

public class JavaScriptNodeJsProvider implements NodeJsProvider {

  @Nullable
  @Override
  public Path getNodeJsPathFor(Project project) {
    var interpreter = NodeJsInterpreterManager.getInstance(project).getInterpreter();
    return interpreter != null ? Paths.get(interpreter.getReferenceName()) : null;
  }

}
