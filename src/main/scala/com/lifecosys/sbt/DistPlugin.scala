/**
 * Copyright (C) 2011-2012 Typesafe <http://typesafe.com/>
 */

package com.lifecosys.sbt

import sbt._
import sbt.Keys._
import sbt.Load.BuildStructure
import sbt.classpath.ClasspathUtilities
import sbt.Project.Initialize
import java.io.File

/**
 * Original from akka sbt plugin.
 */
object DistPlugin extends Plugin {

  case class DistConfig(outputDirectory: File, configSourceDirs: Seq[File], distZipName: String, distJvmOptions: String,
                        distMainClass: String, libFilter: File ⇒ Boolean, additionalFiles: Seq[File], additionalLibs: Seq[File])

  val Dist = config("dist") extend (Runtime)
  val dist = TaskKey[File]("dist", "Builds an runtime directory")
  val distClean = TaskKey[Unit]("clean", "Removes runtime directory")

  val outputDirectory = SettingKey[File]("output-directory")
  val distZipName = TaskKey[String]("dist-zip-name", "name of dist zip file")
  val configSourceDirs = TaskKey[Seq[File]]("config-source-directories", "Configuration files are copied from these directories")
  val additionalFiles = TaskKey[Seq[File]]("additional-files", "Additional files copied to base directory.")

  val distJvmOptions = SettingKey[String]("dist-jvm-options", "JVM parameters to use in start script")
  val distMainClass = SettingKey[String]("dist-main-class", "main class to use in start script")

  val libFilter = SettingKey[File ⇒ Boolean]("lib-filter", "Filter of dependency jar files")
  val additionalLibs = TaskKey[Seq[File]]("additional-libs", "Additional dependency jar files")
  val distConfig = TaskKey[DistConfig]("dist-config")

  val distNeedsPackageBin = dist <<= dist.dependsOn(packageBin in Compile)

  lazy val distSettings: Seq[Setting[_]] =
    inConfig(Dist)(Seq(
      dist <<= packageBin,
      packageBin <<= distTask,
      distClean <<= distCleanTask,
      dependencyClasspath <<= (dependencyClasspath in Runtime),
      unmanagedResourceDirectories <<= (unmanagedResourceDirectories in Runtime),
      outputDirectory <<= target / "dist",
      distZipName <<= (name, version) map { (n, v) ⇒ "%s-%s-dist.zip".format(n.toLowerCase, v) },
      configSourceDirs <<= defaultConfigSourceDirs,
      distJvmOptions := "-Xms1024M -Xmx1024M -Xss1M -XX:MaxPermSize=256M -XX:+UseParallelGC",
      distMainClass := "akka.kernel.Main",
      libFilter := { f ⇒ true },
      additionalFiles <<= defaultAdditionalFiles,
      additionalLibs := Seq.empty[File],
      distConfig <<= (outputDirectory, configSourceDirs, distZipName, distJvmOptions, distMainClass, libFilter, additionalFiles, additionalLibs) map DistConfig)) ++
      Seq(dist <<= (dist in Dist), distNeedsPackageBin)

  private def distTask: Initialize[Task[File]] =
    (thisProject, distConfig, sourceDirectory, crossTarget, dependencyClasspath, allDependencies, buildStructure, state) map {
      (project, conf, src, targets, cp, allDeps, buildStruct, st) ⇒
        val distBinPath = conf.outputDirectory / "bin"
        val distConfigPath = conf.outputDirectory / "config"
        val distLibPath = conf.outputDirectory / "lib"

        val subProjectDependencies: Set[SubProjectInfo] = allSubProjectDependencies(project, buildStruct, st)

        val log = st.log

        log.info("Creating distribution %s ..." format conf.outputDirectory)
        IO.createDirectory(conf.outputDirectory)
        Scripts(conf.distJvmOptions, conf.distMainClass).writeScripts(distBinPath)
        copyDirectories(conf.configSourceDirs, distConfigPath)
        copyJars(targets, distLibPath)

        copyFiles(libFiles(cp, conf.libFilter), distLibPath)
        copyFiles(conf.additionalLibs, distLibPath)
        for (subProjectDependency ← subProjectDependencies) {
          val subTarget = subProjectDependency.target
          EvaluateTask(buildStruct, packageBin in Compile, st, subProjectDependency.projectRef)
          copyJars(subTarget, distLibPath)
        }
        copyFiles(conf.additionalFiles, conf.outputDirectory)

        log.info("Distribution directory created.")

        val zipFile: File = targets / conf.distZipName
        val files = (conf.outputDirectory ***) x rebase(conf.outputDirectory, "")
        log.debug("Creating zip file from:" + files.mkString(", "))
        IO.zip(files, zipFile)
        log.info("Created distribution zip file success: " + zipFile)

        conf.outputDirectory
    }

  private def distCleanTask: Initialize[Task[Unit]] =
    (distConfig, crossTarget, streams) map {
      (conf, targets, s) ⇒
        val log = s.log
        val zipFile = targets / conf.distZipName
        log.info("Deleting " + zipFile)
        IO.delete(zipFile)
        log.info("Cleaning " + conf.outputDirectory)
        IO.delete(conf.outputDirectory)
    }

  def isKernelProject(dependencies: Seq[ModuleID]): Boolean = true

  private def defaultConfigSourceDirs = (sourceDirectory, unmanagedResourceDirectories) map {
    (src, resources) ⇒
      Seq(src / "main" / "config") ++ resources
  }

