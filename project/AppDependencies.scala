import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.6.0"

  val compile: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-core"                 % "2.12.0",
    "uk.gov.hmrc"   %% "bootstrap-backend-play-30" % bootstrapVersion,
    "com.beachape"  %% "enumeratum-play-json"      % "1.8.2"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30" % bootstrapVersion,
    "org.mockito"        % "mockito-core"           % "5.11.0",
    "org.scalatestplus" %% "mockito-4-11"           % "3.2.18.0"
  ).map(_ % Test)

  val it: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test
  )

}
