package aQute.bnd.maven.export.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.StringJoiner;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
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
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mojo(name = "bnd-generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.NONE, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

	protected final Logger			logger	= LoggerFactory.getLogger(getClass());

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

	/**
	 * The Mojo by itself does nothing. Dependent on its configuration, it will
	 * try to find a suitable generator in one of the dependencies in this list.
	 */
	@Parameter(property = "externalPlugins", required = false)
	List<Dependency>				externalPlugins;

	/**
	 * Allows multiple steps
	 */
	@Parameter(property = "steps", required = false)
	List<Step>						steps;

	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession										session;

	@Component
	private RepositorySystem									system;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		int errors = 0;

		List<Dependency> normalizedDependencies = new ArrayList<Dependency>();
		if (externalPlugins != null) {
			for (Dependency dependency : externalPlugins) {
				normalizedDependencies.add(normalizeDependendency(dependency));
			}
		}
		Properties additionalProperties = new Properties();
		StringJoiner instruction = new StringJoiner(",");
		steps.stream()
			.map(this::mapStep)
			.forEach(instruction::add);
		if (instruction.length() > 0) {
			logger.info("created instructions from steps: {}", instruction.toString());
			additionalProperties.put("-generate.maven", instruction.toString());
		}
		try {

			BndContainer container = new BndContainer.Builder(project, session, repositorySession, system)
				.setDependencies(normalizedDependencies)
				.setAdditionalProperiets(additionalProperties)
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

	private String mapStep(Step step) {
		StringJoiner joiner = new StringJoiner(";");
		joiner.add(step.getSource());
		joiner.add("output=" + step.getOutput());
		if (step.getGenerateCommand() != null) {
			joiner.add("generate=\"" + step.getGenerateCommand() + "\"");
		}
		if (step.getSystemCommand() != null) {
			joiner.add("system=\"" + step.getSystemCommand() + "\"");
		}
		step.getProperties()
			.forEach((k, v) -> joiner.add(k + "=\"" + v + "\""));
		return joiner.toString();
	}

	private Dependency normalizeDependendency(Dependency dependency) throws MojoExecutionException {
		if(dependency.getVersion() != null) {
			return dependency;
		} else {
			List<Dependency> deps = project.getDependencyManagement() != null ? project.getDependencyManagement()
				.getDependencies() : Collections.emptyList();
			return deps
				.stream()
				.filter(d -> d.getArtifactId()
					.equals(dependency.getArtifactId())
					&& d.getGroupId()
						.equals(dependency.getGroupId()))
				.findFirst()
				.map(d -> d.clone())
				.orElseThrow(() -> new MojoExecutionException(dependency, "Version is missing",
					"The Version of the " + dependency.toString() + " is missing"));
		}
	}
}
