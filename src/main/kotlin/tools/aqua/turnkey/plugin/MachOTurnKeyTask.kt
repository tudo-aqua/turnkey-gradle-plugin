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

import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import tools.aqua.turnkey.plugin.analysis.AnalyzableMachOLibrary
import tools.aqua.turnkey.plugin.analysis.MachOLibraryFactory
import tools.aqua.turnkey.plugin.analysis.SpeculativeMachOLibrary
import tools.aqua.turnkey.plugin.util.PlatformPath
import tools.aqua.turnkey.plugin.util.PlatformPath.Companion.macOSLoaderRelative
import tools.aqua.turnkey.plugin.util.mapToSet

/**
 * TurnKey editing task for MachO (i.e., macOS) library bundles. This rewrites libraries to use
 * file-relative linkage for bundled libraries. Autoloading is supported.
 */
abstract class MachOTurnKeyTask :
    AbstractTurnKeyTask<PlatformPath, AnalyzableMachOLibrary, SpeculativeMachOLibrary>(
        AnalyzableMachOLibrary::class, SpeculativeMachOLibrary::class) {

  /** The provider for the native library abstraction layer. */
  @get:ServiceReference protected abstract val nativeLibraryFactory: Property<MachOLibraryFactory>

  override val analyzer: MachOLibraryFactory
    get() = nativeLibraryFactory.get()

  override val autoloadSupported: Boolean = true

  override fun rewrite(
      library: AnalyzableMachOLibrary,
      includedLibraries: List<AnalyzableMachOLibrary>
  ) {
    library.rPath = emptyList()
    library.id = macOSLoaderRelative(library.fuzzyName)

    val included = includedLibraries.mapToSet { it.fuzzyName }
    library.dependencies =
        library.libraryDependencies.map {
          if (it.fuzzyName in included) macOSLoaderRelative(it.fuzzyName) else it.linkageName
        }
  }
}
