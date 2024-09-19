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

import com.diffplug.gradle.spotless.SpotlessTask
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.gradle.node.variant.computeNodeDir
import com.github.gradle.node.variant.computeNodeExec
import org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE
import org.gradle.api.attributes.Bundling.EXTERNAL
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.DOCUMENTATION
import org.gradle.api.attributes.DocsType.DOCS_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.plugins.BasePlugin.BUILD_GROUP
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
  `maven-publish`
  signing

  alias(libs.plugins.detekt)
  alias(libs.plugins.dokka)
  alias(libs.plugins.gitVersioning)
  alias(libs.plugins.nexusPublish)
  alias(libs.plugins.node)
  alias(libs.plugins.spotless)
  alias(libs.plugins.taskTree)
  alias(libs.plugins.versions)
}

group = "tools.aqua"

version = "0.0.0-undetected-SNAPSHOT"

gitVersioning.apply {
  describeTagFirstParent = false
  refs {
    considerTagsOnBranches = true
    tag("(?<version>.*)") {
      // on a tag: use the tag name as is
      version = "\${ref.version}"
    }
    branch("main") {
      // on the main branch: use <last.tag.version>-<distance>-<commit>-SNAPSHOT
      version = "\${describe.tag.version}-\${describe.distance}-\${commit.short}-SNAPSHOT"
    }
    branch(".+") {
      // on other branches: use <last.tag.version>-<branch.name>-<distance>-<commit>-SNAPSHOT
      version =
          "\${describe.tag.version}-\${ref.slug}-\${describe.distance}-\${commit.short}-SNAPSHOT"
    }
  }

  // optional fallback configuration in case of no matching ref configuration
  rev {
    // in case of missing git data: use 0.0.0-unknown-0-<commit>-SNAPSHOT
    version = "0.0.0-unknown-0-\${commit.short}-SNAPSHOT"
  }
}

repositories { mavenCentral() }

dependencies {
  api(libs.javaparser)

  implementation(libs.commons.io)
  implementation(libs.commons.text)
  implementation(libs.jgrapht)
  implementation(libs.jimfs)
  implementation(libs.turnkey.support)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.launcher)

  detektPlugins(libs.detekt.compiler)
  detektPlugins(libs.detekt.faire)
}

node {
  download = true
  workDir = layout.buildDirectory.dir("nodejs")
}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isStable = stableKeyword || regex.matches(version)
  return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
  gradleReleaseChannel = "current"
  revision = "release"
  rejectVersionIf { isNonStable(candidate.version) && !isNonStable(currentVersion) }
}

spotless {
  kotlin {
    licenseHeaderFile(project.file("config/license/Apache-2.0-cstyle")).updateYearWithLatest(true)
    ktfmt()
  }
  kotlinGradle {
    licenseHeaderFile(project.file("config/license/Apache-2.0-cstyle"), "(plugins|import )")
        .updateYearWithLatest(true)
    ktfmt()
  }
  format("markdown") {
    target("*.md")
    licenseHeaderFile(project.file("config/license/CC-BY-4.0-xmlstyle"), """(#+|\[!\[)""")
        .updateYearWithLatest(true)
    prettier()
        .npmInstallCache()
        .nodeExecutable(computeNodeExec(node, computeNodeDir(node)).get())
        .config(mapOf("parser" to "markdown", "printWidth" to 100, "proseWrap" to "always"))
  }
  yaml {
    target(".github/**/*.yml")
    licenseHeaderFile(project.file("config/license/Apache-2.0-hashmark"), "[A-Za-z-]+:")
        .updateYearWithLatest(true)
    prettier()
        .npmInstallCache()
        .nodeExecutable(computeNodeExec(node, computeNodeDir(node)).get())
        .config(mapOf("parser" to "yaml", "printWidth" to 100))
  }
  format("toml") {
    target("gradle/libs.versions.toml")
    licenseHeaderFile(project.file("config/license/Apache-2.0-hashmark"), """\[[A-Za-z-]+]""")
        .updateYearWithLatest(true)
    prettier(mapOf("prettier-plugin-toml" to libs.versions.prettier.toml.get()))
        .npmInstallCache()
        .nodeExecutable(computeNodeExec(node, computeNodeDir(node)).get())
        .config(
            mapOf(
                "plugins" to listOf("prettier-plugin-toml"),
                "parser" to "toml",
                "alignComments" to false,
                "printWidth" to 100,
            ))
  }
}

