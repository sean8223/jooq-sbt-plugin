import java.io.{File, FileWriter}

import sbt.Keys._
import sbt._

import scala.xml.dtd.{DocType, SystemID}
import scala.xml.{Elem, Null, Text, TopScope, XML}

object JOOQPlugin extends Plugin {

  val JOOQ = config("jooq")

  // task keys

  val codegen = TaskKey[Unit]("codegen", "Generates code")

  val multiplyJooqOptions = SettingKey[Map[String,Seq[(String, String)]]]("muliply-jooq-options","multiply jooq options")

  val jooqVersion = SettingKey[String]("jooq-version", "JOOQ version.")

  val sl4jVersion = SettingKey[String]("slf4j-version", "slf4j version.")

  val jooqLogLevel = SettingKey[String]("jooq-log-level", "JOOQ log level.")

  val jooqOutputDirectory = SettingKey[File]("jooq-output-directory", "JOOQ output directory.")

  val jooqConfigFile = SettingKey[Option[File]]("jooq-config-file", "Specific config file to use in lieu of jooq-options")

  val jooqForceGen = SettingKey[Boolean]("jooq-force-gen","force generate file")

  // exported keys

  val jooqSettings = inConfig(JOOQ)(Seq(

    // add unmanaged jars to the JOOQ classpath to support proprietary
    // drivers (e.g. Oracle) that aren't available via Ivy/Maven
    managedClasspath <<= (classpathTypes, update, unmanagedJars in Compile) map { (ct, u, uj) =>
      Classpaths.managedJars(JOOQ, ct, u) ++ uj
    },

    codegen <<= (streams,
      baseDirectory,
      managedClasspath in JOOQ,
      jooqOutputDirectory,
      jooqLogLevel,
      jooqConfigFile,
      multiplyJooqOptions) map {
      case (s, bd, mcp, jod, jll, jcf,mjo) =>
        mjo.foreach{
          case (optionName,jo)=>
            println("generating for :"+optionName)
            println("options :\n"+jo.map(o => s"${o._1} -> ${o._2}").mkString("\n"))
            executeJooqCodegen(s.log, bd, mcp, jod, jo, jll, jcf)
        }
    }

  )) ++ Seq(

    jooqVersion := "3.5.3",

    sl4jVersion := "1.7.10",

    multiplyJooqOptions := Map.empty,

    jooqLogLevel := "info",

    jooqOutputDirectory <<= (sourceManaged in Compile)(_ / "java"),

    jooqConfigFile := None,

    jooqForceGen := true,

    sourceGenerators in Compile <+= (streams,
      baseDirectory,
      managedClasspath in JOOQ,
      jooqOutputDirectory,
      jooqLogLevel,
      jooqConfigFile,
      jooqForceGen,
      multiplyJooqOptions) map {
      case (s, bd, mcp, jod,jll, jcf,jforceGen,mjo) =>
        mjo.foldLeft(Seq.empty[File]){
          case (seq,(optionName,jo)) =>
            println("gathering option for :"+optionName)
            seq ++ executeJooqCodegenIfOutOfDate(s.log, bd, mcp, jod, jo, jll, jcf,jforceGen)
        }
    },

    libraryDependencies <++= (scalaVersion, jooqVersion, sl4jVersion) apply {
      (sv, jv, sl4jv) => {
        Seq("org.jooq" % "jooq" % jv % JOOQ.name,
          "org.jooq" % "jooq" % jv, // also add this to the project's compile configuration
          "org.jooq" % "jooq-meta" % jv % JOOQ.name,
          "org.jooq" % "jooq-codegen" % jv % JOOQ.name,
          "org.slf4j" % "slf4j-api" % sl4jv % JOOQ.name,
          "org.slf4j" % "slf4j-log4j12" % sl4jv % JOOQ.name,
          "org.slf4j" % "jul-to-slf4j" % sl4jv % JOOQ.name,
          "log4j" % "log4j" % "1.2.17" % JOOQ.name)
      }
    },

    ivyConfigurations += JOOQ

  )

  private def getOrGenerateJooqConfig(log: Logger,
                                      outputDirectory: File,
                                      options: Seq[(String, String)],
                                      jooqConfigFile: Option[File]) = {
    jooqConfigFile.getOrElse(generateJooqConfig(log, outputDirectory, options))
  }

  private def generateJooqConfig(log: Logger, outputDirectory: File, options: Seq[(String, String)]) = {
    val tmp = File.createTempFile("jooq-config", ".xml")
    tmp.deleteOnExit()
    val fw = new FileWriter(tmp)
    try {
      val replaced = Seq("generator.target.directory" -> outputDirectory.getAbsolutePath) ++ options.filter { kv => kv._1 != "generator.target.directory" }
      val xml = replaced.foldLeft(<configuration/>) {
        (xml, kv) => xmlify(kv._1.split("\\."), kv._2, xml)
      }
      XML.save(tmp.getAbsolutePath, xml, "UTF-8", xmlDecl = true)
    }
    finally {
      fw.close()
    }
    log.debug("Wrote JOOQ configuration to " + tmp.getAbsolutePath)
    tmp
  }

