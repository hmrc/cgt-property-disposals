import sbt.*

object AppDependencies {
  private val playVersion  = "play-30"
  private val mongoVersion = "1.4.0"

  private val bootstrapVersion = "8.5.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                             %% s"bootstrap-backend-$playVersion"         % bootstrapVersion,
    "org.typelevel"                           %% "cats-core"                               % "2.10.0",
    "org.julienrf"                            %% "play-json-derived-codecs"                % "10.1.0",
    "com.github.pureconfig"                   %% "pureconfig"                              % "0.17.5",
    "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer"               % "20191001.1",
    "uk.gov.hmrc.mongo"                       %% s"hmrc-mongo-work-item-repo-$playVersion" % mongoVersion
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                %% "tax-year"                      % "4.0.0"          % scope,
    "uk.gov.hmrc"                %% s"bootstrap-test-$playVersion"  % bootstrapVersion % scope,
    "uk.gov.hmrc.mongo"          %% s"hmrc-mongo-test-$playVersion" % mongoVersion     % scope,
    "org.mockito"                %% "mockito-scala"                 % "1.17.31"        % scope,
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.14"     % "1.2.5"          % scope,
    "org.scalatestplus"          %% "scalacheck-1-17"               % "3.2.17.0"       % scope,
    "com.eclipsesource"          %% "play-json-schema-validator"    % "0.9.5"          % scope,
    "org.apache.pekko"           %% "pekko-testkit"                 % "1.0.2"          % scope
  )
}
