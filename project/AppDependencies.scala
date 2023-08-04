import sbt.*

object AppDependencies {

  val akkaVersion  = "2.6.19"
  val playVersion  = "play-28"
  val mongoVersion = "0.68.0"

  private val bootstrapPlay28Version = "5.25.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                             %% s"bootstrap-backend-$playVersion"         % bootstrapPlay28Version,
    "org.typelevel"                           %% "cats-core"                               % "2.6.0",
    "org.julienrf"                            %% "play-json-derived-codecs"                % "10.0.2",
    "com.github.kxbmap"                       %% "configs"                                 % "0.6.1",
    "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer"               % "20191001.1",
    "uk.gov.hmrc"                             %% "time"                                    % "3.18.0",
    "uk.gov.hmrc.mongo"                       %% s"hmrc-mongo-work-item-repo-$playVersion" % mongoVersion
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                %% s"bootstrap-test-$playVersion" % bootstrapPlay28Version % scope,
    "org.mockito"                %% "mockito-scala"                % "1.17.12"              % scope,
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.14"    % "1.2.5"                % scope,
    "org.scalatestplus"          %% "scalatestplus-scalacheck"     % "3.1.0.0-RC2"          % scope,
    "com.eclipsesource"          %% "play-json-schema-validator"   % "0.9.5"                % scope,
    "com.typesafe.akka"          %% "akka-testkit"                 % akkaVersion            % scope
  )
}
