package org.sonarlint.intellij.analysis;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class GitThreadFactory implements ThreadFactory {
  private static final String NAME_PREFIX = "git-scm-";
  private final AtomicInteger count = new AtomicInteger(0);

  @Override
  public Thread newThread(Runnable r) {
    Thread t = new Thread(r);
    t.setName(NAME_PREFIX + count.getAndIncrement());
    t.setDaemon(true);
    return t;
  }
}
