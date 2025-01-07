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

import kotlin.reflect.KClass
import org.jgrapht.graph.AbstractGraph
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.traverse.TopologicalOrderIterator
import tools.aqua.turnkey.plugin.util.reachSet

/**
 * Analyzes dependencies between native libraries of type [L] (analyzable) and [S] (speculative)
 * with linkage names of type [T]. This class can identify loading roots and load orders as well as
 * disambiguate bundled and system libraries.
 */
internal class DependencyGraph<
    T, L : AnalyzableNativeLibrary<T, S>, S : SpeculativeNamedLibrary<T>>(
    private val graph: AbstractGraph<NativeLibrary<T>, DefaultEdge>,
    private val analyzableClass: KClass<L>,
    private val speculativeClass: KClass<S>
) {
  /** Holder for factory functions. */
  companion object {
    /**
     * Create a dependency graph for the given [libraries] with reified classes [analyzableClass]
     * and [speculativeClass].
     *
     * @throws IllegalArgumentException if a dependency loop is defined by the libraries.
     */
    fun <T, L : AnalyzableNativeLibrary<T, S>, S : SpeculativeNamedLibrary<T>> of(
        libraries: Iterable<L>,
        analyzableClass: KClass<L>,
        speculativeClass: KClass<S>
    ): DependencyGraph<T, L, S> {
      // populate the lookup with all existing libraries
      val existingLookup =
          buildMap<String, AnalyzableNativeLibrary<T, S>> {
            libraries.forEach { new ->
              merge(new.fuzzyName, new) { old, _ ->
                val diff = old.fuzzyMismatch - new.fuzzyMismatch
                require(diff != 0) {
                  "equally fuzzy libraries $old and $new with identical fuzzy name added"
                }
                if (diff < 0) old else new
              }
            }
          }
      val lookup = existingLookup.toMutableMap<String, NativeLibrary<T>>()

      // populate the graph with all existing libraries
      val graph = DirectedAcyclicGraph<NativeLibrary<T>, DefaultEdge>(DefaultEdge::class.java)
      libraries.forEach(graph::addVertex)

      libraries.forEach { library ->
        library.libraryDependencies.forEach { dependency ->
          // if the library is already present in the lookup (as analyzable or speculative form),
          // use that
          // if not, add the speculative form present to the lookup and graph and use it
          val real =
              lookup.computeIfAbsent(dependency.fuzzyName) { dependency.also(graph::addVertex) }

          // add the dependency edge
          graph.addEdge(library, real)
        }
      }

      return DependencyGraph(graph, analyzableClass, speculativeClass)
    }

    /** Create a dependency graph for the given [libraries]. */
    inline fun <
        T, reified L : AnalyzableNativeLibrary<T, S>, reified S : SpeculativeNamedLibrary<T>> of(
        libraries: Iterable<L>
    ): DependencyGraph<T, L, S> = of(libraries, L::class, S::class)

    /** Create a dependency graph for the given [libraries]. */
    inline fun <
        T, reified L : AnalyzableNativeLibrary<T, S>, reified S : SpeculativeNamedLibrary<T>> of(
        vararg libraries: L
    ): DependencyGraph<T, L, S> = of(libraries.asIterable())
  }

  /**
   * Compute a dependency subgraph containing only the given [roots] and their transitive
   * dependencies.
   */
  fun subgraphFrom(roots: Set<NativeLibrary<T>>): DependencyGraph<T, L, S> =
      DependencyGraph(AsSubgraph(graph, graph.reachSet(roots)), analyzableClass, speculativeClass)

  /**
   * Compute a dependency subgraph containing only the given [roots] and their transitive
   * dependencies.
   */
  fun subgraphFrom(vararg roots: NativeLibrary<T>): DependencyGraph<T, L, S> =
      subgraphFrom(roots.toSet())

  /** The libraries that are not dependencies of any other library in this graph. */
  val linkageRoots: Set<L>
    get() =
        graph
            .vertexSet()
            .asSequence()
            .filter { graph.inDegreeOf(it) == 0 }
            .filterIsInstance(analyzableClass.java)
            .toSet()

  /**
   * An ordering over the bundled libraries contained in this graph that maintains load order: if
   * any library `a` depends (transitively) on `b`, this will contain `a` before `b`.
   */
  val localLibrariesInLoadOrder: List<L>
    get() =
        TopologicalOrderIterator(graph).asSequence().filterIsInstance(analyzableClass.java).toList()

  /** The system libraries contained in this graph, in no particular order. */
  val systemLibraries: Set<S>
    get() = graph.vertexSet().asSequence().filterIsInstance(speculativeClass.java).toSet()
}
