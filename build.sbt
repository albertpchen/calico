ThisBuild / tlBaseVersion := "0.1"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers := List(
  tlGitHubDev("armanbilge", "Arman Bilge")
)

ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / crossScalaVersions := Seq("3.2.0")
ThisBuild / scalacOptions ++= Seq("-new-syntax", "-indent", "-source:future")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / tlJdkRelease := Some(8)

val CatsVersion = "2.8.0"
val CatsEffectVersion = "3.3.14"
val MonocleVersion = "3.1.0"

lazy val root = tlCrossRootProject.aggregate(frp, calico, widget, example, todoMvc, unidocs)

lazy val frp = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("frp"))
  .settings(
    name := "calico-frp",
    tlVersionIntroduced := Map("3" -> "0.1.1"),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % CatsVersion,
      "org.typelevel" %%% "cats-effect" % CatsEffectVersion,
      "co.fs2" %%% "fs2-core" % "3.3.0",
      "org.typelevel" %%% "cats-laws" % CatsVersion % Test,
      "org.typelevel" %%% "cats-effect-testkit" % CatsEffectVersion % Test,
      "org.typelevel" %%% "discipline-munit" % "1.0.9" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "0.7.29" % Test
    )
  )

lazy val calico = project
  .in(file("calico"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calico",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "shapeless3-deriving" % "3.2.0",
      "dev.optics" %%% "monocle-core" % MonocleVersion,
      "com.raquo" %%% "domtypes" % "0.16.0-RC3",
      "org.scala-js" %%% "scalajs-dom" % "2.3.0"
    )
  )
  .dependsOn(frp.js)

lazy val widget = project
  .in(file("widget"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calico-widget"
  )
  .dependsOn(calico)

lazy val example = project
  .in(file("example"))
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .dependsOn(calico, widget)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    Compile / fastLinkJS / scalaJSLinkerConfig ~= {
      import org.scalajs.linker.interface.ModuleSplitStyle
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("calico")))
    },
    libraryDependencies ++= Seq(
      "dev.optics" %%% "monocle-macro" % MonocleVersion
    )
  )

lazy val todoMvc = project
  .in(file("todo-mvc"))
  .enablePlugins(ScalaJSPlugin, BundleMonPlugin, NoPublishPlugin)
  .dependsOn(calico)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    Compile / fastLinkJS / scalaJSLinkerConfig ~= {
      import org.scalajs.linker.interface.ModuleSplitStyle
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("todomvc")))
    },
    libraryDependencies ++= Seq(
      "dev.optics" %%% "monocle-macro" % MonocleVersion
    ),
    bundleMonCheckRun := true,
    bundleMonCommitStatus := false,
    bundleMonPrComment := false
  )

ThisBuild / githubWorkflowBuild +=
  WorkflowStep.Sbt(
    List("bundleMon"),
    name = Some("Monitor artifact size"),
    cond = Some("matrix.project == 'rootJS'")
  )

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(ScalaJSPlugin, TypelevelUnidocPlugin)
  .settings(
    name := "calico-docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(frp.js, calico)
  )

lazy val jsdocs = project.dependsOn(calico, widget).enablePlugins(ScalaJSPlugin)
lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    tlSiteApiPackage := Some("calico"),
    mdocJS := Some(jsdocs),
    laikaConfig ~= { _.withRawContent },
    tlSiteHeliumConfig ~= {
      // Actually, this *disables* auto-linking, to avoid duplicates with mdoc
      _.site.autoLinkJS()
    },
    tlSiteRelatedProjects ++= Seq(
      TypelevelProject.CatsEffect,
      TypelevelProject.Fs2,
      "http4s-dom" -> url("https://http4s.github.io/http4s-dom/")
    ),
    laikaInputs := {
      import laika.ast.Path.Root
      val jsArtifact = (todoMvc / Compile / fullOptJS / artifactPath).value
      val sourcemap = jsArtifact.getName + ".map"
      laikaInputs
        .value
        .delegate
        .addFile(
          jsArtifact,
          Root / "todomvc" / "index.js"
        )
        .addFile(
          jsArtifact.toPath.resolveSibling(sourcemap).toFile,
          Root / "todomvc" / sourcemap
        )
    },
    mdocVariables += {
      val src = IO.readLines(
        (todoMvc / sourceDirectory).value / "main" / "scala" / "todomvc" / "TodoMvc.scala")
      "TODO_MVC_SRC" -> src.dropWhile(!_.startsWith("package")).mkString("\n")
    },
    laikaSite := laikaSite.dependsOn(todoMvc / Compile / fullOptJS).value
  )
