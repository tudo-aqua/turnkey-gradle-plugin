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

package tools.aqua.turnkey.plugin

import com.github.javaparser.ParseProblemException
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Generic editing task Java files via the JavaParser library. This can modify a single Java file
 * ([CompilationUnit] in Java parlance) and writes the altered file to a new location.
 */
abstract class JavaRewriteTask : DefaultTask() {

  /** The directory containing the Java source file(s). */
  @get:InputDirectory abstract val inputDirectory: DirectoryProperty

  /**
   * The *relative* path in the [inputDirectory] where the source file to modify is located. This
   * should usually be the package and file name part of the source file, e.g.
   * `com/acme/Example.java`.
   */
  @get:Input abstract val inputFile: Property<String>

  /**
   * The rewrite script. The script must modify the passed [CompilationUnit]; the plugin handles the
   * writing back. Defaults to a no-op.
   *
   * Note that Gradle will attempt to invoke Java Serialization on the rewrite script to support
   * up-to-date checking. As a result, if a lambda is passed that references the containing class
   * (i.e., an instance of [JavaRewriteTask]), serialization will fail. This means that the lambda
   * may not capture the surrounding task object!
   */
  @get:Input abstract val rewrite: Property<(CompilationUnit) -> Unit>

  init {
    @Suppress("LeakingThis") rewrite.convention {}
  }

  /** Convenience function to set [rewrite]. */
  fun rewrite(action: (CompilationUnit) -> Unit) {
    // XXX: ktlint + spotless deletes assignment operator import
    rewrite.set(action)
  }

  /** The directory where the rewritten Java source file is written. */
  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  /**
   * The *relative* path in the [outputDirectory] where the rewritten source file is placed. This
   * should usually be the package and file name part of the source file, e.g.
   * `com/acme/Example.java`. Defaults to [inputFile]. This allows reuse of the [outputDirectory] in
   * a subsequent step as, e.g., a source directory without having to escape the package part.
   */
  @get:Input abstract val outputFile: Property<String>

  init {
    @Suppress("LeakingThis") outputFile.convention(inputFile)
  }

  /**
   * The task logic. The following steps are performed:
   * 1. Load [inputDirectory]`/`[inputFile].
   * 2. Parse the contents using JavaParser and throw [ParseProblemException] if parsing fails.
   * 3. Apply [rewrite] to the parsed compilation unit.
   * 4. Write the modified compilation unit to [outputDirectory]`/`[outputFile].
   */
  @TaskAction
  fun doRewrite() {
    val inputPath = inputDirectory.file(inputFile).get().asFile.toPath()
    val compilationUnit = StaticJavaParser.parse(inputPath)

    rewrite.get()(compilationUnit)

    val outputPath = outputDirectory.file(inputFile).get().asFile.toPath()
    outputPath.parent.createDirectories()
    outputPath.writeText(compilationUnit.toString())
  }
}
