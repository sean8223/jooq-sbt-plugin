sbtPlugin := true

version := "1.6.0"

organization := "sean8223"

name := "jooq-sbt-plugin"

crossScalaVersions := Seq("2.10.5","2.11.6")

scalaVersion := crossScalaVersions.value.head

libraryDependencies ++= {
  if(scalaVersion.value.startsWith("2.11")){
    Seq( "org.scala-lang.modules" %% "scala-xml" % "1.0.4")
  }else{
    Seq()
  }
}
