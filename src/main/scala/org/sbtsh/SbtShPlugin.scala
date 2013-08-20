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

object SbtShPlugin extends Plugin with SbtShKeys{

  override lazy val settings = Seq(Keys.commands += shCommand) ++ additionalSettings

  def shCommand = Command.args("sh", "<shell command>") { (state, args) => 
    val ret = args.mkString(" ") !
    
    state
  }

  def createCreateNewRepoScript(s: TaskStreams) = {
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
    val f = new File("./createNewRepo.sh")
    if (!f.exists()) {
      f.createNewFile();
      IO.write(f, content)
      "chmod +x ./createNewRepo.sh" !
    }
  }

  def createRepoTask(localRep: File, gitHubRep: String, s: TaskStreams) {
    Process("./createNewRepo.sh" :: Nil,
      Path.userHome,
        "localMavenRepo" -> localRep.getAbsolutePath,
        "gitHubRepo" -> gitHubRep
      )
  }

  def publishToGithubRepoTask(localRep: File, s: TaskStreams) {
    Process("./publishToGitHub.sh" :: Nil,
      Path.userHome,
      "localMavenRepo" -> localRep.getAbsolutePath
    )
  }

  def localPublishTo(v: String, l: File) =
    Some(Resolver.file("file", l / (if (v.trim.endsWith("SNAPSHOT")) "snapshots" else "releases")))



  lazy val additionalSettings: Seq[Project.Setting[_]] = Seq(
    localRepo := Path.userHome / "github" / "maven",
    githubRepo := "git@github.com:NAUMEN-GP/maven.git" ,
    createPublishScripts <<= streams.map(s => createCreateNewRepoScript(s)),
    publishTo <<= (version, localRepo)(localPublishTo),
    createRepo <<= (localRepo, githubRepo,  streams, createPublishScripts) map( (l, g, s, _) => createRepoTask(l, g, s)),
    publishToGithubRepo <<=
      (createRepo, publish, localRepo, streams) map ((_, _, l, s) => publishToGithubRepoTask(l, s))
  )

}
