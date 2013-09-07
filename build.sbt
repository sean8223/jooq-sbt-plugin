sbtPlugin := true

version := "1.0"

organization := "sean8223"

name := "jooq-sbt-plugin"

crossScalaVersions := Seq("2.9.1", "2.9.2", "2.10.0", "2.10.1", "2.10.2")

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/Dropbox/Dev/repository/releases")))
