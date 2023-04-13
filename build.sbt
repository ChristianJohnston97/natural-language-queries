
name := "cdp"

version := "0.1"

scalaVersion := "2.12.5"

libraryDependencies ++= Seq(
  "io.cequence" %% "openai-scala-client" % "0.3.1",
  "org.typelevel" %% "cats-core"     % "2.8.0",
  "org.typelevel" %% "cats-effect"   % "3.3.14",
  "org.tpolecat" %% "doobie-core"  % "1.0.0-RC2",
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2",
  "org.postgresql"   % "postgresql" % "42.5.1"
)
