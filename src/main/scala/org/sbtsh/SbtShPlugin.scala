package org.sbtsh

import sbt._
import sbt.CommandSupport._
import sbt.Keys._


trait SbtShKeys {
  val localRepo = SettingKey[File]("local-repo")
  val githubRepo = SettingKey[String]("github-repo")
  val createRepo = TaskKey[Unit]("create-repo")
  val publishToGithubRepo = TaskKey[Unit]("publish-to-github-repo")
  val createPublishScripts = TaskKey[Unit]("create-publish-scripts")
}

object SbtShPlugin extends Plugin with SbtShKeys {

  override lazy val settings = Seq(Keys.commands += shCommand) ++ additionalSettings

  def shCommand = Command.args("sh", "<shell command>") {
    (state, args) =>
      val ret = args.mkString(" ") !

      state
  }

  /*#!/bin/sh
cd $localMavenRepo
git add .
git commit -am "publish by script"
git pull --rebase    origin gh-pages
git rebase origin/gh-pages
git push origin gh-pages*/

  def createCreateNewRepoScript(s: TaskStreams) = {
    val filename = "createNewRepo.sh"
    val content =
      """#!/bin/sh
        |cd
        |mkdir $localMavenRepo
        |cd $localMavenRepo
        |if [ -d .git ]
        |then true
        |else
        | git init
        | git add .
        | git commit -am "first"
        | git checkout --orphan gh-pages
        | git rm -rf .
        | git add .
        | git commit -am "first"
        | git remote add origin $gitHubRepo
        | git pull  origin gh-pages
        | git rebase origin/gh-pages
        | git push origin gh-pages
        |fi
        |
      """.stripMargin
    val f = new File(filename)
    if (!f.exists()) {
      f.createNewFile();
      IO.write(f, content)
      "chmod +x ./" + filename ! s.log
    }
  }

  def createPublishToGithubScript(s: TaskStreams) = {
    val filename = "publishToGitHub.sh"
    val content =
      """#!/bin/sh
        |cd $localMavenRepo
        |git add .
        |git commit -am "publish by script"
        |git pull --rebase    origin gh-pages
        |git rebase origin/gh-pages
        |git push origin gh-pages
        |
      """.stripMargin
    val f = new File(filename)
    if (!f.exists()) {
      f.createNewFile();
      IO.write(f, content)
      "chmod +x ./" + filename ! s.log
    }
  }


  def createRepoTask(localRep: File, gitHubRep: String, s: TaskStreams) {
    s.log.info("create repo task")
    s.log.info("current dir: " + new File("").getAbsolutePath)
    Process("./createNewRepo.sh" :: Nil,
      None,
      "localMavenRepo" -> localRep.getAbsolutePath,
      "gitHubRepo" -> gitHubRep
    ) ! s.log
  }

  def publishToGithubRepoTask(localRep: File, s: TaskStreams) {
    s.log.info("publishToGithubRepoTask")
    s.log.info("current dir: " + new File("").getAbsolutePath)
    Process("./publishToGitHub.sh" :: Nil,
      None,
      "localMavenRepo" -> localRep.getAbsolutePath
    ) ! s.log
  }

  def localPublishTo(v: String, l: File) =
    Some(Resolver.file("file", l / (if (v.trim.endsWith("SNAPSHOT")) "snapshots" else "releases")))


  lazy val additionalSettings: Seq[Project.Setting[_]] = Seq(
    localRepo := Path.userHome / "github" / "maven",
    githubRepo := "git@github.com:NAUMEN-GP/maven.git",
    createPublishScripts <<= streams.map(s => createCreateNewRepoScript(s)),
    publishTo <<= (version, localRepo)(localPublishTo),
    createRepo <<= (localRepo, githubRepo, streams, createPublishScripts) map ((l, g, s, _) => createRepoTask(l, g, s)),
    publishToGithubRepo <<=
      (createRepo, publish, localRepo, streams) map ((_, _, l, s) => publishToGithubRepoTask(l, s))
  )

}
