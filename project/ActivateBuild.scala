import sbt._
import Keys._

object ActivateBuild extends Build {

    /* Core dependencies */
    val javassist = "org.javassist" % "javassist" % "3.20.0-GA"
    val radonStm = "net.fwbrasil" %% "radon-stm" % "1.7"
    val smirror = "net.fwbrasil" %% "smirror" % "0.9"
    val guava = "com.google.guava" % "guava" % "19.0"
    val objenesis = "org.objenesis" % "objenesis" % "2.1"
    val jug = "com.fasterxml.uuid" % "java-uuid-generator" % "3.1.3"
    val reflections = "org.reflections" % "reflections" % "0.9.10" exclude ("javassist", "javassist") exclude ("dom4j", "dom4j")
    val grizzled = "org.clapper" %% "grizzled-slf4j" % "1.0.2"
    val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.0"
    val jodaTime = "joda-time" % "joda-time" % "2.9.1"
    val jodaConvert = "org.joda" % "joda-convert" % "1.8.1"
    val gremlin = "com.tinkerpop.gremlin" % "gremlin-java" % "2.4.0"
    val xstream = "com.thoughtworks.xstream" % "xstream" % "1.4.7" exclude ("xpp3", "xpp3_min")
    val jettison = "org.codehaus.jettison" % "jettison" % "1.3.4"
    val findBugs = "com.google.code.findbugs" % "jsr305" % "2.0.1"
    val kryo = "com.esotericsoftware.kryo" % "kryo" % "3.0.3"
    val cassandraDriver = "com.datastax.cassandra" % "cassandra-driver-core" % "1.0.5"

    val postgresql = "org.postgresql" % "postgresql" % "9.4-1107-jdbc42"
    val hirakiCP = "com.zaxxer" % "HikariCP" % "2.4.3"
    val h2 = "com.h2database" % "h2" % "1.4.190"
    val derby = "org.apache.derby" % "derby" % "10.10.1.1"
    val jtds = "net.sourceforge.jtds" % "jtds" % "1.3.1"

    val gfork = "org.gfork" % "gfork" % "0.11"

    /* Mongo */
    val mongoDriver = "org.mongodb" % "mongo-java-driver" % "2.11.4"
    val mongoScala = "org.mongodb.scala" %% "mongo-scala-driver" % "1.1.0"

    lazy val activate =
        Project(
            id = "activate",
            base = file("."),
            aggregate = Seq(activateCore,
                activateJdbc, activateMongo, activateSprayJson, activateMongoAsync),
            settings = commonSettings
        )

    lazy val activateCore =
        Project(
            id = "activate-core",
            base = file("activate-core"),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(javassist, radonStm, objenesis, jug,
                        reflections, grizzled, logbackClassic, jodaTime, jodaConvert,
                        smirror, xstream, jettison, findBugs, guava)
            )
        )

    lazy val activateJdbc =
        Project(
            id = "activate-jdbc",
            base = file("activate-jdbc"),
            dependencies = Seq(activateCore),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(hirakiCP)
            )
        )

    lazy val activateMongo =
        Project(
            id = "activate-mongo",
            base = file("activate-mongo"),
            dependencies = Seq(activateCore),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(mongoDriver)
            )
        )

    val reactivemongo = "org.reactivemongo" %% "reactivemongo" % "0.11.9"

    lazy val activateMongoAsync =
        Project(
            id = "activate-mongo-async",
            base = file("activate-mongo-async"),
            dependencies = Seq(activateCore, activateMongo),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(reactivemongo)
            )
        )

    val sprayJson = "io.spray" %% "spray-json" % "1.3.2"

    lazy val activateSprayJson =
        Project(
            id = "activate-spray-json",
            base = file("activate-spray-json"),
            dependencies = Seq(activateCore),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(sprayJson)
            )
        )

    val junit = "junit" % "junit" % "4.11"
    val specs2 = "org.specs2" %% "specs2" % "2.4.2"

    // lazy val activateTest =
    //     Project(id = "activate-test",
    //         base = file("activate-test"),
    //         dependencies = Seq(activateCore, activateJdbc % "test",
    //             activateMongo % "test", activateSprayJson % "test",
    //             activateMongoAsync % "test"),
    //         settings = commonSettings ++ Seq(
    //             libraryDependencies ++=
    //                 Seq(junit % "test", specs2 % "test", postgresql % "test",
    //                     h2 % "test", gfork % "test", jtds % "test"),
    //             scalacOptions ++= Seq("-Xcheckinit")
    //         )
    //     )

    /* Resolvers */
    val customResolvers = Seq(
        "Maven" at "http://repo1.maven.org/maven2/",
        "Typesafe" at "http://repo.typesafe.com/typesafe/releases",
        "Local Maven Repository" at "file://" + Path.userHome + "/.m2/repository",
        "fwbrasil.net" at "http://fwbrasil.net/maven/",
        "spray" at "http://repo.spray.io/",
        "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
        "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
        "Alfesco" at "https://maven.alfresco.com/nexus/content/groups/public/"
    )

    def commonSettings =
        Defaults.defaultSettings ++ Seq(
            organization := "net.fwbrasil",
            version := "1.7",
            scalaVersion := "2.11.7",
            javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
            scalacOptions := Seq("-deprecation", "-unchecked", "-feature", "-language:_", "-encoding", "utf8"),
            publishMavenStyle := true,
            // publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository"))),
            // publishTo := Option(Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as("maven") withPermissions("0644")),
            publishTo <<= version { v: String =>
                val nexus = "https://oss.sonatype.org/"
                val fwbrasil = "http://fwbrasil.net/maven/"
                if (v.trim.endsWith("SNAPSHOT"))
                    Option(Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as ("maven") withPermissions ("0644"))
                else
                    Some("releases" at nexus + "service/local/staging/deploy/maven2")
            },
            resolvers ++= customResolvers,
            publishMavenStyle := true,
            publishArtifact in Test := false,
            pomIncludeRepository := { x => false },
            pomExtra := (
                <url>http://github.com/fwbrasil/activate/</url>
                <licenses>
                    <license>
                        <name>LGPL</name>
                        <url>https://github.com/fwbrasil/activate/blob/master/LICENSE-LGPL</url>
                        <distribution>repo</distribution>
                    </license>
                </licenses>
                <scm>
                    <url>git@github.com:fwbrasil/activate.git</url>
                    <connection>scm:git:git@github.com:fwbrasil/activate.git</connection>
                </scm>
                <developers>
                    <developer>
                        <id>fwbrasil</id>
                        <name>Flavio W. Brasil</name>
                        <url>http://fwbrasil.net</url>
                    </developer>
                </developers>
            ),
            compileOrder := CompileOrder.JavaThenScala,
            parallelExecution in Test := false
        )
}