  private def defaultAdditionalFiles = (baseDirectory, baseDirectory) map {
    (b, c) ⇒ includeAdditionalFiles(b)
  }

  def includeAdditionalFiles(base: File): Seq[File] = base.listFiles(new FileFilter {
    def accept(file: File): Boolean = file.isFile && !file.getName.startsWith(".")
  }).toSeq

  private case class Scripts(jvmOptions: String, mainClass: String) {

    def writeScripts(to: File) = {
      scripts.map {
        script ⇒
          val target = new File(to, script.name)
          IO.write(target, script.contents)
          setExecutable(target, script.executable)
      }.foldLeft(None: Option[String])(_ orElse _)
    }

    private case class DistScript(name: String, contents: String, executable: Boolean)

    private def scripts = Set(DistScript("start", distShScript, true),
      DistScript("start.bat", distBatScript, true))

    private def distShScript =
      ("#!/bin/sh\n\n" +
        "APP_HOME=\"$(cd \"$(cd \"$(dirname \"$0\")\"; pwd -P)\"/..; pwd)\"\n" +
        "APP_CLASSPATH=\"$APP_HOME/config:$APP_HOME/lib/*\"\n" +
        "JAVA_OPTS=\"%s\"\n\n" +
        "java $JAVA_OPTS -cp \"$APP_CLASSPATH\" -Dapp.home=\"$APP_HOME\" %s \"$@\"\n").format(jvmOptions, mainClass)

    private def distBatScript =
      ("@echo off\r\n\r\n" +
        "set APP_HOME=%%~dp0..\r\n" +
        "set APP_CLASSPATH=%%APP_HOME%%\\config;%%APP_HOME%%\\lib\\*\r\n" +
        "set JAVA_OPTS=%s\r\n\r\n" +
        "java %%JAVA_OPTS%% -cp \"%%APP_CLASSPATH%%\" -Dapp.home=\"%%APP_HOME%%\" %s %%*\r\n").format(jvmOptions, mainClass)

    private def setExecutable(target: File, executable: Boolean): Option[String] = {
      val success = target.setExecutable(executable, false)
      if (success) None else Some("Couldn't set permissions of " + target)
    }
  }

  private def copyDirectories(fromDirs: Seq[File], to: File) = {
    IO.createDirectory(to)
    for (from ← fromDirs) {
      IO.copyDirectory(from, to)
    }
  }

  private def copyJars(fromDir: File, toDir: File) = {
    val jarFiles = fromDir.listFiles.filter(f ⇒
      f.isFile &&
        f.name.endsWith(".jar") &&
        !f.name.contains("-sources.jar") &&
        !f.name.contains("-javadoc.jar") &&
        !f.name.contains("-test.jar"))

    copyFiles(jarFiles, toDir)
  }

  private def copyFiles(files: Seq[File], toDir: File) = {
    for (f ← files) {
      IO.copyFile(f, new File(toDir, f.getName))
    }
  }

  private def libFiles(classpath: Classpath, libFilter: File ⇒ Boolean): Seq[File] = {
    val (libs, directories) = classpath.map(_.data).partition(ClasspathUtilities.isArchive)
    libs.map(_.asFile).filter(libFilter)
  }

  private def includeProject(project: ResolvedProject, parent: ResolvedProject): Boolean = {
    parent.uses.exists {
      case ProjectRef(uri, id) ⇒ id == project.id
      case _                   ⇒ false
    }
  }

  private def allSubProjectDependencies(project: ResolvedProject, buildStruct: BuildStructure, state: State): Set[SubProjectInfo] = {
    val buildUnit = buildStruct.units(buildStruct.root)
    val uri = buildStruct.root
    val allProjects = buildUnit.defined.map {
      case (id, proj) ⇒ (ProjectRef(uri, id) -> proj)
    }

    val subProjects: Seq[SubProjectInfo] = allProjects.collect {
      case (projRef, proj) if includeProject(proj, project) ⇒ projectInfo(projRef, proj, buildStruct, state, allProjects)
    }.toList

    val allSubProjects = subProjects.map(_.recursiveSubProjects).flatten.toSet
    allSubProjects
  }

  private def projectInfo(projectRef: ProjectRef, project: ResolvedProject, buildStruct: BuildStructure, state: State,
                          allProjects: Map[ProjectRef, ResolvedProject]): SubProjectInfo = {

    def optionalSetting[A](key: SettingKey[A]) = key in projectRef get buildStruct.data

    def setting[A](key: SettingKey[A], errorMessage: ⇒ String) = {
      optionalSetting(key) getOrElse {
        state.log.error(errorMessage);
        throw new IllegalArgumentException()
      }
    }

    val subProjects = allProjects.collect {
      case (projRef, proj) if includeProject(proj, project) ⇒ projectInfo(projRef, proj, buildStruct, state, allProjects)
    }.toList

    val target = setting(Keys.crossTarget, "Missing crossTarget directory")
    SubProjectInfo(projectRef, target, subProjects)
  }

  private case class SubProjectInfo(projectRef: ProjectRef, target: File, subProjects: Seq[SubProjectInfo]) {

    def recursiveSubProjects: Set[SubProjectInfo] = {
      val flatSubProjects = for {
        x ← subProjects
        y ← x.recursiveSubProjects
      } yield y

      flatSubProjects.toSet + this
    }

  }

}
