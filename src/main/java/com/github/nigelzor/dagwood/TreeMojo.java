package com.github.nigelzor.dagwood;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

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
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactDescriptorException;
import org.sonatype.aether.resolution.ArtifactDescriptorRequest;
import org.sonatype.aether.resolution.ArtifactDescriptorResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import com.github.nigelzor.dagwood.SerializingDependencyVisitor.NodeFormatter;
import com.github.nigelzor.dagwood.SerializingDependencyVisitor.DefaultNodeFormatter;

/**
 * Displays the dependency tree for this project, or a specified artifact.
 *
 * @goal tree
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
	 * If specified, this parameter will cause the dependency tree to be written to the path specified, instead of
	 * writing to the console.
	 * @parameter expression="${outputFile}"
	 */
	private File outputFile;

	/**
	 * The artifact to calculate dependencies for.
	 *
	 * @parameter expression="${artifact}" default-value="${project.groupId}:${project.artifactId}:${project.packaging}:${project.version}"
	 */
	private String artifact;

	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			ArtifactDescriptorResult artifactDescriptor = repoSystem.readArtifactDescriptor(repoSession,
					new ArtifactDescriptorRequest(new DefaultArtifact(artifact), projectRepos, null));

			CollectRequest collectRequest = new CollectRequest();
			collectRequest.setDependencies(artifactDescriptor.getDependencies());
			collectRequest.setManagedDependencies(artifactDescriptor.getManagedDependencies());
			collectRequest.setRepositories(projectRepos);

			CollectResult dependencies = repoSystem.collectDependencies(repoSession, collectRequest);

			StringWriter writer = new StringWriter();
			NodeFormatter nodeFormatter = new DefaultNodeFormatter(artifactDescriptor.getArtifact().toString());
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
		} catch (ArtifactDescriptorException e) {
			throw new MojoExecutionException("Failed to read artifact descriptor", e);
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
