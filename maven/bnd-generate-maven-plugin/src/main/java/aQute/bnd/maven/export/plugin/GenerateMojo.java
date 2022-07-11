package aQute.bnd.maven.export.plugin;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.maven.lib.configuration.Bundles;
import aQute.bnd.maven.lib.resolve.Scope;

@Mojo(name = "bnd-generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateMojo extends AbstractMojo {
	private static final Logger									logger	= LoggerFactory.getLogger(GenerateMojo.class);

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject										project;

	@Parameter(defaultValue = "${settings}", readonly = true)
	private Settings											settings;

	@Parameter(defaultValue = "${mojoExecution}", readonly = true)
	MojoExecution												mojoExecution;

	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
	private RepositorySystemSession								repositorySession;

	@Parameter(defaultValue = "${project.build.directory}", readonly = true)
	private File												targetDir;

	@Parameter(property = "bnd.skip", defaultValue = "false")
	boolean														skip;

	@Parameter
	private Bundles												bundles	= new Bundles();

	@Parameter(defaultValue = "true")
	private boolean												reportOptional;

	@Parameter(defaultValue = "true")
	private boolean												attach;

	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession										session;

	@Component
	private RepositorySystem									system;

	@Component
	private ProjectDependenciesResolver							resolver;

	@Component
	@SuppressWarnings("deprecation")
	protected org.apache.maven.artifact.factory.ArtifactFactory	artifactFactory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		int errors = 0;
		Set<Scope> scopes = new HashSet<Scope>();
		scopes.add(Scope.compile);
		scopes.add(Scope.provided);
		scopes.add(Scope.system);
		try {
			BndContainer container = new BndContainer.Builder(project, session, repositorySession, resolver,
				artifactFactory, system).setBundles(bundles.getFiles(project.getBasedir()))
					.setIncludeDependencyManagement(true)
					.setScopes(scopes)
					.setUseMavenDependencies(true)
					.build();

			GenerateOperation operation = getOperation();

			errors = container.generate("generating", targetDir, operation, settings, mojoExecution);

		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		if (errors > 0)
			throw new MojoFailureException(errors + " errors found");
	}

	private GenerateOperation getOperation() {
		return (taskName, project) -> {
				try {
					project.getGenerate().generate(false);
					if (project.isOk()) {
					return 0;
					}
			} finally {
				int errors = BndContainer.report(project);
				if (errors > 0) {
					return errors;
				}
			}
			return 0;
		};
	}
}
