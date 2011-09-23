package com.github.nigelzor.dagwood;

import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.CollectResult;
import org.sonatype.aether.collection.DependencyCollectionException;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.graph.DependencyVisitor;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.util.artifact.DefaultArtifact;

/**
 * Replacement for dependency:tree using Maven 3 dependency logic
 *
 * @goal tree
 * @phase verify
 * @requiresProject false
 */
public class TreeMojo extends AbstractMojo {

	private class TreePrinter implements DependencyVisitor {
		private int depth = 0;

		public boolean visitLeave(DependencyNode node) {
			depth--;
			return true;
		}

		public boolean visitEnter(DependencyNode node) {
			StringBuilder line = new StringBuilder();
			for (int i = 0; i < depth; i++) {
				line.append("   ");
			}
			Dependency dependency = node.getDependency();
			if (dependency == null) {
				line.append("-");
			} else {
				line.append(dependency.getArtifact()).append(":").append(dependency.getScope());
			}
			getLog().info(line);
			depth++;
			return true;
		}
	}

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

			if (artifact != null) {
				getLog().info("Collecting dependencies for " + artifact);

				collectRequest.setRoot(new Dependency(new DefaultArtifact(artifact), null));
			} else {
				if (project == null) {
					throw new MojoExecutionException("Either project or artifact must be specified");
				}
				getLog().info("Collecting dependencies for " + project.getName());

				dependencyBuilder.addDependenciesToRequest(collectRequest, project, repoSession.getArtifactTypeRegistry());
			}

			for (RemoteRepository repository : projectRepos) {
				collectRequest.addRepository(repository);
			}
			for (RemoteRepository repository : pluginRepos) {
				collectRequest.addRepository(repository);
			}

			CollectResult dependencies = repoSystem.collectDependencies(repoSession, collectRequest);
			dependencies.getRoot().accept(new TreePrinter());
		} catch (DependencyCollectionException e) {
			throw new MojoExecutionException("Failed to collect dependencies", e);
		}
	}

}
