import org.typelevel.scalacoptions.ScalacOptions
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "pillar2-external-test-stub"

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / majorVersion := 0

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    ScoverageKeys.coverageExcludedFiles :=
      "<empty>;com.kenshoo.play.metrics.*;.*definition.*;prod.*;testOnlyDoNotUseInAppConf.*;.*stubs.*;.*models.*;" +
        "app.*;.*BuildInfo.*;.*Routes.*;.*repositories.*;.*package.*;.*controllers.test.*;.*services.test.*;.*metrics.*",
    ScoverageKeys.coverageMinimumStmtTotal := 80,
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
      "-Werror" // Treat all other warnings as errors
    )
  )
  .settings(
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / unmanagedSourceDirectories := (Test / baseDirectory)(base => Seq(base / "test", base / "test-common")).value,
    Test / unmanagedResourceDirectories := Seq(baseDirectory.value / "test-resources"),
    tpolecatExcludeOptions ++= Set(
      ScalacOptions.warnNonUnitStatement
    )
  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings *)

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