  private def xmlify(key: Seq[String], value: String, parent: Elem): Elem = {
    // convert a sequence of strings representing a XML path into a sequence
    // of nodes, and merge it in to the specified parent, reusing any nodes
    // that already exist, e.g. "value" at Seq("foo", "bar", "baz") becomes
    // <foo><bar><baz>value</baz></bar></foo>
    key match {
      case Seq(first) =>
        val child = parent.child ++ Elem(null, first, Null, TopScope, false,Text(value))

        Elem(null, parent.label, Null, TopScope, child.isEmpty,child:_*)
      case Seq(first, rest@_*) =>
        val (pre, post) = parent.child.span {
          _.label != first
        }
        post match {
          case Nil => xmlify(key, value, Elem(null, parent.label, Null, TopScope, false,parent.child ++ Elem(null, first, Null, TopScope,minimizeEmpty = true): _*))
          case _ => Elem(null, parent.label, Null, TopScope,false, pre ++ xmlify(rest, value, Elem(null, post.head.label, Null, TopScope,false, post.head.child: _*)) ++ post.tail: _*)
        }
    }
  }

  private def generateLog4jConfig(log: Logger, logLevel: String) = {
    // shunt any messages at warn and higher to stderr, everything else to
    // stdout, thanks to http://stackoverflow.com/questions/8489551/logging-error-to-stderr-and-debug-info-to-stdout-with-log4j
    val tmp = File.createTempFile("log4j", ".xml")
    tmp.deleteOnExit()
    val configuration =
      <log4j:configuration>
        <appender name="stderr" class="org.apache.log4j.ConsoleAppender">
          <param name="threshold" value="warn"/>
          <param name="target" value="System.err"/>
          <layout class="org.apache.log4j.PatternLayout">
            <param name="ConversionPattern" value="%m%n"/>
          </layout>
        </appender>
        <appender name="stdout" class="org.apache.log4j.ConsoleAppender">
          <param name="threshold" value="debug"/>
          <param name="target" value="System.out"/>
          <layout class="org.apache.log4j.PatternLayout">
            <param name="ConversionPattern" value="%m%n"/>
          </layout>
          <filter class="org.apache.log4j.varia.LevelRangeFilter">
            <param name="LevelMin" value="debug"/>
            <param name="LevelMax" value="info"/>
          </filter>
        </appender>
        <root>
          <priority value={logLevel}></priority>
          <appender-ref ref="stderr"/>
          <appender-ref ref="stdout"/>
        </root>
      </log4j:configuration>
    XML.save(tmp.getAbsolutePath, configuration, "UTF-8", xmlDecl = true, DocType("log4j:configuration", SystemID("log4j.dtd"), Nil))
    log.debug("Wrote log4j configuration to " + tmp.getAbsolutePath)
    tmp
  }

  private def generateClasspathArgument(log: Logger,
                                        classpath: Seq[Attributed[File]],
                                        jooqConfigFile: File) = {
    val cp = (classpath.map {
      _.data.getAbsolutePath
    } :+ jooqConfigFile.getParentFile.getAbsolutePath).mkString(System.getProperty("path.separator"))
    log.debug("Classpath is " + cp)
    cp
  }

  private def executeJooqCodegenIfOutOfDate(log: Logger,
                                            baseDirectory: File,
                                            managedClasspath: Seq[Attributed[File]],
                                            outputDirectory: File,
                                            options: Seq[(String, String)],
                                            logLevel: String,
                                            jooqConfigFile: Option[File],
                                             forceGen:Boolean) = {
    // lame way of detecting whether or not code is out of date, user can always
    // run jooq:codegen manually to force regeneration
    val files = (outputDirectory ** "*.java").get
    if (files.isEmpty)
      executeJooqCodegen(log, baseDirectory, managedClasspath, outputDirectory, options, logLevel, jooqConfigFile)
    else files
  }

  private def executeJooqCodegen(log: Logger,
                                 baseDirectory: File,
                                 managedClasspath: Seq[Attributed[File]],
                                 outputDirectory: File,
                                 options: Seq[(String, String)],
                                 logLevel: String,
                                 jooqConfigFile: Option[File]):Seq[File] = {
    val jcf = getOrGenerateJooqConfig(log, outputDirectory, options, jooqConfigFile)

    log.info("Using jooq config " + jcf.getAbsolutePath)
    val log4jConfig = generateLog4jConfig(log, logLevel)
    val classpathArgument = generateClasspathArgument(log, managedClasspath, jcf)
    val cmdLine = Seq("java", "-classpath", classpathArgument, "-Dlog4j.configuration=" + log4jConfig.toURI.toURL, "org.jooq.util.GenerationTool", "/" + jcf.getName)
    log.debug("Command line is " + cmdLine.mkString(" "))
    val rc = Process(cmdLine, baseDirectory) ! log
    rc match {
      case 0 => ;
      case x => sys.error("Failed with return code: " + x)
    }
    (outputDirectory ** "*.java").get
  }

}
