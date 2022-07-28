package aQute.bnd.maven.export.plugin;

import java.util.Properties;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * A generate insturction will be created from this configuration as described
 * in the <a href="https://bnd.bndtools.org/instructions/generate.html">bnd
 * generate documentation</a>
 *
 * @author Juergen Albert
 */
public class Step {

	/**
	 * This is the clause in the resulting
	 * <a href="https://bnd.bndtools.org/instructions/generate.html"> generate
	 * instruction</a>. It is used to establish a fileset where wildcard are
	 * supported as usual.
	 */
	@Parameter
	private String	source;

	/**
	 * This is the output directory in the resulting
	 * <a href="https://bnd.bndtools.org/instructions/generate.html"> generate
	 * instruction</a>
	 */
	@Parameter
	private String	output;

	/**
	 * The generate option in the resulting
	 * <a href="https://bnd.bndtools.org/instructions/generate.html"> generate
	 * instruction. It is used to find a external plugin which may hold a
	 * generator or a declared Main-Class</a>
	 */
	@Parameter(required = false)
	private String		generateCommand	= null;

	/**
	 * The system option in the resulting
	 * <a href="https://bnd.bndtools.org/instructions/generate.html"> generate
	 * instruction. It will be executed as a system command.</a>
	 */
	@Parameter(required = false)
	private String		systemCommand	= null;

	/**
	 * Any additional properties that the specific generate plugin will support.
	 */
	@Parameter(property = "properties", required = false)
	private Properties	properties		= new Properties();

	public String getSource() {
		return source;
	}

	public String getOutput() {
		return output;
	}

	public String getGenerateCommand() {
		return generateCommand;
	}

	public String getSystemCommand() {
		return systemCommand;
	}

	public Properties getProperties() {
		return properties;
	}


}
