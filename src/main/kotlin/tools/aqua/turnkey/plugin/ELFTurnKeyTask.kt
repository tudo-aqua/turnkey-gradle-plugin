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

package tools.aqua.turnkey.plugin

import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import tools.aqua.turnkey.plugin.analysis.AnalyzableELFLibrary
import tools.aqua.turnkey.plugin.analysis.ELFLibraryFactory
import tools.aqua.turnkey.plugin.analysis.SpeculativeELFLibrary
import tools.aqua.turnkey.plugin.util.PlatformPath.Companion.linuxOriginRelative
import tools.aqua.turnkey.plugin.util.mapToSet

/**
 * TurnKey editing task for ELF (i.e., Linux) library bundles. This rewrites libraries to use RPath
 * linkage for bundled libraries and sets the RPath relative to the file. Autoloading is supported.
 */
abstract class ELFTurnKeyTask :
    AbstractTurnKeyTask<String, AnalyzableELFLibrary, SpeculativeELFLibrary>(
        AnalyzableELFLibrary::class, SpeculativeELFLibrary::class) {

  /** The provider for the native library abstraction layer. */
  @get:ServiceReference protected abstract val nativeLibraryFactory: Property<ELFLibraryFactory>

  override val analyzer: ELFLibraryFactory
    get() = nativeLibraryFactory.get()

  override val autoloadSupported: Boolean = true

  override fun rewrite(
      library: AnalyzableELFLibrary,
      includedLibraries: List<AnalyzableELFLibrary>
  ) {
    library.rPath = listOf(linuxOriginRelative())
    library.soName = library.fuzzyName

    val included = includedLibraries.mapToSet { it.fuzzyName }
    library.needed =
        library.libraryDependencies.map {
          if (it.fuzzyName in included) it.fuzzyName else it.linkageName
        }
  }
}
