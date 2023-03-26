import sbt._
import Keys._
import sbtbuildinfo._
import BuildInfoKeys._
import scalafix.sbt.ScalafixPlugin.autoImport._
import xerial.sbt.Sonatype.autoImport._

import java.nio.file.FileSystems
import java.nio.file.Files

object BuildHelper {

  private val versions: Map[String, String] = {
    import org.snakeyaml.engine.v2.api.{Load, LoadSettings}

    import java.util.{List => JList, Map => JMap}
    import scala.jdk.CollectionConverters._

    val doc = new Load(LoadSettings.builder().build())
      .loadFromReader(scala.io.Source.fromFile(".github/workflows/ci.yaml").bufferedReader())
    val yaml = doc.asInstanceOf[JMap[String, JMap[String, JMap[String, JMap[String, JMap[String, JList[String]]]]]]]
    val list = yaml.get("jobs").get("test").get("strategy").get("matrix").get("scala").asScala
    list.map(v => (v.split('.').take(2).mkString("."), v)).toMap
  }

  val Scala213: String = versions("2.13")
  val ScalaDotty: String = versions("3.2")

  val `shiva.scala.version` = {
    readVersionFromSysProps().orElse(readVersionFromFile()) match {
      case Some(value) => value
      case None =>
        println(s"=== [VERSION] No version from sys-props or file, defaulting to ${ScalaDotty} ===")
        ScalaDotty
    }
  }

    def readVersionFromSysProps() = {
    val version = sys.props.get("shiva.scala.version")
    if (version.isDefined) {
      println(s"=== [VERSION] Reading Version from Sys Props: ${version} ===")
    }
    version
  }

  def readVersionFromFile() = {
    val sPath = FileSystems.getDefault().getPath(".shiva.scala.version")
    if (Files.exists(sPath)) {
      val strOpt = Files.readAllLines(sPath).toArray().headOption
      strOpt match {
        case Some("2.13") =>
          println(s"=== [VERSION] Reading Version from .shiva.scala.version file: ${Scala213} ===")
          Some(Scala213)
        case Some("3") =>
          println(s"=== [VERSION] Reading Version from .shiva.scala.version file: ${ScalaDotty} ===")
          Some(ScalaDotty)
        case Some(v) =>
          throw new IllegalArgumentException(s"Only three values supported for .shiva.scala.version 2.12/2.13/3 but `${v}` found.")
        case None =>
          println("=== [VERSION] Found a .shiva.scala.version file but was empty. Skipping ===")
          None
      }
    } else {
      println("=== [VERSION] Found a .shiva.scala.version file but was empty. Skipping ===")
      None
    }
  }

  def isScala3 =
    CrossVersion.partialVersion(`shiva.scala.version`) match {
      case Some((3, _)) => true
      case _ => false
    }


  private def extraOptions(scalaVersion: String, optimize: Boolean) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _)) =>
        List("-language:implicitConversions", "-Xignore-scala2-macros")
      case Some((2, 13)) =>
        List("-Ywarn-unused:params,-implicits") ++ std2xOptions ++ optimizerOptions(optimize)
      case Some((2, 12)) =>
        List(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Ywarn-unused:params,-implicits",
          "-Xfuture",
          "-Xsource:2.13",
          "-Xmax-classfile-name",
          "242"
        ) ++ std2xOptions ++ optimizerOptions(optimize)
      case _ => Nil
    }

  private def optimizerOptions(optimize: Boolean): List[String] =
    if (optimize) List("-opt:l:inline", "-opt-inline-from:zio.internal.**") else Nil

    private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  )

    private val std2xOptions = Seq(
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )

  def stdSettings(prjName: String) =
    List(
      name := s"$prjName",
      crossScalaVersions := List(Scala213, ScalaDotty),
      ThisBuild / scalaVersion := Scala213,
      scalacOptions := stdOptions ++ extraOptions(scalaVersion.value, optimize = !isSnapshot.value),
      semanticdbEnabled := scalaVersion.value != ScalaDotty,
      semanticdbOptions += "-P:semanticdb:synthetics:on",
      semanticdbVersion := scalafixSemanticdb.revision,
      ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
      ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0",
      Test / parallelExecution := true,
      incOptions ~= (_.withLogRecompileOnMacro(false)),
      autoAPIMappings := true
    )

  def buildInfoSettings(packageName: String) =
    Seq(
      buildInfoKeys    := Seq[BuildInfoKey](organization, moduleName, name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := packageName
    )

  val dottySettings = Seq(
    crossScalaVersions := Seq(ScalaDotty),
    scalacOptions ++= {
      if (scalaVersion.value == ScalaDotty)
        Seq() // Seq("-noindent", "-Xcheck-macros")
      else
        Seq()
    },
    scalacOptions --= {
      if (scalaVersion.value == ScalaDotty)
        Seq() //Seq("-Xfatal-warnings")
      else
        Seq()
    },
    Compile / doc / sources  := {
      val old = (Compile / doc / sources).value
      if (scalaVersion.value == ScalaDotty) {
        Nil
      } else {
        old
      }
    },
    Test / parallelExecution := {
      val old = (Test / parallelExecution).value
      if (scalaVersion.value == ScalaDotty) {
        false
      } else {
        old
      }
    }
  )
}
