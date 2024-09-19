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

import org.jgrapht.Graph
import org.jgrapht.Graphs.getOppositeVertex

/** Get the set of nodes in this graph reachable from the nodes in [from], including these nodes. */
internal fun <V, E> Graph<V, E>.reachSet(from: Set<V>): Set<V> {
  val black = mutableSetOf<V>()
  val grey = from.toMutableSet()
  while (grey.isNotEmpty()) {
    val next = grey.first()

    grey -= next
    black += next

    outgoingEdgesOf(next).forEach {
      val adjacent = getOppositeVertex(this, it, next)
      if (adjacent !in black) {
        grey += adjacent
      }
    }
  }
  return black
}

/** Get the set of nodes in this graph reachable from the nodes in [from], including these nodes. */
internal fun <V, E> Graph<V, E>.reachSet(vararg from: V): Set<V> = reachSet(from.toSet())
