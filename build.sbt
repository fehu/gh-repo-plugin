sbtPlugin := true

organization := "feh.util"

name := "gh-repo-plugin"

version := "0.2-SNAPSHOT"

crossScalaVersions := Seq("2.11.4, 2.10.4", "2.9.3", "2.9.2")

libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "3.6.0.201411121045-m1"