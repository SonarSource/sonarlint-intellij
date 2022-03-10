package org.sonarlint.intellij.core

import com.intellij.openapi.module.Module

data class ProjectBinding(val connectionName: String, val projectKey: String, val moduleBindingsOverrides: Map<Module, String>)
