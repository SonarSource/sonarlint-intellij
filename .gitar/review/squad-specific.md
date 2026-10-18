# IntelliJ review focus

Apply these checks only when the diff touches the relevant behavior.
Leave an advisory finding only for a concrete failure supported by the changed
code and its callers; name the failing scenario and source location.
Update an existing thread for the same concern, preserve unresolved evidence,
and remain quiet when the checks pass. Do not approve, block, or modify the PR.

## Threading and PSI

- For UI changes, trace the caller to the EDT before flagging unsafe widget or
  model access. For expensive analysis, I/O, or backend waits, establish whether
  execution can block the EDT; do not infer a violation from a method name alone.
- For PSI and project-model access, follow the read-action boundary and object
  lifetime. Check the existing helpers before recommending another wrapper.
- Relevant current entry points include
  `src/main/java/org/sonarlint/intellij/SonarLintIntelliJClient.kt` and
  `src/main/java/org/sonarlint/intellij/core/BackendService.kt`.

## Cancellation and disposal

- When work crosses asynchronous boundaries, check cancellation propagation,
  including platform cancellation exceptions, and whether callbacks can run
  after project, module, or UI disposal. Show a reachable stale callback or lost
  cancellation before raising a concern.
- For changed analysis scheduling, inspect
  `src/main/java/org/sonarlint/intellij/analysis/AnalysisSubmitter.kt`;
  for changed current-file UI behavior, inspect
  `src/main/java/org/sonarlint/intellij/ui/currentfile/CurrentFilePanel.kt`.
- Respect the surrounding platform conventions and supported IDE versions.
  Avoid general threading rewrites or speculative lifecycle warnings.
