package org.sonarlint.intellij;

import com.intellij.ide.AppLifecycleListener;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.telemetry.SonarLintTelemetryImpl;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.SonarLintActions;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintInitializer implements AppLifecycleListener {

  @Override
  public void appFrameCreated(@NotNull List<String> commandLineArgs) {
    SonarLintUtils.getService(SonarApplication.class).init();
    GlobalLogOutput.get().init();
    SonarLintUtils.getService(SonarLintTelemetry.class).init();
  }

  @Override
  public void appClosing() {
    SonarLintUtils.getService(SonarLintTelemetry.class).dispose();
  }
  
}
