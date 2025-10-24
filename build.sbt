import org.typelevel.scalacoptions.ScalacOptions
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "pillar2-external-test-stub"

ThisBuild / scalaVersion := "3.3.5"
ThisBuild / majorVersion := 0

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(CodeCoverageSettings.settings*)
  .settings(
    ScoverageKeys.coverageExcludedFiles := ".*models.*;.*package.*;.*config.*;.*helpers.*",
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    PlayKeys.playDefaultPort := 10055,
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // Suppress warnings in generated routes files
    scalacOptions ++= Seq(
      "-Wconf:src=routes/.*:s", // Suppress warnings in route files
      "-Wconf:msg=parameter.*is never used:s", // Suppress unused parameter warnings
      "-Wconf:msg=Flag.*set repeatedly:s",
      "-Wconf:msg=Setting -Wunused set to all redundantly:s",
      "-Wconf:msg=Unreachable case except for null.*:s",
      "-Werror" // Treat all other warnings as errors
    )
  )
  .settings(
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / unmanagedSourceDirectories := (Test / baseDirectory)(base => Seq(base / "test", base / "test-common")).value,
    tpolecatExcludeOptions ++= Set(ScalacOptions.warnNonUnitStatement)
  )

addCommandAlias("prePrChecks", ";scalafmtCheckAll;scalafmtSbtCheck;scalafixAll --check")
addCommandAlias("lint", ";scalafmtAll;scalafmtSbt;scalafixAll")

lazy val it = project
  .enablePlugins(play.sbt.PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(
    tpolecatExcludeOptions ++= Set(
      ScalacOptions.warnNonUnitStatement
    )
  )
  .settings(libraryDependencies ++= AppDependencies.it)

inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)
