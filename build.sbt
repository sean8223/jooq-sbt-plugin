sbtPlugin := true

version := "1.7-SNAPSHOT"

organization := "sean8223"

name := "jooq-sbt-plugin"

crossScalaVersions := Seq("2.9.3", "2.10.6", "2.11.7")

val jooqVersion = "3.8.4"

libraryDependencies ++= Seq(
  "com.floreysoft" % "jmte" % "3.2.0",
  "org.jooq" % "jooq" % jooqVersion,
  "org.jooq" % "jooq-meta" % jooqVersion,
  "org.jooq" % "jooq-codegen" % jooqVersion,
  "org.slf4j" % "slf4j-api" % "1.7.2",
  "org.slf4j" % "slf4j-log4j12" % "1.7.2",
  "org.slf4j" % "jul-to-slf4j" % "1.7.2",
  "log4j" % "log4j" % "1.2.17"
)
