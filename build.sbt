import BuildHelper._
import Dependencies._

inThisBuild(
  List(
    organization := "com.github.saucam",
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "saucam",
        "Yash Datta",
        "yd2590@gmail.com",
        url("https://github.com/saucam")
      )
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")

lazy val core = project
    .in(file("core"))
    .settings(
      moduleName := "shiva-core",
    )

lazy val root = project
    .in(file("."))
    .settings(
      publish / skip := true,
      scalaVersion := `shiva.scala.version`
    )
    .aggregate(core, docs)

lazy val docs = project
  .in(file("shiva-docs"))
  .settings(
    excludeDependencies ++= Seq(
      ("org.scala-lang.modules" % "scala-collection-compat_2.13"),
      ("com.lihaoyi" % "pprint_2.13"),
      ("com.lihaoyi" % "fansi_2.13"),
      ("com.lihaoyi" % "sourcecode_2.13"),
      ("com.geirsson" % "metaconfig-core_2.13"),
      ("com.geirsson" % "metaconfig-typesafe-config_2.13"),
      ("org.typelevel" % "paiges-core_2.13")
    ),
    crossScalaVersions := Seq(Scala212, Scala213, ScalaDotty),
    scalaVersion := ScalaDotty,
    moduleName := "shiva-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    mdocVariables := Map(
      "VERSION" -> version.value
    )
  )
  .enablePlugins(MdocPlugin)