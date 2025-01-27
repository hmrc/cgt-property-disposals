import sbt.*

object AppDependencies {
  private val playVersion  = "play-30"
  private val mongoVersion = "2.3.0"
  private val pekkoVersion = "1.1.1"

  private val bootstrapVersion = "9.5.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                             %% s"bootstrap-backend-$playVersion"         % bootstrapVersion,
    "org.typelevel"                           %% "cats-core"                               % "2.12.0",
    "org.julienrf"                            %% "play-json-derived-codecs"                % "11.0.0",
    "com.github.pureconfig"                   %% "pureconfig"                              % "0.17.8",
    "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer"               % "20240325.1",
    "org.apache.pekko"                        %% "pekko-actor-typed"                       % pekkoVersion,
    "org.apache.pekko"                        %% "pekko-protobuf-v3"                       % pekkoVersion,
    "org.apache.pekko"                        %% "pekko-serialization-jackson"             % pekkoVersion,
    "org.apache.pekko"                        %% "pekko-stream"                            % pekkoVersion,
    "uk.gov.hmrc.mongo"                       %% s"hmrc-mongo-work-item-repo-$playVersion" % mongoVersion,
    "com.openhtmltopdf"                        % "openhtmltopdf-pdfbox"                    % "1.0.10"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                %% "tax-year"                      % "5.0.0"          % scope,
    "uk.gov.hmrc"                %% s"bootstrap-test-$playVersion"  % bootstrapVersion % scope,
    "uk.gov.hmrc.mongo"          %% s"hmrc-mongo-test-$playVersion" % mongoVersion     % scope,
    "org.mockito"                %% "mockito-scala"                 % "1.17.37"        % scope,
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.18"     % "1.3.2"          % scope,
    "org.scalatestplus"          %% "scalacheck-1-18"               % "3.2.19.0"       % scope,
    "org.apache.pekko"           %% "pekko-testkit"                 % pekkoVersion     % scope
  )
}
