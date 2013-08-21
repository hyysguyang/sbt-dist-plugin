sbt-dist-plugin
===============

A sbt plugin to build a executable dist distribution files.


### How to build it?

```bash
git clone https://github.com/hyysguyang/sbt-dist-plugin.git
cd sbt-dist-plugin && sbt publish-local
```

### How to use it?

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


### Credits

The original code for this project based upon [akka] sbt plugin.


### License

_sbt-dist-plugin_ is licensed under [APL 2.0].


  [akka]: https://github.com/akka/akka