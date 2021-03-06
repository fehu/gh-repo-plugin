package feh.util

import org.eclipse.jgit.api.Git
import sbt._
import Keys._
import scala.collection.convert.wrapAsScala._

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
      Classpaths.publishConfig(
        packagedArtifacts.in(ghPublishLocal).value,
        ivyFile = None,
        resolverName = resolver.name,
        checksums = checksums.in(ghPublishLocal).value,
        logging = ivyLoggingLevel.value,
        overwrite = isSnapshot.value
      )
    },
    ghPublishLocal <<= Classpaths.publishTask(ghPublishConfig, deliverLocal),
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

    ghSubmit <<= ghSubmit.dependsOn(ghPublishLocal),
    ghPush := {
      val repoDir = ghRepoLocalFile.value getOrElse noGhRepoLocalError(ghRepoLocalEnv.value)
      val pushed = Git.open(repoDir).push().call()
      streams.value.log.info("pushed: " + pushed.map(_.getURI).mkString(","))
    },

    ghPublish := {
      val currProj = Project.extract(state.value).currentProject
      if(currProj.aggregate.isEmpty) {
        Project.runTask(ghPublishLocal, state.value)
        Project.runTask(ghSubmit, state.value)
      }
      else TaskUtils.runTasksForAllSubProjects(currProj, state.value, ghPublishLocal, ghSubmit)
      Project.runTask(ghPush, state.value)
    },

    aggregate in ghPublish := false
  )

  private def noGhRepoLocalError(envVar: String) =
    sys.error(s"local version of github-based repo project is not defined, check $envVar environment variable")
}