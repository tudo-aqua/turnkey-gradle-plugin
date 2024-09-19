/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2024 The TurnKey Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools.aqua.turnkey.plugin.util

import java.nio.file.Path
import kotlin.io.path.absolutePathString
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Thrown to indicate a failure of the auto-discovery for a tool. This may be caused by the tool not
 * being installed or using an unusual name.
 *
 * @param name the name of the missing tool.
 */
internal class ToolNotFoundException(name: String) :
    GradleException("tool not auto-discoverable: $name")

/**
 * Create a [Provider] for the tool [name] with possible [alternativeNames], using the given lookup
 * [path].
 */
internal fun Project.toolProvider(
    path: Map<String, Path>,
    name: String,
    vararg alternativeNames: String
): Provider<String> = provider {
  findProperty("turnkey.tool.$name")?.let {
    logger.debug { "$name overridden by property: $it" }
    return@provider it.toString()
  }
  path[name]?.let {
    val absolute = it.absolutePathString()
    logger.debug { "$name discovered by primary name: $absolute" }
    return@provider absolute
  }
  alternativeNames.forEach { alternativeName ->
    path[alternativeName]?.let {
      val absolute = it.absolutePathString()
      logger.debug { "$name discovered by alternative name $alternativeName: $absolute" }
      return@provider absolute
    }
  }
  throw ToolNotFoundException(name)
}
