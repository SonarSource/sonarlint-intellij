/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.monitoring

import com.intellij.openapi.application.ApplicationInfo
import io.sentry.Sentry
import io.sentry.SentryOptions
import org.apache.commons.lang3.SystemUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.telemetry.SonarLintTelemetry

object MonitoringService {

    private const val CLIENT_DSN = "https://68144d3abca48b0ea9e3ca1a6a24bf74@o1316750.ingest.us.sentry.io/4510509893353472"

    private var active = false

    fun isDogfoodingEnvironment(): Boolean {
        return "1" == System.getenv("SONARSOURCE_DOGFOODING")
    }

    @JvmStatic
    fun init() {
        if (!active) {
            active = true
            getService(SonarLintTelemetry::class.java).enabled().thenAccept { isEnabled ->
                if (isEnabled) {
                    initializeSentry()
                } else {
                    active = false
                }
            }
        }
    }

    @JvmStatic
    fun close() {
        if (active) {
            active = false
            Sentry.close()
        }
    }

    @JvmStatic
    fun reinit() {
        if (active) {
            return
        }
        active = true
        initializeSentry()
    }

    private fun initializeSentry() {
        Sentry.init { options: SentryOptions ->
            options.dsn = CLIENT_DSN
            options.release = getService(org.sonarlint.intellij.SonarLintPlugin::class.java).version
            if (isDogfoodingEnvironment()) {
                options.environment = "dogfood"
            } else {
                options.environment = "production"
            }
            options.setTag("ideVersion", ApplicationInfo.getInstance().fullVersion)
            options.setTag("platform", System.getProperty(SystemUtils.OS_NAME))
            options.setTag("architecture", System.getProperty(SystemUtils.OS_ARCH))
            options.addInAppInclude("org.sonarlint.intellij")
        }
    }
}
