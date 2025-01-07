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

package tools.aqua.turnkey.plugin.util

import java.io.File.pathSeparatorChar
import java.io.IOException
import java.lang.System.getenv
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

internal val smartPathLookup: Map<String, Path> by lazy {
  computeSmartPathLookup(searchPath.map { listBinariesOn(it) }, pathExtensions)
}
internal val pathExtensions: Set<String> by lazy {
  getenv("PATHEXT")?.split(pathSeparatorChar)?.toSet() ?: emptySet()
}
internal val searchPath: List<Path> by lazy {
  getenv("PATH")?.split(pathSeparatorChar)?.map(::Path) ?: emptyList()
}

internal fun listBinariesOn(path: Path): List<Path> =
    try {
      path.listDirectoryEntries().filter(Path::isExecutable)
    } catch (_: IOException) {
      emptyList()
    }

internal fun computeSmartPathLookup(
    path: Iterable<Iterable<Path>>,
    pathExtensions: Iterable<String>
): Map<String, Path> {
  val fromPath = path.map { it.toLookup() }.mergeWithDescendingPrecedence()
  val withImplicitFNEs = fromPath + fromPath.toImplicitFNEAliases(pathExtensions)
  val withVersions = withImplicitFNEs + withImplicitFNEs.toVersionAliases()
  val withLLVM = withVersions + withVersions.toLLVMAliases()
  val withLLVMVersions = withLLVM + withLLVM.toVersionAliases()
  return withLLVMVersions
}

/** Create a lookup from the path under their real name, giving precedence to earlier entries. */
internal fun Iterable<Path>.toLookup(): Map<String, Path> = associate { path -> path.name to path }

/** Generate new entries resulting from removing implicit file name extensions. */
internal fun Map<String, Path>.toImplicitFNEAliases(
    pathExtensions: Iterable<String>
): Map<String, Path> =
    asSequence()
        .flatMap { (name, path) ->
          pathExtensions.asSequence().mapNotNull { extension ->
            val maybeShortened = name.removeSuffix(extension)
            if (maybeShortened != name && maybeShortened !in this) (maybeShortened to path)
            else null
          }
        }
        .toMap()

/**
 * Generate new entries resulting from removing a `llvm-` prefix, giving precedence to existing
 * entries.
 */
internal fun Map<String, Path>.toLLVMAliases(): Map<String, Path> =
    asSequence()
        .mapNotNull { (name, path) ->
          val maybeShortened = name.removePrefix("llvm-")
          if (maybeShortened != name && maybeShortened !in this) (maybeShortened to path) else null
        }
        .toMap()

private val versionedRegex = """(.*)-([0-9]+)""".toRegex()

/**
 * Generate new entries resulting from removing a `-version` suffix, giving precedence to higher
 * versions.
 */
internal fun Map<String, Path>.toVersionAliases(): Map<String, Path> {
  data class VersionedBinary(val name: String, val version: Int, val path: Path)
  return entries
      .asSequence()
      .mapNotNull { (name, path) ->
        versionedRegex
            .matchEntire(name)
            ?.destructured
            ?.let { (shortened, version) -> VersionedBinary(shortened, version.toInt(), path) }
            ?.let { if (it.name !in this) it else null }
      }
      .groupBy(VersionedBinary::name)
      .values
      .map { versions -> versions.maxBy(VersionedBinary::version) }
      .asSequence()
      .map { (name, _, path) -> name to path }
      .toMap()
}
