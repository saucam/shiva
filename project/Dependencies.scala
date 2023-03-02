import sbt._

object Dependencies {

  implicit class ModuleIdOps(module: ModuleID) {
    def excludeVersionConflicting =
      module.excludeAll(
        (Seq(
          // Remve scala-collection-compat, pprint, fansi, and sourcecode from dependencies here
          // since we are manually importing them
          ExclusionRule(organization = "org.scala-lang.modules"),
          ExclusionRule(organization = "com.lihaoyi")
        )): _*
      )
  }

  val breezeVersion = "2.1.0"
  val fastUtilVersion = "8.5.11"
  val dropwizardVersion = "4.2.9"

  val scalaTestVersion = "3.2.15"
  val PPrintVersion = "0.6.6"
  val FansiVersion = "0.2.14"
  val SourceCodeVersion = "0.2.8"

  val breeze               = "org.scalanlp" %% "breeze" % breezeVersion
  val fastutil             = "it.unimi.dsi" % "fastutil" % fastUtilVersion
  val dropwizard           = "nl.grons" %% "metrics4-scala" % dropwizardVersion

  val scalaTest            = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"

  val pprint = ("com.lihaoyi" %% "pprint" % PPrintVersion).excludeVersionConflicting
  val fansi = ("com.lihaoyi" %% "fansi" % FansiVersion).excludeVersionConflicting
  val sourcecode = ("com.lihaoyi" %% "sourcecode" % SourceCodeVersion).excludeVersionConflicting

}
