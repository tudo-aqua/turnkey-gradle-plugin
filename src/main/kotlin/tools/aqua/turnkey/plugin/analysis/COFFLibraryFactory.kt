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
import tools.aqua.turnkey.plugin.tools.ReadObj
import tools.aqua.turnkey.plugin.util.mapToSet

/** Analysis tool for COFF libraries. */
abstract class COFFLibraryFactory :
    NativeLibraryFactory<String, AnalyzableCOFFLibrary, SpeculativeCOFFLibrary> {

  /** The `readobj` tool. */
  @get:ServiceReference protected abstract val readObj: Property<ReadObj>

  override operator fun invoke(path: Path): AnalyzableCOFFLibrary =
      AnalyzableCOFFLibrary(path, readObj.get())
}

/** A speculative COFF library (the common format on Windows). */
class SpeculativeCOFFLibrary(override val linkageName: String) : SpeculativeNamedLibrary<String> {
  override val fuzzyName: String
    get() = linkageName.lowercase()

  override fun toString(): String = "SpeculativeCOFFLibrary(linkageName='$linkageName')"
}

/**
 * A wrapper for an on-disk COFF library (the common format on Windows). This only provides
 * introspection capabilities and no way to edit any metadata.
 */
class AnalyzableCOFFLibrary(override val path: Path, private val readObj: ReadObj) :
    AnalyzableNativeLibrary<String, SpeculativeCOFFLibrary> {
  override val linkageName: String = path.name

  override val fuzzyName: String = linkageName.lowercase()

  override val fuzzyMismatch: Int = 0

  /** The imports defined by the library, in load order. */
  val imports: List<String>
    get() = readObj.getCOFFImports(path)

  override val libraryDependencies: Set<SpeculativeCOFFLibrary>
    get() = imports.mapToSet(::SpeculativeCOFFLibrary)

  override fun toString(): String =
      "AnalyzableCOFFLibrary(path=$path, linkageName='$linkageName', fuzzyName='$fuzzyName')"
}
