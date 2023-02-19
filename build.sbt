import BuildHelper._
import Dependencies._

inThisBuild(
  List(
    organization := "saucam.github",
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
    )

lazy val root = project
    .in(file("."))
    .settings(
      publish / skip := true,
      scalaVersion := `shiva.scala.version`
    )
    .aggregate(`core`)
