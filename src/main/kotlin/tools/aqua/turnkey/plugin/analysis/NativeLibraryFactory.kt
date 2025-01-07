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
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Provides analysis capabilities for libraries native libraries of type [L] (analyzable) and [S]
 * (speculative) with linkage names of type [T]. This will usually return an active object
 * containing references to the analysis tools used.
 */
interface NativeLibraryFactory<
    T, L : AnalyzableNativeLibrary<T, S>, S : SpeculativeNamedLibrary<T>> :
    BuildService<BuildServiceParameters.None> {
  /** Create a tool-aware active wrapper object for the library at path [path]. */
  operator fun invoke(path: Path): L
}

/**
 * A native library that may or may not be actually available.
 *
 * @param T the type of name used by the library.
 */
sealed interface NativeLibrary<out T> {
  /**
   * The name of the library as "seen" by the linker. This may be a string or a path, for example.
   */
  val linkageName: T
  /**
   * The fuzzy name of the library. This should permit recognition of libraries even if they are not
   * present in, e.g., the correct installation path.
   */
  val fuzzyName: String
}

/**
 * A native library this is speculative, i.e., derived from a dependency.
 *
 * @param T the type of name used by the library.
 */
interface SpeculativeNamedLibrary<out T> : NativeLibrary<T>

/**
 * A native library that is actually present in the file system and can be analyzed for
 * dependencies. Subclasses will offer methods and properties for editing type-specific metadata.
 *
 * @param T the type of name used by the library.
 * @param S the companion speculative library type.
 */
interface AnalyzableNativeLibrary<out T, out S : SpeculativeNamedLibrary<T>> : NativeLibrary<T> {
  /**
   * A quantification of the "fuzziness" of the library name, i.e. how far the fuzzy name diverges
   * from the actual name. Higher is more divergent.
   */
  val fuzzyMismatch: Int
  /** The location of the library in the file system. */
  val path: Path
  /** The dependencies this library is linked to. */
  val libraryDependencies: Set<S>
}
