package com.github.nigelzor.dagwood;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.IOUtil;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.CollectResult;
import org.sonatype.aether.collection.DependencyCollectionException;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import com.github.nigelzor.dagwood.SerializingDependencyVisitor.NodeFormatter;
import com.github.nigelzor.dagwood.SerializingDependencyVisitor.DefaultNodeFormatter;

/**
 * Replacement for dependency:tree using Maven 3 dependency logic
 *
 * @goal tree
 * @phase verify
 * @requiresProject false
 */
public class TreeMojo extends AbstractMojo {

	/**
	 * The entry point to Aether, i.e. the component doing all the work.
	 *
	 * @component
	 */
	private RepositorySystem repoSystem;

	/**
	 * The current repository/network configuration of Maven.
	 *
	 * @parameter default-value="${repositorySystemSession}"
	 * @readonly
	 */
	private RepositorySystemSession repoSession;

	/**
	 * The project's remote repositories to use for the resolution of project dependencies.
	 *
	 * @parameter default-value="${project.remoteProjectRepositories}"
	 * @readonly
	 */
	private List<RemoteRepository> projectRepos;

	/**
	 * The project's remote repositories to use for the resolution of plugins and their dependencies.
	 *
	 * @parameter default-value="${project.remotePluginRepositories}"
	 * @readonly
	 */
	private List<RemoteRepository> pluginRepos;

	/**
	 * If specified, this parameter will cause the dependency tree to be written to the path specified, instead of
	 * writing to the console.
	 * @parameter expression="${outputFile}"
	 */
	private File outputFile;

	/**
	 * The artifact to calculate dependencies for.
	 *
	 * @parameter expression="${artifact}"
	 */
	private String artifact;

	/**
	 * The project to calculate dependencies for.
	 *
	 * @parameter expression="${project.model}"
	 */
	private Model project;

	private AetherDependencyBuilder dependencyBuilder = new AetherDependencyBuilder();

	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			CollectRequest collectRequest = new CollectRequest();
			String rootName;

			if (artifact != null) {
				rootName = artifact;
				collectRequest.setRoot(new Dependency(new DefaultArtifact(artifact), null));
			} else {
				if (project == null) {
					throw new MojoExecutionException("Either project or artifact must be specified");
				}
				rootName = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getPackaging() + ":" + project.getVersion();
				dependencyBuilder.addDependenciesToRequest(collectRequest, project, repoSession.getArtifactTypeRegistry());
			}

			for (RemoteRepository repository : projectRepos) {
				collectRequest.addRepository(repository);
			}
			for (RemoteRepository repository : pluginRepos) {
				collectRequest.addRepository(repository);
			}

			CollectResult dependencies = repoSystem.collectDependencies(repoSession, collectRequest);

			StringWriter writer = new StringWriter();
			NodeFormatter nodeFormatter = new DefaultNodeFormatter(rootName);
			dependencies.getRoot().accept(new SerializingDependencyVisitor(writer,
					SerializingDependencyVisitor.STANDARD_TOKENS, nodeFormatter));

			try {
				if (outputFile == null) {
					log(writer.toString(), getLog());
				} else {
					write(writer.toString(), outputFile);
					getLog().info("Wrote dependency tree to: " + outputFile);
				}
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to write dependencies to file", e);
			}
		} catch (DependencyCollectionException e) {
			throw new MojoExecutionException("Failed to collect dependencies", e);
		}
	}

	private static void write(String string, File outputFile) throws IOException {
		outputFile.getParentFile().mkdirs();
		FileWriter writer = null;
		try {
			writer = new FileWriter(outputFile, false);
			writer.write(string);
		} finally {
			IOUtil.close(writer);
		}
	}

	private static void log(String string, Log log) throws IOException {
		BufferedReader reader = new BufferedReader(new StringReader(string));
		String line;
		while ((line = reader.readLine()) != null) {
			log.info(line);
		}
		reader.close();
	}
}
