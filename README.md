This is an SBT plugin that provides an interface to the JOOQ code generation tool
(<http://www.jooq.org>). The plugin is compatible with SBT 0.11.3+ and Scala 2.9.1+.

The current version of the plugin is *1.2*


Quick Start
===========

1. Add jooq-sbt-plugin to your `project/plugins.sbt`:
        
        resolvers += "sean8223 Releases" at "https://github.com/sean8223/repository/raw/master/releases"
        addSbtPlugin("sean8223" %% "jooq-sbt-plugin" % CURRENT_PLUGIN_VERSION) // see above
		
2. In your `build.sbt`, do the following:

   * Inject the plugin settings into your build definition:
   
            seq(jooqSettings:_*)
			
     This will also add the JOOQ libraries to your application's compile
	 `libraryDependencies`.
			
   * Add your database driver to your list of `libraryDependencies` with "jooq" scope:
   
            libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.22" % "jooq"
			
   * Configure options for your environment:
   
             jooqOptions := Seq("jdbc.driver" -> "com.mysql.jdbc.Driver",
                                "jdbc.url" -> "jdbc:mysql://localhost:3306/fnord",
                                "jdbc.user" -> "fnord",
                                "jdbc.password" -> "fnord",
                                "generator.database.name" -> "org.jooq.util.mysql.MySQLDatabase",
                                "generator.database.inputSchema" -> "fnord",
                                "generator.target.packageName" -> "com.myproject.jooq")
			 
			 
Settings
========

The plugin exposes several settings:

* *jooq-options* (`jooqOptions` in build.sbt): a `Seq[Tuple2[String, String]]`
  containing configuration properties for the JOOQ code generator. These will 
  be transformed into paths into the XML configuration file; for example, the option
  `"jdbc.driver" -> "com.mysql.jdbc.Driver"` will be merged into the `<configuration>`
  document as:

            <configuration>
			   <jdbc>
			     <driver>com.mysql.jdbc.Driver</driver>
			   </jdbc>
            </configuration>

  Refer to <http://www.jooq.org/doc/3.0/manual/code-generation/codegen-configuration/>
  for a complete description of JOOQ's configuration options. 

* *jooq-output-directory* (`jooqOutputDirectory` in build.sbt): a `File`
  indicating where JOOQ should deposit the source files it generates. By
  default, this is set to the value of `compile:source-managed` + "/java"
  (usually target/_scala-version_/src_managed/main/java), but it can
  be changed to suit your project layout as needed.

* *jooq-version* (`jooqVersion` in build.sbt): a `String` indicating the version
  of JOOQ to use. The JOOQ libraries at this version will also be imported into your
  project's compile scope.

* *jooq-log-level* (`jooqLogLevel` in build.sbt): a `String` controlling the
  the verbosity of code generator's logging. It defaults to "info", which 
  still produces a fair amount of output. Setting it to "error" will effectively
  silence it, except in the case of problems. Other values include "warn" and "debug".


Tasks
=====

And provides a single task:

* *jooq:codegen*: Runs the code generator.

The plugin also attaches to the `compile:compile` task (by way of 
`compile-source-generators`) and will run prior to compile if doesn't see any
`*.java` files in the directory indicated by `jooq-output-directory` (e.g. if
you run `clean`).


History
=======

* 1.0: Initial release
* 1.1: Fixed error in which `jooqOutputDirectory` was incorrectly being set to `sourceManaged` rather than `sourceManaged in Compile`
* 1.2: Added `unmanagedJars in Compile` to the `managedClasspath` used by the plugin to facilitate use of proprietary drivers that might not be accessible via Ivy/Maven repos.
