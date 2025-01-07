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

package tools.aqua.turnkey.plugin.analysis

import java.nio.file.Path
import kotlin.io.path.name
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import tools.aqua.turnkey.plugin.tools.InstallNameTool
import tools.aqua.turnkey.plugin.tools.OTool
import tools.aqua.turnkey.plugin.util.PlatformPath
import tools.aqua.turnkey.plugin.util.VersionedMachOLibrary
import tools.aqua.turnkey.plugin.util.levenshteinDistanceTo
import tools.aqua.turnkey.plugin.util.mapToSet

/** Analysis tool for Mach-O libraries. */
abstract class MachOLibraryFactory :
    NativeLibraryFactory<PlatformPath, AnalyzableMachOLibrary, SpeculativeMachOLibrary> {

  /** The `install_name_tool` tool. */
  @get:ServiceReference protected abstract val installNameTool: Property<InstallNameTool>

  /** The `otool` tool. */
  @get:ServiceReference protected abstract val oTool: Property<OTool>

  override operator fun invoke(path: Path): AnalyzableMachOLibrary =
      AnalyzableMachOLibrary(path, installNameTool.get(), oTool.get())
}

/** A speculative Mach-O library (the common format on macOS). */
class SpeculativeMachOLibrary(override val linkageName: PlatformPath) :
    SpeculativeNamedLibrary<PlatformPath> {
  override val fuzzyName: String = VersionedMachOLibrary.parse(linkageName.name).baseName

  override fun toString(): String =
      "SpeculativeMachOLibrary(linkageName=$linkageName, fuzzyName='$fuzzyName')"
}

/**
 * A wrapper for a Mach-O library (the common format on macOS). This provides full edit capabilities
 * for the ID (linkage name), RPATH (linkage path) and can change the dependencies via replacement.
 */
class AnalyzableMachOLibrary(
    override val path: Path,
    private val installNameTool: InstallNameTool,
    private val oTool: OTool
) : AnalyzableNativeLibrary<PlatformPath, SpeculativeMachOLibrary> {

  override val linkageName: PlatformPath
    get() = id

  override val fuzzyName: String
    get() = VersionedMachOLibrary.parse(linkageName.name).baseName

  override val fuzzyMismatch: Int
    get() = path.name levenshteinDistanceTo fuzzyName

  /**
   * The ID (linkage name of the library). This is specified as a path, similar to
   * [libraryDependencies].
   */
  var id: PlatformPath
    get() = oTool.getSharedLibraryID(path)
    set(value) = installNameTool.setID(path, value)

  /**
   * The RPATH, i.e., additional paths that should be considered by the linker to find library
   * needed libraries.
   */
  var rPath: List<PlatformPath>
    get() = oTool.getRPath(path)
    set(value) {
      installNameTool.clearRPath(path)
      if (value.isNotEmpty()) installNameTool.addRPath(path, value)
    }

  /**
   * The needed libraries, i.e., the libraries dependencies. These are specified as absolute paths
   * or relative to the magic paths `@executable_path` (the location of the launched binary),
   * `@loader_path` (the location of this library), or `@rpath` (the [rPath]). This does *not*
   * contain the [id], although Mach-O normally includes it here.
   */
  var dependencies: List<PlatformPath>
    // MachO libraries depend on themselves. Remove to avoid loops.
    get() = oTool.getUsedSharedLibraries(path).filter { it != id }
    set(value) {
      val current = dependencies
      require(value.size == current.size)
      val changes = (current zip value).filter { (from, to) -> from != to }
      if (changes.isNotEmpty()) installNameTool.replaceLibrary(path, changes)
    }

  override val libraryDependencies: Set<SpeculativeMachOLibrary>
    get() = dependencies.mapToSet(::SpeculativeMachOLibrary)

  override fun toString(): String =
      "AnalyzableMachOLibrary(path=$path, linkageName=$linkageName, fuzzyName='$fuzzyName')"
}
