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

import java.nio.file.Files.newDirectoryStream
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.*
import kotlin.reflect.KClass
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy.FAIL
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import tools.aqua.turnkey.plugin.analysis.AnalyzableNativeLibrary
import tools.aqua.turnkey.plugin.analysis.DependencyGraph
import tools.aqua.turnkey.plugin.analysis.NativeLibraryFactory
import tools.aqua.turnkey.plugin.analysis.SpeculativeNamedLibrary
import tools.aqua.turnkey.plugin.util.debug
import tools.aqua.turnkey.plugin.util.mapToSet
import tools.aqua.turnkey.plugin.util.warn
import tools.aqua.turnkey.support.TurnKey.TURNKEY_FILE_NAME
import tools.aqua.turnkey.support.TurnKeyMetadata

/**
 * Common superclass for TurnKey tasks. A task handles native libraries of type [L] (analyzable) and
 * [S] (speculative) with linkage names of type [T].
 */
abstract class AbstractTurnKeyTask<
    T, L : AnalyzableNativeLibrary<T, S>, S : SpeculativeNamedLibrary<T>>(
    private val analyzableClass: KClass<L>,
    private val speculativeClass: KClass<S>
) : DefaultTask() {

  /** The library source files to analyze and transform. */
  @get:InputFiles abstract val libraries: ConfigurableFileCollection

  /** The name(s) of the root libraries, i.e. the libraries that are used by a JNI loader. */
  @get:Input abstract val rootLibraryNames: SetProperty<String>

  /** Gradle file system interface. */
  @get:Inject protected abstract val fs: FileSystemOperations

  /** The target directory to copy libraries to. This should then be added to a source set. */
  @get:OutputDirectory abstract val targetDirectory: DirectoryProperty

  /**
   * An optional, additional prefix to use for the libraries. That can be used to place the output
   * files into a specific package without having to "escape" the package directory for source set
   * adding.
   */
  @get:Input abstract val targetSubPath: Property<String>

  /**
   * Execute turnkey analysis and transformation. Work is performed in a task-specific temporary
   * directory. This performs the following user-visible steps:
   * 1. All [libraries] are analyzed used the subclass-defined [analyzer]. This yields linkage
   *    information, from which a dependency graph is built.
   * 2. The dependency graph is pruned to the dependencies of the [rootLibraryNames].
   * 3. A sequence of load instructions is determined that loads all required bundled libraries in
   *    order. If the subclass reports [autoloadSupported], this will only contain the
   *    [rootLibraryNames], otherwise, this will be the reverse topological sorting on the
   *    dependency graph.
   * 4. All metadata are aggregated into a [TurnKeyMetadata] file (`turnkey.xml`).
   * 5. The subclass-defined [rewrite] is called. This applies transformations to make the library
   *    turnkey-compatible.
   */
  @OptIn(ExperimentalPathApi::class)
  @TaskAction
  fun convertToTurnkey() {
    val workDir = temporaryDir.toPath()
    logger.info("Cleaning working directory: $workDir")
    newDirectoryStream(workDir).forEach(Path::deleteRecursively)

    val actualFiles =
        libraries.mapNotNull {
          if (it.isDirectory) {
            logger.warn { "Ignoring directory in library list: $it" }
            null
          } else it
        }

    fs.copy {
      from(actualFiles)
      into(workDir)
      duplicatesStrategy = FAIL
    }

    val analysis = analyzer
    val libraries = newDirectoryStream(workDir).toList().map { analysis(it) }
    logger.debug { "Working directory contents: ${libraries.joinToString()}" }
    val dependencyGraph = DependencyGraph.of(libraries, analyzableClass, speculativeClass)
    logger.debug { "Dependency graph: $dependencyGraph" }

    val (actualRoots, rootedGraph) = filterUnneededLibraries(libraries, dependencyGraph)
    val includedLibraries = rootedGraph.localLibrariesInLoadOrder
    val systemLibraries = rootedGraph.systemLibraries

    cleanupUnusedLibraries(libraries, includedLibraries)
    writeMetadata(actualRoots, includedLibraries, systemLibraries, workDir)

    logger.info("Performing rewrite operation(s)")
    includedLibraries.forEach { rewrite(it, includedLibraries) }

    val realTarget =
        if (targetSubPath.isPresent) targetDirectory.dir(targetSubPath.get()) else targetDirectory
    logger.info("Copying libraries to $realTarget")
    fs.copy {
      from(workDir)
      into(realTarget)
      duplicatesStrategy = FAIL
    }
  }

  /**
   * Prune the [dependencyGraph] to only contain libraries reachable from [libraries]. This also
   * performs some checks and returns the set of roots not reachable from another root and the
   * pruned graph.
   */
  private fun filterUnneededLibraries(
      libraries: Iterable<L>,
      dependencyGraph: DependencyGraph<T, L, S>
  ): Pair<MutableSet<L>, DependencyGraph<T, L, S>> {
    val roots =
        rootLibraryNames.get().mapToSet { rootLibraryName ->
          libraries.singleOrNull { it.fuzzyName == rootLibraryName }
              ?: throw GradleException("Root library $rootLibraryName not in input files")
        }
    val limitedGraph = dependencyGraph.subgraphFrom(roots)
    val validRoots = roots.toMutableSet().apply { retainAll(limitedGraph.linkageRoots) }

    val invalidRoots = roots - validRoots
    if (invalidRoots.isNotEmpty()) {
      logger.warn {
        "Root libraries ${invalidRoots.map { it.fuzzyName }} are not roots, but dependencies of roots. These will be treated as dependencies."
      }
    }

    return validRoots to limitedGraph
  }

  /** Delete the files for all [libraries] that are not [includedLibraries]. */
  private fun cleanupUnusedLibraries(libraries: List<L>, includedLibraries: List<L>) {
    val unusedLibraries = libraries - includedLibraries.toSet()
    if (unusedLibraries.isNotEmpty()) {
      logger.info("Unused libraries ${unusedLibraries.map { it.fuzzyName }} will be removed")
    }
    unusedLibraries.forEach { it.path.deleteExisting() }
  }

  /**
   * Write a [TurnKeyMetadata] file to [workDir]`/turnkey.xml` containing the given [roots],
   * [includedLibraries], and [systemLibraries].
   */
  private fun writeMetadata(
      roots: Iterable<L>,
      includedLibraries: Iterable<L>,
      systemLibraries: Iterable<S>,
      workDir: Path
  ) {

    if (logger.isInfoEnabled) {
      logger.info("Bundled libraries (in resolved load order):")
      includedLibraries.forEach { logger.info(it.toString()) }
      logger.info("System libraries (not bundled):")
      systemLibraries.forEach { logger.info(it.toString()) }
    }

    val loadCommands =
        if (autoloadSupported) {
          logger.info("Autoload supported, load order will only contain roots")
          roots.map { it.path.name }
        } else {
          logger.info("Autoload not supported, load order will contain all libraries")
          includedLibraries.map { it.path.name }.reversed()
        }

    val bundledLibraries = includedLibraries.map { it }

    val metadata =
        TurnKeyMetadata(
            bundledLibraries.mapToSet { it.path.name },
            systemLibraries.mapToSet { it.linkageName.toString() },
            loadCommands)
    (workDir / TURNKEY_FILE_NAME).outputStream().use(metadata::writeTo)
  }

  /** Obtain the library analyzer for the given library type. */
  @get:Internal protected abstract val analyzer: NativeLibraryFactory<T, L, S>

  /**
   * `true` iff autoloading is supported, i.e. the system loader for the given library type will
   * correctly resolve dependencies. If this is `false`, all dependencies must be explicitly loaded
   * via JNI before the actual JNI libraries.
   */
  @get:Internal protected abstract val autoloadSupported: Boolean

  /** Perform the rewrites necessary for the given library type to make turnkey loading possible. */
  protected abstract fun rewrite(library: L, includedLibraries: List<L>)
}
