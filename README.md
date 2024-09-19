<!--
   SPDX-License-Identifier: CC-BY-4.0

   Copyright 2019-2024 The TurnKey Authors

   This work is licensed under the Creative Commons Attribution 4.0
   International License.

   You should have received a copy of the license along with this
   work. If not, see <https://creativecommons.org/licenses/by/4.0/>.
-->

[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/tudo-aqua/turnkey-gradle-plugin/ci.yml?logo=githubactions&logoColor=white)](https://github.com/tudo-aqua/turnkey-gradle-plugin/actions)
[![JavaDoc](https://javadoc.io/badge2/tools.aqua/turnkey-gradle-plugin/javadoc.svg)](https://javadoc.io/doc/tools.aqua/turnkey-gradle-plugin)
[![Maven Central](https://img.shields.io/maven-central/v/tools.aqua/turnkey-gradle-plugin?logo=apache-maven)](https://search.maven.org/artifact/tools.aqua/turnkey-gradle-plugin)

# TurnKey Gradle Plugin

This is a helper plugin for transforming existing libraries into TurnKey bundles. For a detailed
discussion of TurnKey bundles, see
[the TurnKey Support Library documentation](https://github.com/tudo-aqua/turnkey-support). The
plugin provides two core features: binary rewriting and source file rewriting.

## Using the Plugin

The plugin is published on Maven Central, _not_ the Gradle Plugin Portal. Add to your
`settings.gradle.kts`:

```kotlin
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenLocal()
  }
}
```

then import the plugin in the `build.gradle.kts` via

```kotlin
plugins {
  id("tools.aqua.turnkey") version "<version>"
}
```

When applied, the plugin does nothing. You must add tasks from the plugin to your project to handle
your specific build scenario.

## Binary Rewriting

Binary rewriting operates on bundles of libraries for a specific platform. Each bundle is analyzed
as a whole and transformed into TurnKey-linked artifacts. The plugin automatically generates a
`turnkey.xml` descriptor file for the bundle. The task is applied like this:

```kotlin
val turnkeyLinuxAMD64 by
    tasks.registering(ELFTurnKeyTask::class) {
      libraries.from(myBinaries).filter { it.isFile }
      rootLibraryNames.add("libexamplejava.so")
      targetDirectory = layout.buildDirectory.dir("turnkey/linux-amd64")
      targetSubPath = "com/acme/example/linux/amd64"
    }
```

Note that the task class is specific to the binary format (in this example, ELF). TurnKey tasks
offer the following properties:

- `libraries` (required): must be set to the library files to be analyzed. Do _not_ include
  duplicates, non-library files, or directories.
- `rootLibraryNames` (required): the libraries that are required by your Java code. This is often a
  specific JNI library. All dependencies of this library are automatically included by the plugin.
- `targetDirectory` (required): the base output directory for the transformed files and the
  `turnkey.xml`.
- `targetSubPath` (optional): when set, this suffix is added to the target directory for placement.
  This is a convenience that allows introducing a package structure while allowing a subsequent task
  to add the `targetDirectory` to its inputs while maintaining Gradle dependency tracking.

At the moment, tasks for COFF (Windows), ELF (Linux), and Mach-O (macOS) libraries exist. These
tasks require external, native programs to be installed. Native program lookup is designed to handle
most installation naming schemes by searching the following naming schemes for the program `example`
in each directory on the `$PATH`:

1. try `example`
2. for each defined file name extension `.`_e_ in `$PATHEXT`, try `example.`_e_ (required for `.exe`
   on Windows)
3. try `example-`_n_, where _n_ is a numeric version (required for multiple LLVM versions on
   Debian); in case of conflicts, the highest version is chosen
4. try `llvm-example` (required for LLVM reimplementations of tools)
5. try `llvm-example-`_n_ (required for multiple LLVM reimplementaion version on Debian); in case of
   conflicts, the highest version is chosen

### COFF Support

COFF libraries can be handled by the `COFFTurnKeyTask`. This task requires a `readobj` binary to be
installed. Both the GNU project and the LLVM project provide implementations. Since on Windows, no
relative linkage can be specified, this task only analyzes the dependency graph and generates
explicit load instructions for all dependents. No rewriting is performed.

### ELF Support

ELF libraries can be handled by the `ELFTurnKeyTask`. This task requires a `patchelf` binary to be
installed, which is maintained by the NixOS project. This sets each library's RPath to origin
linkage (i.e., same-directory lookup). Only the root libraries need to be loaded.

### Mach-O Support

Mach-O libraries can be handled by the `MachOTurnKeyTask`. This task requires both a
`install_name_tool` (alternatively: `install-name-tool`) and a `otool` binary to be installed. Both
Apple and the LLVM project provide implementations. This sets each bundled dependency's linkage to
loader-relative paths (i.e., same-directory lookup). Only the root libraries need to be loaded.

## Java Rewriting

Java rewriting operates on single files via [JavaParser](https://javaparser.org/). It is designed to
insert calls to the TurnKey support library into an existing native wrapper that does not use the
TurnKey abstraction. A transformation can be defined like this:

```kotlin
val rewriteNativeJava by
    tasks.registering(JavaRewriteTask::class) {
      inputDirectory = myJava.flatMap { it.outputDir }
      inputFile = "com/acme/example/Native.java"

      rewrite { compilationUnit ->
        val nativeClass = compilationUnit.types.single { it.name.id == "Native" }
        val staticInitializer =
          nativeClass.members
            .filterIsInstance<InitializerDeclaration>()
            .first(InitializerDeclaration::isStatic)
        staticInitializer.body =
          BlockStmt(
            NodeList(
              ExpressionStmt(
                MethodCallExpr(
                  "tools.aqua.turnkey.support.TurnKey.load",
                  StringLiteralExpr("com/acme/example"),
                  MethodReferenceExpr(
                    ClassExpr(parseClassOrInterfaceType("com.acme.example.Native")),
                    NodeList(),
                    "getResourceAsStream")),
              )))
      }

      outputDirectory = layout.buildDirectory.dir("generated/rewritten")
    }
```

The task offers the following properties:

- `inputDirectory` (required): must be set to a directory containing Java source files.
- `inputFile` (required): is resolved relative to the `inputDirectory` to locate the file to
  rewrite. This is a convenience that allows depending on a previous task's outputs via the
  `inputDirectory` while maintaining Gradle dependency tracking.
- `rewrite` (required): a function that accepts a JavaParser `CompilationUnit` and applies the
  desired transformation. For convenience, this can be set via a function, as shown above. The
  example above replaces a static initializer with a call to the TurnKey support library.
  **Important:** the lambda assigned to this variable is subjected to Java serialization by Gradle
  for rebuild elision purposes. If the lambda captures any non-serializable object (e.g., the
  containing task object), runtime errors will result!
- `outputDirectory` (required): the base output directory for the transformed Java file.
- `outputFile` (optional): is resolved relative to the `outputDirectory` to locate the rewritten
  file (defaults to the `inputFile`). This is a convenience that allows maintaining a package
  structure while allowing a subsequent task to add the `outputDirectory` to its inputs while
  maintaining Gradle dependency tracking.

## Java and Gradle

The plugin requires Java 17 (minimum for Gradle 9+) and is linked against the Gradle 8.10.1 stack.

## License

The plugin and other non-runtime code are licensed under the
[Apache Licence, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). Standalone documentation
is licensed under the
[Creative Commons Attribution 4.0 International License](https://creativecommons.org/licenses/by/4.0/).
Dependencies use other open-source licenses.
