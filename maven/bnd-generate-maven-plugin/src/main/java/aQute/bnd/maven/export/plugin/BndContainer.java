package aQute.bnd.maven.export.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.ProviderType;
import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.maven.lib.configuration.BeanProperties;
import aQute.bnd.maven.lib.configuration.ConfigurationHelper;
import aQute.bnd.maven.lib.resolve.DependencyResolver;
import aQute.bnd.maven.lib.resolve.ImplicitFileSetRepository;
import aQute.bnd.maven.lib.resolve.LocalPostProcessor;
import aQute.bnd.maven.lib.resolve.PostProcessor;
import aQute.bnd.maven.lib.resolve.Scope;
import aQute.bnd.osgi.Processor;
import aQute.bnd.repository.fileset.FileSetRepository;
import aQute.bnd.service.RepositoryPlugin;
import aQute.bnd.unmodifiable.Sets;

@ProviderType
public class BndContainer {

	private static final Logger										logger	= LoggerFactory
		.getLogger(BndContainer.class);

	private final List<File>										bundles;

	private final boolean											includeDependencyManagement;

	private final MavenProject										project;

	private final RepositorySystemSession							repositorySession;

	private final Set<Scope>										scopes;

	private final MavenSession										session;

	private final boolean											useMavenDependencies;

	@SuppressWarnings("deprecation")
	private final org.apache.maven.artifact.factory.ArtifactFactory	artifactFactory;

	private final ProjectDependenciesResolver						resolver;

	private final RepositorySystem									system;

	private final boolean											transitive;

	private final PostProcessor										postProcessor;

	public static class Builder {

		private final MavenProject										project;
		private final MavenSession										session;
		private final RepositorySystemSession							repositorySession;
		private final ProjectDependenciesResolver						resolver;
		@SuppressWarnings("deprecation")
		private final org.apache.maven.artifact.factory.ArtifactFactory	artifactFactory;
		private final RepositorySystem									system;
		private List<File>												bundles						= Collections
			.emptyList();
		private boolean													includeDependencyManagement	= false;
		private Set<Scope>												scopes						= Sets
			.of(Scope.compile, Scope.runtime);
		private boolean													useMavenDependencies		= true;
		private boolean													transitive					= true;
		private PostProcessor											postProcessor				= new LocalPostProcessor();

		@SuppressWarnings("deprecation")
		public Builder(MavenProject project, MavenSession session, RepositorySystemSession repositorySession,
			ProjectDependenciesResolver resolver, org.apache.maven.artifact.factory.ArtifactFactory artifactFactory,
			RepositorySystem system) {

			this.project = Objects.requireNonNull(project);
			this.session = Objects.requireNonNull(session);
			this.repositorySession = Objects.requireNonNull(repositorySession);
			this.resolver = Objects.requireNonNull(resolver);
			this.artifactFactory = Objects.requireNonNull(artifactFactory);
			this.system = Objects.requireNonNull(system);
		}

		public Builder setBundles(List<File> bundles) {
			this.bundles = bundles;
			return this;
		}

		public Builder setIncludeDependencyManagement(boolean includeDependencyManagement) {
			this.includeDependencyManagement = includeDependencyManagement;
			return this;
		}

		public Builder setPostProcessor(PostProcessor postProcessor) {
			this.postProcessor = postProcessor;
			return this;
		}

		public Builder setScopes(Set<Scope> scopes) {
			this.scopes = scopes;
			return this;
		}

		public Builder setTransitive(boolean transitive) {
			this.transitive = transitive;
			return this;
		}

		public Builder setUseMavenDependencies(boolean useMavenDependencies) {
			this.useMavenDependencies = useMavenDependencies;
			return this;
		}

		public BndContainer build() {
			return new BndContainer(project, session, resolver, repositorySession, artifactFactory, system, scopes,
				bundles, useMavenDependencies, includeDependencyManagement, transitive, postProcessor);
		}

	}

	public static int report(Processor project) {
		int errors = 0;
		for (String warning : project.getWarnings()) {
			logger.warn("Warning : {}", warning);
		}
		for (String error : project.getErrors()) {
			logger.error("Error   : {}", error);
			errors++;
		}
		return errors;
	}

