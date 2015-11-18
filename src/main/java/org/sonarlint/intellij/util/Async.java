/**
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
package org.sonarlint.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class Async {
    private static final int FIFTY_MS = 50;

    private Async() {
    }

    @NotNull
    public static <T> T asyncResultOf(@NotNull final Callable<T> callable,
                                      @NotNull final T defaultValue) {
        try {
            return whenFinished(executeOnPooledThread(callable)).get();

        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static <T> Future<T> executeOnPooledThread(final Callable<T> callable) {
        return ApplicationManager.getApplication().executeOnPooledThread(callable);
    }

    private static <T> Future<T> whenFinished(final Future<T> future) {
        while (!future.isDone() && !future.isCancelled()) {
            ProgressManager.checkCanceled();
            waitFor(FIFTY_MS);
        }
        return future;
    }

    private static void waitFor(final int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            // ignored
        }
    }
}