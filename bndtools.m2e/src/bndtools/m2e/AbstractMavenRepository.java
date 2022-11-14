package bndtools.m2e;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.project.IMavenProjectChangedListener;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.m2e.core.project.MavenProjectChangedEvent;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.service.repository.Repository;

import aQute.bnd.osgi.repository.BaseRepository;
import aQute.bnd.service.RepositoryPlugin;

public abstract class AbstractMavenRepository extends BaseRepository
	implements Repository, RepositoryPlugin, IMavenProjectChangedListener {

	final IMavenProjectRegistry mavenProjectRegistry;

	protected AbstractMavenRepository(IMavenProjectRegistry mavenProjectRegistry) {
		this.mavenProjectRegistry = mavenProjectRegistry;
	}

	@Override
	public boolean canWrite() {
		return false;
	}

	@Override
	public String getLocation() {
		Location location = Platform.getInstanceLocation();

		if (location != null) {
			return location.getURL()
				.toString();
		}

		return null;
	}

	@Override
	public PutResult put(InputStream stream, PutOptions options) throws Exception {
		throw new IllegalStateException(getName() + " is read-only");
	}

	File guessBundleFile(IMavenProjectFacade projectFacade) {
		String buildDirectoryGuess;

		IProject project = projectFacade.getProject();
		IPath outputLocation = projectFacade.getOutputLocation();

		if (outputLocation.segment(0)
			.equals(project.getFullPath()
				.segment(0))) {
			outputLocation = outputLocation.removeFirstSegments(1);
		}

		IFolder folder = project.getFolder(outputLocation.toString());

		if (folder.exists()) {
			outputLocation = folder.getLocation();
		}

		if (outputLocation != null) {
			if (outputLocation.lastSegment()
				.equals("classes")) {
				outputLocation = outputLocation.removeLastSegments(1);
			}

			buildDirectoryGuess = outputLocation.toOSString();
		} else {
			buildDirectoryGuess = projectFacade.getProject()
				.getLocation()
				.toOSString() + "/target";
		}

		ArtifactKey artifactKey = projectFacade.getArtifactKey();

		String finalNameGuess = buildDirectoryGuess + "/" + ArtifactKeyHelper.getArtifactId(artifactKey) + "-"
			+ ArtifactKeyHelper.getVersion(artifactKey) + ".jar";

		return new File(finalNameGuess);
	}

	void cleanup() {
		mavenProjectRegistry.removeMavenProjectChangedListener(this);
	}

	/**
	 * Needed in M2E version 2.0
	 */
	public void mavenProjectChanged(List<MavenProjectChangedEvent> events, IProgressMonitor monitor) {
		mavenProjectChanged(events.toArray(new MavenProjectChangedEvent[0]), monitor);
	}

}
