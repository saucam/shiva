import BuildHelper._
import Dependencies._
import sbt.Keys._
import sbt._
import scala.sys.process._

Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / autoAPIMappings := true

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

inThisBuild(
  List(
    organization := "io.github.saucam",
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "saucam",
        "Yash Datta",
        "yd2590@columbia.edu",
        url("https://github.com/saucam")
      )
    )
  )
)

addCommandAlias("check", "fixCheck; fmtCheck")
addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheckAll")
addCommandAlias("prepare", "fix; fmt")

lazy val core = project
    .in(file("core"))
    .enablePlugins(BuildInfoPlugin, ScalaUnidocPlugin)
    .settings(dottySettings)
    .settings(stdSettings("shiva-core"))
    .settings(
      homepage := Option(url("https://github.com/saucam/shiva")),
      libraryDependencies ++= List(
        breeze,
        fastutil,
        scalaTest
      ),
      target in(ScalaUnidoc, unidoc) := (baseDirectory in LocalRootProject).value / "website" / "static" / "api",
      // Compile / unidoc / target := baseDirectory.value / ".." / "website" / "static" / "api",
      cleanFiles += (target in(unidoc)).value
    )

lazy val root = project
    .in(file("."))
    .enablePlugins(ScalaUnidocPlugin)
    .settings(
      name := "shiva",
      publish / skip := true,
      scalaVersion := `shiva.scala.version`
    )
    .aggregate(core, docs)

lazy val docs = project
  .in(file("shiva-docs"))
  .settings(
    publish / skip := true,
    excludeDependencies ++= Seq(
      ("org.scala-lang.modules" % "scala-collection-compat_2.13"),
      ("com.lihaoyi" % "pprint_2.13"),
      ("com.lihaoyi" % "fansi_2.13"),
      ("com.lihaoyi" % "sourcecode_2.13"),
      ("com.geirsson" % "metaconfig-core_2.13"),
      ("com.geirsson" % "metaconfig-typesafe-config_2.13"),
      ("org.typelevel" % "paiges-core_2.13")
    ),
    crossScalaVersions := Seq(Scala213, ScalaDotty),
    scalaVersion := ScalaDotty,
    moduleName := "shiva-docs",
    unidocProjectFilter in(ScalaUnidoc, unidoc) := inProjects(core),
    target in(ScalaUnidoc, unidoc) := (baseDirectory in LocalRootProject).value / "website" / "static" / "api",
    cleanFiles += (target in(ScalaUnidoc, unidoc)).value,
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(unidoc in (core, Compile)).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value,
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    mdocVariables := Map(
      "VERSION" -> version.value
    ),
    postDocusaurusBuild := {
      Process("node website/post-build.js").!
    },
    docusaurusBuildWithPostBuild := Def.sequential(docusaurusCreateSite, postDocusaurusBuild).value
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
  .dependsOn(core)

val postDocusaurusBuild = taskKey[Unit]("Run post-docusaurus build script")
val docusaurusBuildWithPostBuild = taskKey[Unit]("Run docusaurus create site with post-build.js")
