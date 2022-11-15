package aQute.bnd.maven.resolver.plugin;

import static aQute.bnd.maven.lib.resolve.BndrunContainer.report;

import java.io.File;
import java.io.Writer;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import aQute.bnd.maven.lib.configuration.Bndruns;
import aQute.bnd.maven.lib.configuration.Bundles;
import aQute.bnd.maven.lib.resolve.BndrunContainer;
import aQute.bnd.maven.lib.resolve.Operation;
import aQute.bnd.maven.lib.resolve.Scope;
import aQute.bnd.osgi.Constants;
import aQute.bnd.unmodifiable.Sets;
import aQute.lib.io.IO;
import aQute.lib.utf8properties.UTF8Properties;
import biz.aQute.resolve.ResolveProcess;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.osgi.service.resolver.ResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the <code>-runbundles</code> for the given bndrun file.
 */
@Mojo(name = "resolve", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class ResolverMojo extends AbstractMojo {
	private static final Logger									logger	= LoggerFactory.getLogger(ResolverMojo.class);

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject										project;

	@Parameter(defaultValue = "${settings}", readonly = true)
	private Settings											settings;

	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
	private RepositorySystemSession								repositorySession;

	@Parameter
	private Bndruns												bndruns	= new Bndruns();

	@Parameter
	private Bundles												bundles	= new Bundles();

	@Parameter(defaultValue = "true")
	private boolean												useMavenDependencies;

	@Parameter(defaultValue = "true")
	private boolean												failOnChanges;

	@Parameter(defaultValue = "true")
	private boolean												writeOnChanges;

	@Parameter(defaultValue = "${project.build.directory}", readonly = true)
	private File												targetDir;

	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession										session;

	@Parameter(property = "bnd.resolve.include.dependency.management", defaultValue = "false")
	private boolean												includeDependencyManagement;

	@Parameter(defaultValue = "true")
	private boolean												reportOptional;

	@Parameter(property = "bnd.resolve.scopes", defaultValue = "compile,runtime")
	private Set<Scope>											scopes	= Sets.of(Scope.compile, Scope.runtime);

	@Parameter(property = "bnd.resolve.skip", defaultValue = "false")
	private boolean												skip;

	/**
	 * The bndrun files will be read from this directory.
	 */
	@Parameter(defaultValue = "${project.basedir}")
	private File 												bndrunDir;

	/**
	 * The bndrun files will be written to this directory. If the
	 * specified directory is the same as {@link #bndrunDir}, then
	 * any changes to a bndrun file will cause the bndrun file to be overwritten.
	 */
	@Parameter(defaultValue = "${project.basedir}")
	private File 												outputBndrunDir;

	@Component
	private RepositorySystem									system;

	@Component
	private ProjectDependenciesResolver							resolver;

	@Component
	@SuppressWarnings("deprecation")
	private org.apache.maven.artifact.factory.ArtifactFactory	artifactFactory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
			logger.debug("skip project as configured");
			return;
		}

		int errors = 0;

		try {
			List<File> bndrunFiles = bndruns.getFiles(bndrunDir, "*.bndrun");

			if (bndrunFiles.isEmpty()) {
				logger.warn(
					"No bndrun files were specified with <bndrun> or found as *.bndrun in the project. This is unexpected.");
				return;
			}

			BndrunContainer container = new BndrunContainer.Builder(project, session, repositorySession, resolver,
				artifactFactory, system).setBundles(bundles.getFiles(project.getBasedir()))
					.setIncludeDependencyManagement(includeDependencyManagement)
					.setScopes(scopes)
					.setUseMavenDependencies(useMavenDependencies)
					.build();

			Operation operation = getOperation();

			for (File runFile : bndrunFiles) {
				logger.info("Resolving {}:", runFile);
				if (!Objects.equals(outputBndrunDir, bndrunDir)) {
					IO.mkdirs(outputBndrunDir);
					File outputRunFile = new File(outputBndrunDir, runFile.getName());
					try (Writer writer = IO.writer(outputRunFile)) {
						UTF8Properties props = new UTF8Properties();
						props.setProperty(Constants.INCLUDE, String.format("\"~%s\"", escape(IO.absolutePath(runFile))));
						props.store(writer, null);
					}
					runFile = outputRunFile;
				}
				errors += container.execute(runFile, "resolve", targetDir, operation);
			}
		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		if (errors > 0)
			throw new MojoFailureException(errors + " errors found");
	}

	private Operation getOperation() {
		return (file, runName, run) -> {
			try {
				String result = run.resolve(failOnChanges, writeOnChanges);
				logger.info("{}: {}", Constants.RUNBUNDLES, result);
			} catch (ResolutionException re) {
				logger.error(ResolveProcess.format(re, reportOptional));
				throw re;
			} finally {
				int errors = report(run);
				if (errors > 0) {
					return errors;
				}
			}
			return 0;
		};
	}

	private String escape(String input) {
		final int length = input.length();
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length;i++) {
			char c = input.charAt(i);
			if (c == '"') {
				sb.append('\\');
			}
			sb.append(c);
		}
		return sb.length() == length ? input : sb.toString();
	}
}
