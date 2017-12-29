package feh.util

import org.eclipse.jgit.api.Git
import sbt._
import Keys._
import scala.collection.JavaConverters._

object GhPublish extends AutoPlugin{
  override def requires = plugins.IvyPlugin
  override def trigger = allRequirements

  val ghPublish = TaskKey[Unit]("gh-repo-publish", "publish to remote github-based repo")

  val ghRepoLocalFile = SettingKey[Option[File]]("gh-repo-local-file", "local version of github-based repo project")
  val ghRepoLocalResolver = SettingKey[Option[Resolver]]("gh-repo-local-resolver", "local version of github-based repo project")
  val ghRepoLocalEnv = SettingKey[String]("gh-repo-local-env", "Environment variable with a path to the local version of gh repo project")

  private val ghSubmit = TaskKey[Unit]("gh-repo-submit", "submit repo changes")
  private val ghPush = TaskKey[Unit]("gh-repo-push", "push repo changes")

  private val ghPublishConfig = TaskKey[PublishConfiguration]("gh-repo-publish-config")
  private val ghPublishLocal = TaskKey[Unit]("gh-repo-publish-local")

  override lazy val projectSettings = Seq(
    // Default Local ghRepo Environment variable
    ghRepoLocalEnv := "LOCAL_GITHUB_REPO",

    ghRepoLocalFile := sys.env.get(ghRepoLocalEnv.value) map file,
    ghRepoLocalResolver := ghRepoLocalFile.value map (Resolver.file("publish-gh-repo-local", _)),
    resolvers ++= ghRepoLocalResolver.value.toSeq,
    ghPublishConfig := {
      val resolver = ghRepoLocalResolver.value getOrElse noGhRepoLocalError(ghRepoLocalEnv.value)
      PublishConfiguration(
        publishMavenStyle = true,
        deliverIvyPattern = None,
        status = None,
        configurations = None,
        resolverName = Some(resolver.name),
        artifacts = packagedArtifacts.in(ghPublishLocal).value.toVector,
        checksums = checksums.in(ghPublishLocal).value.toVector,
        logging = Some(ivyLoggingLevel.value),
        overwrite = isSnapshot.value
      )
    },
    ghPublishLocal := Classpaths.publishTask(ghPublishConfig, deliverLocal).value,
    ghSubmit := {
      val log = streams.value.log
      val repoDir = ghRepoLocalFile.value getOrElse noGhRepoLocalError(ghRepoLocalEnv.value)
      val branch = "gh-pages"
      val message = s"updating gh-repository for project ${name.value} v.${version.value}"
      val git = Git.open(repoDir)
      val repo = git.getRepository

      if(repo.getBranch != branch)sys.error(s"'$branch' branch expected")

      git.add.addFilepattern(".").call()
      log.info("## " + message)
      git.commit.setMessage(message).call()
    },

    ghSubmit := ghSubmit.dependsOn(ghPublishLocal).value,
    ghPush := {
      val repoDir = ghRepoLocalFile.value getOrElse noGhRepoLocalError(ghRepoLocalEnv.value)
      val pushed = Git.open(repoDir).push().call()
      streams.value.log.info("pushed: " + pushed.asScala.map(_.getURI).mkString(","))
    },

    ghPublish := {
      val stateValue = state.value
      val currProj = Project.extract(stateValue).currentProject
      if(currProj.aggregate.isEmpty) {
        Project.runTask(ghPublishLocal, stateValue)
        Project.runTask(ghSubmit, stateValue)
      }
      else TaskUtils.runTasksForAllSubProjects(currProj, stateValue, ghPublishLocal, ghSubmit)
      Project.runTask(ghPush, stateValue)
    },

    aggregate in ghPublish := false
  )

  private def noGhRepoLocalError(envVar: String) =
    sys.error(s"local version of github-based repo project is not defined, check $envVar environment variable")
}