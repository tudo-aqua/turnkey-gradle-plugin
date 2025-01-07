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
import tools.aqua.turnkey.plugin.analysis.AnalyzableCOFFLibrary
import tools.aqua.turnkey.plugin.analysis.COFFLibraryFactory
import tools.aqua.turnkey.plugin.analysis.SpeculativeCOFFLibrary

/**
 * TurnKey editing task for COFF (i.e., Windows) library bundles. This is not capable of rewriting
 * library files and can only create a suitable load order. Autoloading can not be supported on
 * Windows.
 */
abstract class COFFTurnKeyTask :
    AbstractTurnKeyTask<String, AnalyzableCOFFLibrary, SpeculativeCOFFLibrary>(
        AnalyzableCOFFLibrary::class, SpeculativeCOFFLibrary::class) {

  /** The provider for the native library abstraction layer. */
  @get:ServiceReference protected abstract val nativeLibraryFactory: Property<COFFLibraryFactory>

  override val analyzer: COFFLibraryFactory
    get() = nativeLibraryFactory.get()

  override val autoloadSupported: Boolean = false

  override fun rewrite(
      library: AnalyzableCOFFLibrary,
      includedLibraries: List<AnalyzableCOFFLibrary>
  ) = Unit
}
