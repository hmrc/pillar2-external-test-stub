import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.11.0"
  private val hmrcMongoVersion = "2.5.0"

  val compile: Seq[ModuleID] = Seq(
    "org.typelevel"     %% "cats-core"                 % "2.13.0",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "com.beachape"      %% "enumeratum-play-json"      % "1.8.2"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "org.mockito"        % "mockito-core"            % "5.15.2",
    "org.scalatestplus" %% "mockito-4-11"            % "3.2.18.0"
  ).map(_ % Test)

  val it: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test
  )

}
