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

package tools.aqua.turnkey.plugin.util

import org.assertj.core.api.Assertions.assertThat
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import tools.aqua.turnkey.plugin.containsOnly

@TestInstance(PER_CLASS)
class GraphReachTest {

  @Test
  fun `graph reachability from single root works`() {
    val graph =
        SimpleDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java).apply {
          addVertex("a")
          addVertex("b")
          addVertex("c")
          addVertex("k")
          addVertex("x")

          addEdge("a", "b")
          addEdge("b", "c")
          addEdge("k", "c")
        }

    val reach = graph.reachSet("a")

    assertThat(reach).containsOnly("a", "b", "c")
  }

  @Test
  fun `graph reachability from multiple roots works`() {
    val graph =
        SimpleDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java).apply {
          addVertex("a")
          addVertex("b")
          addVertex("c")
          addVertex("k")
          addVertex("l")
          addVertex("x")

          addEdge("a", "b")
          addEdge("b", "c")
          addEdge("k", "c")
          addEdge("k", "l")
        }

    val reach = graph.reachSet("a", "k")

    assertThat(reach).containsOnly("a", "b", "c", "k", "l")
  }

  @Test
  fun `graph reachability from multiple disjoint roots works`() {
    val graph =
        SimpleDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java).apply {
          addVertex("a")
          addVertex("b")
          addVertex("c")
          addVertex("k")
          addVertex("l")
          addVertex("x")

          addEdge("a", "b")
          addEdge("b", "c")
          addEdge("k", "l")
        }

    val reach = graph.reachSet("a", "k")

    assertThat(reach).containsOnly("a", "b", "c", "k", "l")
  }
}
