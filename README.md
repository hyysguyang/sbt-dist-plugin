sbt-dist-plugin
===============

A sbt plugin to build a executable dist distribution files.


How build it?
==============

```bash
git clone https://github.com/hyysguyang/sbt-dist-plugin.git
cd sbt-dist-plugin && sbt publish-local
```

How to use it?
==============

```scala
  import com.lifecosys.sbt.DistPlugin
  import com.lifecosys.sbt.DistPlugin._
  val distSettings = DistPlugin.distSettings ++ Seq(
    distMainClass in Dist := "hello.Main",
    distJvmOptions in Dist := "-Xms256M -Xmx512M"
  )

  lazy val proxy = Project("Hello", file("."))
      .settings((Defaults.defaultSettings ++ distSettings):_*)

```

To generate dist files:

```bash
sbt dist
```

To clean generated dist files:

```bash
sbt dist:clean
```
