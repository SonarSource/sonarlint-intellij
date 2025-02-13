package org.sonarlint.intellij.actions.filters

import org.sonarsource.sonarlint.core.SonarCloudRegion

data class Quadruple(val a: Boolean, val b: String, val c: String, val d: SonarCloudRegion?)
