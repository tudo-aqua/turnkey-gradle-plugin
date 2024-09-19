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

package tools.aqua.turnkey.plugin.analysis

import java.nio.file.Path
import kotlin.io.path.name
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import tools.aqua.turnkey.plugin.tools.PatchELF
import tools.aqua.turnkey.plugin.util.PlatformPath
import tools.aqua.turnkey.plugin.util.VersionedELFLibrary
import tools.aqua.turnkey.plugin.util.levenshteinDistanceTo
import tools.aqua.turnkey.plugin.util.mapToSet

/** Analysis tool for ELF libraries. */
abstract class ELFLibraryFactory :
    NativeLibraryFactory<String, AnalyzableELFLibrary, SpeculativeELFLibrary> {

  /** The `patchelf` tool. */
  @get:ServiceReference protected abstract val patchELF: Property<PatchELF>

  override operator fun invoke(path: Path): AnalyzableELFLibrary =
      AnalyzableELFLibrary(path, patchELF.get())
}

/** A speculative ELF library (the common format on Linux). */
class SpeculativeELFLibrary(override val linkageName: String) : SpeculativeNamedLibrary<String> {
  override val fuzzyName: String = VersionedELFLibrary.parse(linkageName).baseName

  override fun toString(): String =
      "SpeculativeELFLibrary(linkageName='$linkageName', fuzzyName='$fuzzyName')"
}

/**
 * A wrapper for an ELF library (the common format on Linux). This provides full edit capabilities
 * for the SONAME (linkage name), RPATH (linkage path) and needed libraries.
 */
class AnalyzableELFLibrary(override val path: Path, private val patchELF: PatchELF) :
    AnalyzableNativeLibrary<String, SpeculativeELFLibrary> {
  override val linkageName: String
    get() = path.name

  override val fuzzyName: String
    get() = VersionedELFLibrary.parse(linkageName).baseName

  override val fuzzyMismatch: Int
    get() = linkageName levenshteinDistanceTo fuzzyName

  /** The SONAME (linkage name of the library). */
  var soName: String
    get() = patchELF.getSOName(path)
    set(value) = patchELF.setSOName(path, value)

  /**
   * The RPATH, i.e., additional paths that should be considered by the linker to find library
   * needed libraries. The magic value `$ORIGIN` refers to the location of the library itself.
   */
  var rPath: List<PlatformPath>
    get() = patchELF.getRPath(path)
    set(value) {
      if (value.isEmpty()) patchELF.clearRPath(path) else patchELF.setRPath(path, value)
    }

  /** The needed libraries, i.e., the libraries dependencies. These are specified as names only. */
  var needed: List<String>
    get() = patchELF.getNeeded(path)
    set(value) {
      val current = needed
      if (current.isNotEmpty()) patchELF.removeNeeded(path, current)
      if (value.isNotEmpty()) patchELF.addNeeded(path, value)
    }

  override val libraryDependencies: Set<SpeculativeELFLibrary>
    get() = needed.mapToSet(::SpeculativeELFLibrary)

  override fun toString(): String =
      "AnalyzableELFLibrary(path=$path, linkageName='$linkageName', fuzzyName='$fuzzyName')"
}
