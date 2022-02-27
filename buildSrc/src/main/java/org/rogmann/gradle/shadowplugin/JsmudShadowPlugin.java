package org.rogmann.gradle.shadowplugin;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gradle-plugin to shadow dependencies into a project combined with sources.
 */
public class JsmudShadowPlugin implements Plugin<Project> {
	/** Logger */
	private static final Logger LOGGER = LoggerFactory.getLogger(JsmudShadowPlugin.class); 

	/** plugin-configuration */
	private JsmudShadowModel model;

	/** original main source-folders */
	private List<File> sourceFolderMainOrig;

	/** original test source-folders */
	private List<File> sourceFolderTestOrig;

	/** destination source-folder (contains mapped sources) */
	private File dirSrcMapped;

	/** list of source-files of dependencies */
	private List<File> listDepSources;

	/** {@inheritDoc} */
	@Override
	public void apply(Project project) {
		System.out.println("Initialize JsmudShadowPlugin");
		project.getExtensions().create("JsmudShadowPlugin", JsmudShadowModel.class);
		
		final File buildDir = project.getBuildDir();
		LOGGER.debug("IMP: buildDir=" + buildDir);
		dirSrcMapped = new File(buildDir, "generated/srcMapped");
		try {
			Files.createDirectories(dirSrcMapped.toPath());
		} catch (IOException e) {
			throw new GradleException("Can't create generated source-directory " + dirSrcMapped, e);
		}

		SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
		LOGGER.debug("Source-sets: " + sourceSets);
		final SourceSet ssMain = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
		final SourceDirectorySet sdsJava = ssMain.getJava();
		sourceFolderMainOrig = new ArrayList<>(sdsJava.getSrcDirs());

		final SourceSet ssTest = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
		final SourceDirectorySet sdsTestJava = ssTest.getJava();
		sourceFolderTestOrig = new ArrayList<>(sdsTestJava.getSrcDirs());

		// Is there some task to be used instead of action in constructor?
		final List<String> listDirs = Arrays.asList(new String[] { dirSrcMapped.toString() });
		sdsJava.setSrcDirs(listDirs);

		final List<String> listDirsTest = Arrays.asList(new String[] { "srctest2" });
		sdsTestJava.setSrcDirs(listDirsTest);

		project.afterEvaluate(p -> {
			LOGGER.debug("AfterEvaluate: project=" + p);
			model = p.getExtensions().getByType(JsmudShadowModel.class);
			LOGGER.info("dep-package: " + model.depPackage);
			LOGGER.info("dep-packageShadow: " + model.depPackageShadow);
			if (model.depPackage == null) {
				throw new GradleException("Plugin-Property depPackage is missing");
			}
			if (model.depPackageShadow == null) {
				throw new GradleException("Plugin-Property depPackageShadow is missing");
			}
			listDepSources = determineDepsSources(p);
			LOGGER.info("Source-files of dependencies: " + listDepSources);
		});

		final Task taskCAPD = project.task("copyAndProcessDeps", task -> {
			System.out.println("init task copyAndProcessDeps");
			task.doLast(taskDL -> {
				taskCopyAndProcessSources();
			});
		});
		TaskContainer tasks = project.getTasks();
		tasks.withType(JavaCompile.class).forEach(javaCompile -> {
			LOGGER.debug("JavaCompile: " + javaCompile);
			javaCompile.dependsOn(taskCAPD);
		});
	}

	/**
	 * Implementation of the task which shadows the dependencies and copies and processes the sources.
	 */
	public void taskCopyAndProcessSources() {
		System.out.println("start task copyAndProcessDeps");
		if (listDepSources == null) {
			throw new GradleException("List of source-files of dependencies is missing.");
		}
		final String nameSrcSearch = model.depPackage.replace('.', '/') + '/';
		final String nameSrcReplace = model.depPackageShadow.replace('.', '/') + '/';
		LOGGER.info(String.format("Replace (%s) by (%s)", nameSrcSearch, nameSrcReplace));
		for (final File file : listDepSources) {
			LOGGER.debug("Process " + file);
			try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
				while (true) {
					final ZipEntry entry = zis.getNextEntry();
					if (entry == null) {
						break;
					}
					String nameSrc = entry.getName();
					String nameDst = nameSrc.replace(nameSrcSearch, nameSrcReplace);
					final File fileDest = new File(dirSrcMapped, nameDst);
					if (entry.isDirectory()) {
						try {
							Files.createDirectories(fileDest.toPath());
						} catch (IOException e) {
							throw new GradleException("Can't create generated source-directory " + fileDest, e);
						}
					}
					else {
						try (OutputStream os = new FileOutputStream(fileDest)) {
							copyAndProcessFile(nameSrc, zis, os);
						}
						catch (IOException e) {
							throw new GradleException(String.format("IO-error while generating (%s) via (%s)", fileDest, nameSrc), e);
						}
					}
				}
			}
			catch (IOException e) {
				throw new GradleException("IO-error when reading " + file, e);
			}
		}
		
