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

import com.google.common.jimfs.PathType
import com.google.common.jimfs.PathType.ParseResult
import com.google.common.jimfs.PathType.unix
import com.google.common.jimfs.PathType.windows

/**
 * A platform-dependent path, constructed from JimFS types. This contains the path [type] and the
 * JimFS-internal [parseResult].
 */
data class PlatformPath(val type: PathType, private val parseResult: ParseResult) {
  /** Holder for factory functions. */
  companion object {
    /** Create a UNIX-like path from [path]. */
    @JvmStatic fun unix(path: String): PlatformPath = PlatformPath(unix(), unix().parsePath(path))

    /** Create a UNIX-like absolute path from the components in [names]. */
    @JvmStatic
    fun unixAbsolute(names: Iterable<String>): PlatformPath =
        PlatformPath(unix(), ParseResult("/", names))

    /** Create a UNIX-like absolute path from the components in [names]. */
    @JvmStatic
    fun unixAbsolute(vararg names: String): PlatformPath = unixAbsolute(names.asIterable())

    /** Create a UNIX-like relative path from the components in [names]. */
    @JvmStatic
    fun unixRelative(names: Iterable<String>): PlatformPath =
        PlatformPath(unix(), ParseResult(null, names))

    /** Create a UNIX-like relative path from the components in [names]. */
    @JvmStatic
    fun unixRelative(vararg names: String): PlatformPath = unixRelative(names.asIterable())

    /**
     * Create an origin-relative linkage path for the Linux linker from the components in [names].
     */
    @JvmStatic
    fun linuxOriginRelative(names: Iterable<String>): PlatformPath =
        unixRelative(listOf("\$ORIGIN") + names)

    /**
     * Create an origin-relative linkage path for the Linux linker from the components in [names].
     */
    @JvmStatic
    fun linuxOriginRelative(vararg names: String): PlatformPath =
        linuxOriginRelative(names.asIterable())

    /**
     * Create an executable-relative linkage path for the macOS linker from the components in
     * [names].
     */
    @JvmStatic
    fun macOSExecutableRelative(names: Iterable<String>): PlatformPath =
        unixRelative(listOf("@executable_path") + names)

    /**
     * Create an executable-relative linkage path for the macOS linker from the components in
     * [names].
     */
    @JvmStatic
    fun macOSExecutableRelative(vararg names: String): PlatformPath =
        macOSExecutableRelative(names.asIterable())

    /**
     * Create a loader-relative linkage path for the macOS linker from the components in [names].
     */
    @JvmStatic
    fun macOSLoaderRelative(names: Iterable<String>): PlatformPath =
        unixRelative(listOf("@loader_path") + names)

    /**
     * Create a loader-relative linkage path for the macOS linker from the components in [names].
     */
    @JvmStatic
    fun macOSLoaderRelative(vararg names: String): PlatformPath =
        macOSLoaderRelative(names.asIterable())

    /** Create a RPath-aware linkage path for the macOS linker from the components in [names]. */
    @JvmStatic
    fun macOSRPathRelative(names: Iterable<String>): PlatformPath =
        unixRelative(listOf("@rpath") + names)

    /** Create a RPath-aware linkage path for the macOS linker from the components in [names]. */
    @JvmStatic
    fun macOSRPathRelative(vararg names: String): PlatformPath =
        macOSExecutableRelative(names.asIterable())

    /** Create a Windows-like path from [path]. */
    @JvmStatic
    fun windows(path: String): PlatformPath = PlatformPath(windows(), windows().parsePath(path))

    /** Create a Windows-like absolute path starting at [root] from the components in [names]. */
    @JvmStatic
    fun windowsAbsolute(root: String, names: Iterable<String>): PlatformPath =
        PlatformPath(windows(), ParseResult(root, names))

    /** Create a Windows-like absolute path starting at [root] from the components in [names]. */
    @JvmStatic
    fun windowsAbsolute(root: String, vararg names: String): PlatformPath =
        windowsAbsolute(root, names.asIterable())

    /** Create a Windows-like relative path from the components in [names]. */
    @JvmStatic
    fun windowsRelative(names: Iterable<String>): PlatformPath =
        PlatformPath(windows(), ParseResult(null, names))

    /** Create a Windows-like relative path from the components in [names]. */
    @JvmStatic
    fun windowsRelative(vararg names: String): PlatformPath = windowsRelative(names.asIterable())
  }

  /** `true` iff this path is absolute. */
  val isAbsolute: Boolean
    get() = parseResult.isAbsolute

  /**
   * If this path is absolute, the name of the root (for UNIX, `/`; for Windows, e.g. `C:\`). `null`
   * for relative paths.
   */
  val root: String?
    get() = parseResult.root()

  /** The path components. */
  val components: List<String>
    get() = parseResult.names().toList()

  /** The name of the described file or directory, i.e., the last component. */
  val name: String
    get() = components.last()

  override fun equals(other: Any?): Boolean =
      when {
        this === other -> true
        other !is PlatformPath -> false
        else -> type == other.type && root == other.root && components == other.components
      }

  override fun hashCode(): Int = (31 * root.hashCode()) + components.hashCode()

  override fun toString(): String = type.toString(root, components)
}

/** Create a Linux origin-relative path for this path's name, ignoring other path components. */
internal fun PlatformPath.makeLinuxOriginRelative(): PlatformPath =
    PlatformPath.linuxOriginRelative(name)

/** Create a macOS executable-relative path for this path's name, ignoring other path components. */
internal fun PlatformPath.makeMacOSExecutableRelative(): PlatformPath =
    PlatformPath.macOSExecutableRelative(name)

/** Create a macOS loader-relative path for this path's name, ignoring other path components. */
internal fun PlatformPath.makeMacOSLoaderRelative(): PlatformPath =
    PlatformPath.macOSLoaderRelative(name)

/** Create a macOS RPath-aware path for this path's name, ignoring other path components. */
internal fun PlatformPath.makeMacOSRPathRelative(): PlatformPath =
    PlatformPath.macOSRPathRelative(name)
