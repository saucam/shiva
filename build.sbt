import BuildHelper._
import Dependencies._
import sbt.Keys._

Global / onChangedBuildSource := ReloadOnSourceChanges

sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

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
    .enablePlugins(BuildInfoPlugin)
    .settings(dottySettings)
    .settings(stdSettings("shiva-core"))
    .settings(
      homepage := Option(url("https://github.com/saucam/shiva")),
      libraryDependencies ++= List(
        breeze,
        fastutil,
        scalaTest
      )
    )

lazy val root = project
    .in(file("."))
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
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value,
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    mdocVariables := Map(
      "VERSION" -> version.value
    )
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