		copyProjectSources();
	}

	/**
	 * Determine the sources of the dependencies.
	 * @param p project
	 * @return list of JAR-files
	 */
	private List<File> determineDepsSources(Project p) {
		final List<File> listDepSources = new ArrayList<>();
		ConfigurationContainer configs = p.getConfigurations();
		Configuration configRuntime = configs.getByName("runtimeClasspath");
		final Set<? extends DependencyResult> setDeps = configRuntime.getIncoming().getResolutionResult().getAllDependencies();
		final List<ComponentIdentifier> listIds = new ArrayList<>();
		for (DependencyResult depResult : setDeps) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Dep: " + depResult);
			}
			if (depResult instanceof DefaultResolvedDependencyResult) {
				DefaultResolvedDependencyResult depResolved = (DefaultResolvedDependencyResult) depResult;
				final ResolvedComponentResult selected = depResolved.getSelected();
				ComponentIdentifier compId = selected.getId();
				LOGGER.info("Dependency resolved: " + compId);
				listIds.add(compId);
			}
		}
		@SuppressWarnings("unchecked")
		final Class<? extends Artifact>[] aClass = new Class[] {SourcesArtifact.class};
		final ArtifactResolutionResult result = p.getDependencies().createArtifactResolutionQuery()
			.forComponents(listIds)
			.withArtifacts(JvmLibrary.class, aClass)
			.execute();
		for (ComponentArtifactsResult compArtRes : result.getResolvedComponents()) {
			final Set<ArtifactResult> setSources = compArtRes.getArtifacts(SourcesArtifact.class);
			for (ArtifactResult sourceResult : setSources) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("SourceResult: " + sourceResult);
				}
				if (sourceResult instanceof ResolvedArtifactResult) {
					ResolvedArtifactResult rar = (ResolvedArtifactResult) sourceResult;
					File sourceFile = rar.getFile();
					listDepSources.add(sourceFile);
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("RAR: " + sourceFile);
					}
				}
			}
		}
		return listDepSources;
	}

	/**
	 * Copies source-files and resources into a destination directory.
	 * Java files will be processed.
	 */
	private void copyProjectSources() {
		final Path dstPath = dirSrcMapped.toPath();
		for (File file : sourceFolderMainOrig) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("IMP: copyDir " + file);
			}
			try {
				final Path pathRoot = file.toPath();
				Files.walkFileTree(pathRoot, new FileVisitor<Path>() {
					/** current relative path */
					private Path currRelPath;
					/** current destination directory */
					private Path currDstPath;

					/** {@inheritDoc} */
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						currRelPath = pathRoot.relativize(dir);
						currDstPath = dstPath.resolve(currRelPath);
						if (LOGGER.isDebugEnabled()) {
							LOGGER.debug("dest-dir: " + currDstPath);
						}
						Files.createDirectories(currDstPath);
						
						return FileVisitResult.CONTINUE;
					}

					/** {@inheritDoc} */
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						String name = file.getFileName().toString();
						try (FileInputStream fis = new FileInputStream(file.toFile())) {
							final Path pathDest = currDstPath.resolve(name);
							try (FileOutputStream fos = new FileOutputStream(pathDest.toFile())) {
								copyAndProcessFile(name, fis, fos);
							}
						}
						return FileVisitResult.CONTINUE;
					}

					/** {@inheritDoc} */
					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
						throw new IOException("Can't visit file " + file, exc);
					}

					/** {@inheritDoc} */
					@Override
					public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						return FileVisitResult.CONTINUE;
					}
				});
			}
			catch (IOException e) {
				throw new GradleException("IO-error while processing source-folder " + file, e);
			}
		}
	}

	/**
	 * Copies and processes a source-file or resource.
	 * @param name name of the file
	 * @param is input-stream (must not be closed)
	 * @param os output-stream
	 * @throws IOException
	 */
	private void copyAndProcessFile(final String name, final InputStream is, final OutputStream os)
			throws IOException {
		if (name.endsWith(".java")) {
			final BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
				while (true) {
					String line = br.readLine();
					if (line == null) {
						break;
					}
					line = line.replace(model.depPackage, model.depPackageShadow);
					bw.write(line);
					bw.write(System.lineSeparator());
				}
			}
		}
		else {
			final byte[] buf = new byte[4096];
			while (true) {
				final int len = is.read(buf);
				if (len < 0) {
					break;
				}
				os.write(buf, 0, len);
			}
		}
	}

}