tasks.withType<SpotlessTask>().configureEach { dependsOn(tasks.npmSetup) }

kotlin { jvmToolchain(17) }

val kdocJar by
    tasks.registering(Jar::class) {
      group = BUILD_GROUP
      dependsOn(tasks.dokkaHtml)
      from(tasks.dokkaHtml.flatMap { it.outputDirectory })
      archiveClassifier.set("kdoc")
    }

val kdoc =
    configurations.consumable("kdocElements") {
      isVisible = false

      attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(DOCUMENTATION))
        attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
        attribute(BUNDLING_ATTRIBUTE, objects.named(EXTERNAL))
        attribute(DOCS_TYPE_ATTRIBUTE, objects.named("kdoc"))
      }

      outgoing.artifact(kdocJar)
    }

val javaComponent = components.findByName("java") as AdhocComponentWithVariants

javaComponent.addVariantsFromConfiguration(kdoc.get()) {}

val javadocJar by
    tasks.registering(Jar::class) {
      group = BUILD_GROUP
      dependsOn(tasks.dokkaJavadoc)
      from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
      archiveClassifier.set("javadoc")
    }

java {
  withSourcesJar()
  withJavadocJar()
}

tasks.test {
  useJUnitPlatform()
  testLogging { events(PASSED, SKIPPED, FAILED) }
}

gradlePlugin {
  plugins {
    create("turnKeyPlugin") {
      id = "tools.aqua.turnkey"
      implementationClass = "tools.aqua.turnkey.plugin.TurnKeyPlugin"
    }
  }
}

afterEvaluate {
  // publication registration is delayed by the java-gradle-plugin until after evaluation

  val maven by
      publishing.publications.named<MavenPublication>("pluginMaven") {
        pom {
          name = "TurnKey Gradle Plugin"
          description =
              "Plugin for creating TurnKey projects that provides access to native library and Java rewriting tools."
          url = "https://github.com/tudo-aqua/turnkey-gradle-plugin"
          licenses {
            license {
              name = "Apache License, Version 2.0"
              url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
          }
          developers {
            developer {
              name = "Simon Dierl"
              email = "simon.dierl@tu-dortmund.de"
              organization = "AQUA Group, Department of Computer Science, TU Dortmund University"
              organizationUrl = "https://aqua.engineering/"
            }
          }
          scm {
            connection = "scm:git:https://github.com/tudo-aqua/turnkey-gradle-plugin.git"
            developerConnection = "scm:git:ssh://git@github.com:tudo-aqua/turnkey-gradle-plugin.git"
            url = "https://github.com/tudo-aqua/turnkey-gradle-plugin/tree/main"
          }
        }
      }

  val marker by
      publishing.publications.named<MavenPublication>("turnKeyPluginPluginMarkerMaven") {
        pom {
          name = "TurnKey Gradle Plugin Marker"
          description = "Plugin marker for tools.aqua.turnkey plugin."
          url = "https://github.com/tudo-aqua/turnkey-gradle-plugin"
          licenses {
            license {
              name = "Apache License, Version 2.0"
              url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
          }
          developers {
            developer {
              name = "Simon Dierl"
              email = "simon.dierl@tu-dortmund.de"
              organization = "AQUA Group, Department of Computer Science, TU Dortmund University"
              organizationUrl = "https://aqua.engineering/"
            }
          }
          scm {
            connection = "scm:git:https://github.com/tudo-aqua/turnkey-gradle-plugin.git"
            developerConnection = "scm:git:ssh://git@github.com:tudo-aqua/turnkey-gradle-plugin.git"
            url = "https://github.com/tudo-aqua/turnkey-gradle-plugin/tree/main"
          }
        }
      }

  signing {
    setRequired { gradle.taskGraph.allTasks.any { it is PublishToMavenRepository } }
    useGpgCmd()
    sign(maven, marker)
  }
}

nexusPublishing { this.repositories { sonatype() } }