	@SuppressWarnings("deprecation")
	BndContainer(MavenProject project, MavenSession session, ProjectDependenciesResolver resolver,
		RepositorySystemSession repositorySession, org.apache.maven.artifact.factory.ArtifactFactory artifactFactory,
		RepositorySystem system, Set<Scope> scopes, List<File> bundles, boolean useMavenDependencies,
		boolean includeDependencyManagement, boolean transitive, PostProcessor postProcessor) {
		this.project = project;
		this.session = session;
		this.resolver = resolver;
		this.repositorySession = repositorySession;
		this.artifactFactory = artifactFactory;
		this.system = system;
		this.scopes = scopes;
		this.bundles = bundles;
		this.useMavenDependencies = useMavenDependencies;
		this.includeDependencyManagement = includeDependencyManagement;
		this.transitive = transitive;
		this.postProcessor = postProcessor;
	}

	public int generate(String task, File workingDir, GenerateOperation operation, Settings settings,
		MojoExecution mojoExecution) throws Exception {
		Properties beanProperties = new BeanProperties();
		beanProperties.put("project", project);
		beanProperties.put("settings", settings);
		Properties mavenProperties = new Properties(beanProperties);
		Properties projectProperties = project.getProperties();
		for (Enumeration<?> propertyNames = projectProperties.propertyNames(); propertyNames.hasMoreElements();) {
			Object key = propertyNames.nextElement();
			mavenProperties.put(key, projectProperties.get(key));
		}

		try (Project bnd = init(task, workingDir, mavenProperties)) {
			if (bnd == null) {
				return 1;
			}

			bnd.setTrace(logger.isDebugEnabled());

			bnd.setBase(project.getBasedir());
			File propertiesFile = ConfigurationHelper.loadProperties(bnd, project, mojoExecution);
			bnd.setProperty("project.output", workingDir.getCanonicalPath());

			int errors = report(bnd);
			if (!bnd.isOk()) {
				return errors;
			}
			injectImplicitRepository(bnd.getWorkspace());
			return operation.apply("generate", bnd);
		}
	}

	public Project init(String task, File workingDir, Properties mavenProperties) throws Exception {
		File cnfDir = new File(workingDir, "tempGenerateWS/cnf");
		cnfDir.mkdirs();
		File buildBnd = new File(cnfDir, "build.bnd");
		buildBnd.createNewFile();
		mavenProperties.store(new FileOutputStream(buildBnd), task);
		Workspace workspace = new Workspace(cnfDir.getParentFile());
		Project project = new Project(workspace, workingDir);
		workspace.setOffline(session.getSettings()
			.isOffline());
		project.forceRefresh(); // setBase must be called after forceRefresh
		project.getInfo(workspace);
		return project;
	}

	public boolean injectImplicitRepository(Workspace workspace) throws Exception {
		if (workspace.getPlugin(ImplicitFileSetRepository.class) == null) {
			workspace.addBasicPlugin(getFileSetRepository());
			for (RepositoryPlugin repo : workspace.getRepositories()) {
				repo.list(null);
			}
			return true;
		}
		return false;
	}

	/**
	 * Return a fully configured dependency resolver instance.
	 *
	 * @param project
	 * @return a fully configured dependency resolver instance
	 */
	public DependencyResolver getDependencyResolver(MavenProject project) {
		return new DependencyResolver(project, repositorySession, resolver, system, artifactFactory, scopes, transitive,
			postProcessor, useMavenDependencies, includeDependencyManagement);
	}

	/**
	 * Creates a new repository in every invocation.
	 *
	 * @return a new {@link ImplicitFileSetRepository}
	 * @throws Exception
	 */
	public FileSetRepository getFileSetRepository() throws Exception {
		return getFileSetRepository(project);
	}

	/**
	 * Creates a new repository in every invocation.
	 *
	 * @param project the Maven project
	 * @return a new {@link ImplicitFileSetRepository}
	 * @throws Exception
	 */
	public FileSetRepository getFileSetRepository(MavenProject project) throws Exception {
		DependencyResolver dependencyResolver = getDependencyResolver(project);

		String name = project.getName()
			.isEmpty() ? project.getArtifactId() : project.getName();

		return dependencyResolver.getFileSetRepository(name, bundles, useMavenDependencies,
			includeDependencyManagement);
	}
}
