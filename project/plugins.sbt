resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.jcenterRepo
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("uk.gov.hmrc"               % "sbt-auto-build"     % "3.5.0")
addSbtPlugin("uk.gov.hmrc"               % "sbt-distributables" % "2.1.0")
addSbtPlugin("com.typesafe.play"         % "sbt-plugin"         % "2.8.8")
addSbtPlugin("org.wartremover"           % "sbt-wartremover"    % "3.0.5")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"       % "2.4.3")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix"       % "0.9.30")
addSbtPlugin("org.scoverage"             % "sbt-scoverage"      % "1.9.3")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"       % "0.1.20")
addSbtPlugin("uk.gov.hmrc"               % "sbt-bobby"          % "3.5.0")
