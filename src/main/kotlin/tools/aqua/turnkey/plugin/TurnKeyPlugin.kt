/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2025 The TurnKey Authors
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

package tools.aqua.turnkey.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.registerIfAbsent
import tools.aqua.turnkey.plugin.analysis.COFFLibraryFactory
import tools.aqua.turnkey.plugin.analysis.ELFLibraryFactory
import tools.aqua.turnkey.plugin.analysis.MachOLibraryFactory
import tools.aqua.turnkey.plugin.tools.InstallNameTool
import tools.aqua.turnkey.plugin.tools.OTool
import tools.aqua.turnkey.plugin.tools.PatchELF
import tools.aqua.turnkey.plugin.tools.ReadObj
import tools.aqua.turnkey.plugin.util.smartPathLookup
import tools.aqua.turnkey.plugin.util.toolProvider

/**
 * Configures the external tools used by the TurnKey framework. By default, a detection heuristic is
 * used.
 */
interface TurnKeyExtension {
  /** The `install_name_tool` to use. Must be a binary on the system `$PATH` or a full path. */
  val installNameTool: Property<String>
  /** The `otool` to use. Must be a binary on the system `$PATH` or a full path. */
  val otool: Property<String>
  /** The `patchelf` to use. Must be a binary on the system `$PATH` or a full path. */
  val patchelf: Property<String>
  /** The `readobj` to use. Must be a binary on the system `$PATH` or a full path. */
  val readobj: Property<String>
}

/**
 * Gradle plugin offering TurnKey functionality. When applied, this only performs setup. Users must
 * define TurnKey tasks in their buld scripts manually.
 */
class TurnKeyPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val turnkey = project.extensions.create<TurnKeyExtension>("turnkey")

    val path = smartPathLookup

    turnkey.installNameTool.convention(
        project.toolProvider(path, "install_name_tool", "install-name-tool"))
    turnkey.otool.convention(project.toolProvider(path, "otool"))
    turnkey.patchelf.convention(project.toolProvider(path, "patchelf"))
    turnkey.readobj.convention(project.toolProvider(path, "readobj"))

    project.gradle.sharedServices.apply {
      sequenceOf(COFFLibraryFactory::class, ELFLibraryFactory::class, MachOLibraryFactory::class)
          .forEach { registerIfAbsent(it.simpleName!!, it) }
      sequenceOf(InstallNameTool::class, OTool::class, PatchELF::class, ReadObj::class).forEach {
        // XXX: extension = turnkey ass soon as fixed in Spotless + Ktfmt
        registerIfAbsent(it.simpleName!!, it) { parameters { extension.set(turnkey) } }
      }
    }
  }
}
