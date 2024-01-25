package test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.osgi.resource.Capability;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.AttributeClasses;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.OSInformation;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.resource.RequirementBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;
import aQute.lib.collections.ExtList;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;
import aQute.libg.reporter.ReporterAdapter;
import aQute.service.reporter.Reporter;
import aQute.service.reporter.Reporter.SetLocation;

public class ProcessorTest {

	@Test
	public void testFixupMerge() throws IOException {
		Processor p = new Processor();
		p.setProperty("-fixupmessages.foo", "foo");
		p.setProperty("-fixupmessages.bar", "bar");
		p.error("foo");
		p.error("bar");
		assertTrue(p.check());
		p.close();
	}

	@Test
	public void testGetInfo() throws IOException {
		try (Processor a = new Processor(); Processor b = new Processor()) {
			b.error("error-in-b");
			b.warning("warning-in-b");
			a.getInfo(b);
			assertThat(a.getErrors()).containsExactly("error-in-b");
			assertThat(a.getWarnings()).containsExactly("warning-in-b");
			assertThat(b.getErrors()).isEmpty();
			assertThat(b.getWarnings()).isEmpty();
		}
		try (Processor a = new Processor(); Processor b = new Processor()) {
			b.error("error-in-b");
			b.warning("warning-in-b");
			a.getInfo(b, "prefix ");
			assertThat(a.getErrors()).containsExactly("prefix error-in-b");
			assertThat(a.getWarnings()).containsExactly("prefix warning-in-b");
			assertThat(b.getErrors()).isEmpty();
			assertThat(b.getWarnings()).isEmpty();
		}
		try (Processor a = new Processor(); Processor b = new Processor()) {
			b.error("error-in-b");
			b.setBase(new File("prefix"));
			b.warning("warning-in-b");
			a.getInfo(b, null);
			assertThat(a.getErrors()
				.get(0)).endsWith("biz.aQute.bndlib.tests :error-in-b");
			assertThat(a.getWarnings()
				.get(0)).endsWith("biz.aQute.bndlib.tests :warning-in-b");
			assertThat(b.getErrors()).isEmpty();
			assertThat(b.getWarnings()).isEmpty();
		}
		try (Processor a = new Processor()) {
			Reporter b = new ReporterAdapter();
			b.error("error-in-b");
			b.warning("warning-in-b");

			a.getInfo(b, "");
			assertThat(a.getErrors()
				.get(0)).endsWith("error-in-b");
			assertThat(a.getWarnings()
				.get(0)).endsWith("warning-in-b");
			assertThat(b.getErrors()).isEmpty();
			assertThat(b.getWarnings()).isEmpty();
		}
		try (Processor a = new Processor()) {
			Reporter b = new ReporterAdapter();
			b.error("error-in-b");
			b.warning("warning-in-b");

			a.getInfo(b, "prefix ");
			assertThat(a.getErrors()
				.get(0)).endsWith("prefix error-in-b");
			assertThat(a.getWarnings()
				.get(0)).endsWith("prefix warning-in-b");
			assertThat(b.getErrors()).isEmpty();
			assertThat(b.getWarnings()).isEmpty();
		}
		try (Processor a = new Processor()) {
			Reporter b = new ReporterAdapter();
			b.error("error-in-b");
			b.warning("warning-in-b");

			a.getInfo(b, null);
			assertThat(a.getErrors()
				.get(0)).endsWith("biz.aQute.bndlib.tests :error-in-b");
			assertThat(a.getWarnings()
				.get(0)).endsWith("biz.aQute.bndlib.tests :warning-in-b");
			assertThat(b.getErrors()).isEmpty();
			assertThat(b.getWarnings()).isEmpty();
		}
	}

	@Test
	public void testFixupMacro() throws IOException {
		Processor p = new Processor();
		p.setProperty("skip", "foo");
		p.setProperty("-fixupmessages", "${skip},bar");
		p.error("foo");
		p.error("bar");
		assertTrue(p.check());
		p.close();
	}

	@Test
	public void testNative() throws Exception {
		assertNative("osname=linux;osversion=2.3;processor=arm_le", "(osgi.native.osname~=LINUX)");
		assertNative("osname=Windows;osversion=10.0;processor=x86", "(osgi.native.osname~=Win32)");
		assertNative("osname=Windows;osversion=10.0;processor=x86", "(osgi.native.processor~=pentium)");
		assertNative("osname=Windows;osversion=10.0;processor=x86-64", "(osgi.native.processor~=amd64)");
		assertNative("osname=Linux;osversion=5.1.0;processor=arm", "(osgi.native.processor~=arm)",
			"The 'arm' processor is deprecated");
		assertNative("osname=Mac OS X;osversion=11.1;processor=aarch64", "(osgi.native.processor~=aarch64)");
	}

	@Test
	public void testNativeDefaults() throws Exception {
		try (Processor p = new Processor();) {
			p.setProperty("a", "${native_capability}");

			// Use the current OS first to enable loading things like default
			// file system before we change system properties.
			assertNativeDefault(System.getProperty("os.name"), System.getProperty("os.version"),
				System.getProperty("os.arch"),
				"(&(osgi.native.osname=*)(osgi.native.osversion=*)(osgi.native.processor=*)(osgi.native.language=*))");

			//
			// Mac OS
			//

			assertNativeDefault("Mac OS X", "10.8.2", "x86_64",
				"(&(osgi.native.osname~=MacOSX)(osgi.native.osname~=Mac OS X))");
			assertNativeDefault("Mac OS X", "10.8.2", "x86_64", "(osgi.native.osversion=0010.8.2)");
			assertNativeDefault("Mac OS X", "10.8.2", "x86_64",
				"(&(osgi.native.processor=x86-64)(osgi.native.processor=amd64)(osgi.native.processor=em64t)(osgi.native.processor=x86_64))");
			assertNativeDefault("Mac OS X", "11.1", "aarch64",
				"(&(osgi.native.processor=aarch64)(osgi.native.processor=arm64))");

			//
			// Linux
			//

			assertNativeDefault("Linux", "3.8.8-202.fc18.x86_64", "amd64",
				"(&(osgi.native.osname~=linux)(osgi.native.processor=*)(osgi.native.osversion=3.8.8.-202_fc18_x86_64))");

			assertNativeDefault("Linux", "3.8.8-202.fc18.x86_64", "em64t",
				"(&(osgi.native.osname~=linux)(osgi.native.processor=em64t)(osgi.native.osversion=3.8.8.-202_fc18_x86_64))");

			//
			// Windows
			//

			assertNativeDefault("Windows XP", "5.1.7601.17514", "x86",
				"(&(osgi.native.osname~=WindowsXP)(osgi.native.osname~=WinXP)(osgi.native.osname~=Windows XP)(osgi.native.osname~=Win32))");

			assertNativeDefault("Windows XP", "5.1.7601.17514", "x86",
				"(&(osgi.native.processor~=x86)(osgi.native.processor~=pentium)(osgi.native.processor~=i386)(osgi.native.processor~=i486)(osgi.native.processor~=i686)(osgi.native.processor~=i586))");

			assertNativeDefault("Windows XP", "5.1.7601.17514", "x86", "(&(osgi.native.osversion=5.1.0))");

			assertNativeDefault("Windows Vista", "6.0.7601.17514", "x86",
				"(&(osgi.native.osname~=WindowsVista)(osgi.native.osname~=WinVista)(osgi.native.osname~=Windows Vista)(osgi.native.osname~=Win32))");

			assertNativeDefault("Windows 7", "6.1.7601.17514", "x86",
				"(&(osgi.native.osname~=Windows7)(osgi.native.osname~=Windows 7)(osgi.native.osname~=Win32)(osgi.native.osversion=6.1.0))");

			assertNativeDefault("Windows 8", "6.2.7601.17514", "x86",
				"(&(osgi.native.osname~=Windows8)(osgi.native.osname~=Windows 8)(osgi.native.osname~=Win32)(osgi.native.osversion=6.2.0))");
		}
	}

	@Test
	public void testOperatingSystems() {

		assertIn(OSInformation.getOperatingSystemAliases("Windows XP", "5.1.x").osnames, "WindowsXP", "Windows XP",
			"WinXP", "Win32");
		assertIn(OSInformation.getOperatingSystemAliases("Windows Vista", "6.0.x").osnames, "WindowsVista",
			"Windows Vista", "WinVista", "Win32");
		assertIn(OSInformation.getOperatingSystemAliases("Solaris", "3.8").osnames, "Solaris");
		assertIn(OSInformation.getOperatingSystemAliases("AIX", "3.8").osnames, "AIX");
		assertIn(OSInformation.getOperatingSystemAliases("HP-UX", "3.8").osnames, "HPUX", "hp-ux");

	}

	private void assertIn(String osnames, String... members) {
		List<String> split = Strings.split(osnames);
		for (String member : members) {
			if (!split.contains(member))
				fail(member + " is not a member of " + split);
		}
	}

	@Test
	public void testUnknownProcessor() throws Exception {
		try (Processor p = new Processor();) {
			assertNative("osname=linux;osversion=2.3;processor=FOO;processor=BLA",
				"(&(osgi.native.processor~=FOO)(osgi.native.processor~=BLA))");
		}

	}

	@Test
	public void testUnknownOsname() throws Exception {
		try (Processor p = new Processor();) {
			assertNative("osname=Beos;osversion=2.3;processor=FOO;processor=BLA", "(&(osgi.native.osname~=beos))");
			fail("Expected failure because we use an unknown name");
		} catch (IllegalArgumentException e) {
			// ok
		}
	}

	@Test
	public void testNoOsVersion() throws Exception {
		try (Processor p = new Processor();) {
			String cap = p._native_capability("native_capability", "processor=x86", "osname=Linux");
			System.out.println(cap);
		} catch (IllegalArgumentException e) {
			System.out.println(e);
		}

	}

	private void assertNativeDefault(String osname, String osversion, String processor, String filter)
		throws Exception {
		String origOsName = System.getProperty("os.name");
		String origOsVersion = System.getProperty("os.version");
		String origOsArch = System.getProperty("os.arch");
		String processed;
		try {
			System.setProperty("os.name", osname);
			System.setProperty("os.version", osversion);
			System.setProperty("os.arch", processor);
			assertNative(null, filter);

		} finally {
			System.setProperty("os.name", origOsName);
			System.setProperty("os.version", origOsVersion);
			System.setProperty("os.arch", origOsArch);
		}
	}

	private void assertNative(String in, String filter, String... fixup) throws Exception {
		List<String> split = in == null ? new ArrayList<>() : Strings.split("\\s*;\\s*", in);
		split.add(0, "native_capability");
		try (Processor p = new Processor();) {
			String s = p._native_capability(split.toArray(new String[0]));
			assertNotNull(s);
			System.out.println(s);

			ResourceBuilder rb = new ResourceBuilder();
			List<Capability> capabilities = rb.addProvideCapabilities(s);

			RequirementBuilder qb = new RequirementBuilder("osgi.native");
			qb.addDirective("filter", filter);
			List<Capability> found = ResourceUtils.findProviders(qb.synthetic(), capabilities);
			assertTrue(!found.isEmpty());

			assertTrue(p.check(fixup));
		}
	}

	// Thin wrapper around Processor in order to make some
	// of the protected methods public to facilitate
	// testing.
	static class SeethroughProcessor extends Processor {
		@Override
		// This method is protected in the superclass,
		// overriding only to make it public for testing.
		public Processor beginHandleErrors(String message) {
			return super.beginHandleErrors(message);
		}

		@Override
		// This method is protected in the superclass,
		// overriding only to make it public for testing.
		public void endHandleErrors(Processor previous) {
			super.endHandleErrors(previous);
		}
	}

	@Test
	public void testBeginEndHandleErrors() throws IOException {
		try (Processor sub = new Processor(); SeethroughProcessor owner = new SeethroughProcessor()) {
			Processor previous = owner.beginHandleErrors("dummy");
			final String ERROR = "Error", WARNING = "Warning";
			SetLocation eLoc = sub.error(ERROR);
			SetLocation wLoc = sub.warning(WARNING);
			owner.endHandleErrors(previous);
			assertThat(owner.getErrors()).as("owner errors")
				.containsExactly(ERROR);
			assertThat(sub.getErrors()).as("sub errors")
				.isEmpty();
			assertThat(owner.getLocation(ERROR)).as("owner error loc")
				.isSameAs(eLoc);
			assertThat(sub.getLocation(ERROR)).as("sub error loc")
				.isNull();
			assertThat(owner.getWarnings()).as("owner warnings")
				.containsExactly(WARNING);
			assertThat(sub.getWarnings()).as("sub warnings")
				.isEmpty();
			assertThat(owner.getLocation(WARNING)).as("owner warning loc")
				.isSameAs(wLoc);
			assertThat(sub.getLocation(WARNING)).as("sub warning loc")
				.isNull();
		}
	}

	@Test
	public void testPlugins() {

	}

	@Test
	public void testFixupMessages() throws IOException {
		Processor p = new Processor();
		p.setTrace(true);

		p.error("abc");
		assertFalse(p.isOk());

		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;restrict:=warning");
		assertEquals(1, p.getErrors()
			.size());
		assertEquals(0, p.getWarnings()
			.size());

		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc");
		assertEquals(0, p.getErrors()
			.size());
		assertEquals(0, p.getWarnings()
			.size());

		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;is:=error");
		assertEquals(1, p.getErrors()
			.size());
		assertEquals(0, p.getWarnings()
			.size());

		p.clear();
		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;is:=warning");
		assertEquals(0, p.getErrors()
			.size());
		assertEquals(1, p.getWarnings()
			.size());

		p.clear();
		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;replace:=def");
		assertEquals("def", p.getErrors()
			.get(0));
		assertEquals(0, p.getWarnings()
			.size());

		p.clear();
		p.setProperty(Constants.FIXUPMESSAGES, "'abc def\\s*ghi';is:=warning");
		p.error("abc def  \t\t   ghi");
		assertEquals(0, p.getErrors()
			.size());
		assertEquals(1, p.getWarnings()
			.size());

		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;replace:=def;is:=warning");
		assertEquals("def", p.getWarnings()
			.get(0));
		assertEquals(0, p.getErrors()
			.size());

		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;restrict:=error");
		assertEquals(0, p.getErrors()
			.size());
		assertEquals(1, p.getWarnings()
			.size());

		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc");
		assertEquals(0, p.getErrors()
			.size());
		assertEquals(0, p.getWarnings()
			.size());

		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;is:=warning");
		assertEquals(0, p.getErrors()
			.size());
		assertEquals(1, p.getWarnings()
			.size());

		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;is:=error");
		assertEquals(1, p.getErrors()
			.size());
		assertEquals(0, p.getWarnings()
			.size());

		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;replace:=def");
		assertEquals("def", p.getWarnings()
			.get(0));
		assertEquals(0, p.getErrors()
			.size());

		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;replace:=def;is:=error");
		assertEquals("def", p.getErrors()
			.get(0));
		assertEquals(0, p.getWarnings()
			.size());
		p.close();
	}

	@Test
	public void testDuplicates() {
		assertEquals("", Processor.removeDuplicateMarker("~"));

		assertTrue(Processor.isDuplicate("abc~"));
		assertTrue(Processor.isDuplicate("abc~~~~~~~~~"));
		assertTrue(Processor.isDuplicate("~"));
		assertFalse(Processor.isDuplicate(""));
		assertFalse(Processor.isDuplicate("abc"));
		assertFalse(Processor.isDuplicate("ab~c"));
		assertFalse(Processor.isDuplicate("~abc"));

		assertEquals("abc", Processor.removeDuplicateMarker("abc~"));
		assertEquals("abc", Processor.removeDuplicateMarker("abc~~~~~~~"));
		assertEquals("abc", Processor.removeDuplicateMarker("abc"));
		assertEquals("ab~c", Processor.removeDuplicateMarker("ab~c"));
		assertEquals("~abc", Processor.removeDuplicateMarker("~abc"));
		assertEquals("", Processor.removeDuplicateMarker(""));
		assertEquals("", Processor.removeDuplicateMarker("~~~~~~~~~~~~~~"));
	}

	public static void appendPathTest() throws Exception {
		assertEquals("a/b/c", Processor.appendPath("", "a/b/c/"));
		assertEquals("a/b/c", Processor.appendPath("", "/a/b/c"));
		assertEquals("a/b/c", Processor.appendPath("/", "/a/b/c/"));
		assertEquals("a/b/c", Processor.appendPath("a", "b/c/"));
		assertEquals("a/b/c", Processor.appendPath("a", "b", "c"));
		assertEquals("a/b/c", Processor.appendPath("a", "b", "/c/"));
		assertEquals("a/b/c", Processor.appendPath("/", "a", "b", "/c/"));
		assertEquals("a/b/c", Processor.appendPath("////////", "////a////b///c//"));

	}

	@Test
	public void testUriMacro() throws Exception {
		try (Processor p = new Processor()) {
			String baseURI = p.getBaseURI()
				.toString();
			String otherURI = new URI("file:/some/dir/").toString();
			p.setProperty("uri1", "${uri;dist/bundles}");
			p.setProperty("uri2", "${uri;/dist/bundles}");
			p.setProperty("uri3", "${uri;file:dist/bundles}");
			p.setProperty("uri4", "${uri;file:/dist/bundles}");
			p.setProperty("uri5", "${uri;dist/bundles;" + otherURI + "}");
			p.setProperty("uri6", "${uri;/dist/bundles;" + otherURI + "}");
			p.setProperty("uri7", "${uri;file:dist/bundles;" + otherURI + "}");
			p.setProperty("uri8", "${uri;file:/dist/bundles;" + otherURI + "}");
			p.setProperty("uri9", "${uri;http://foo.com/dist/bundles}");
			p.setProperty("uri10", "${uri;http://foo.com/dist/bundles;" + otherURI + "}");
			p.setProperty("uri11", "${uri;.}");
			String uri1 = p.getProperty("uri1");
			String uri2 = p.getProperty("uri2");
			String uri3 = p.getProperty("uri3");
			String uri4 = p.getProperty("uri4");
			String uri5 = p.getProperty("uri5");
			String uri6 = p.getProperty("uri6");
			String uri7 = p.getProperty("uri7");
			String uri8 = p.getProperty("uri8");
			String uri9 = p.getProperty("uri9");
			String uri10 = p.getProperty("uri10");
			String uri11 = p.getProperty("uri11");
			assertEquals(baseURI + "dist/bundles", uri1);
			assertEquals("file:/dist/bundles", uri2);
			assertEquals(baseURI + "dist/bundles", uri3);
			assertEquals("file:/dist/bundles", uri4);
			assertEquals(otherURI + "dist/bundles", uri5);
			assertEquals("file:/dist/bundles", uri6);
			assertEquals(otherURI + "dist/bundles", uri7);
			assertEquals("file:/dist/bundles", uri8);
			assertEquals("http://foo.com/dist/bundles", uri9);
			assertEquals("http://foo.com/dist/bundles", uri10);
			assertEquals(baseURI, uri11);
			assertTrue(p.check());
		}
	}

	@Test
	public void testUriMacroTooFew() throws IOException {
		try (Processor p = new Processor()) {
			p.setProperty("urix", "${uri}");
			String uri = p.getProperty("urix");
			assertTrue(p.check("too few arguments", "No translation found for macro: uri"));
		}
	}

	@Test
	public void testUriMacroTooMany() throws IOException {
		try (Processor p = new Processor()) {
			p.setProperty("urix", "${uri;file:/dist/bundles;file:/some/dir/;another}");
			String uri = p.getProperty("urix");
			assertTrue(p.check("too many arguments", "No translation found for macro: uri"));
		}
	}

	@Test
	public void testUriMacroNoBase() throws IOException {
		try (Processor p = new Processor()) {
			p.setBase(null);
			p.setProperty("urix", "${uri;dist/bundles}");
			String uri = p.getProperty("urix");
			assertTrue(p.check("No base dir set", "No translation found for macro: uri"));
		}
	}

	@Test
	public void testFileUriMacro() throws Exception {
		try (Processor p = new Processor()) {
			String baseURI = p.getBaseURI()
				.toString();
			File some = new File("generated");
			p.setProperty("uri1", "${fileuri;dist/bundles}");
			p.setProperty("uri2", "${fileuri;" + some.getCanonicalPath() + "/dist/bundles}");
			p.setProperty("uri3", "${fileuri;.}");
			String uri1 = p.getProperty("uri1");
			String uri2 = p.getProperty("uri2");
			String uri3 = p.getProperty("uri3");
			assertEquals(baseURI + "dist/bundles", uri1);
			assertEquals(some.toURI() + "dist/bundles", uri2);
			assertEquals(baseURI, uri3);
			assertTrue(p.check());
		}
	}

	@Test
	public void isInternalTest() {
		assertThat((Predicate<String>) AttributeClasses.MANIFEST).accepts("attribubte", "-foobar")
			.rejects("-internal-key", Constants.SPLIT_PACKAGE_DIRECTIVE, Constants.FROM_DIRECTIVE);
	}

	@Test
	public void toExternalTest() {
		Attrs attrs = new Attrs();
		ExtList<String> value = new ExtList<>("a", "b", "c");
		attrs.putTyped("foo", value);
		attrs.putTyped("-internal-foo", value);
		attrs.putTyped(Constants.SPLIT_PACKAGE_DIRECTIVE, "foobar;strategy=merge-first");
		Attrs ext = attrs.select(AttributeClasses.MANIFEST);

		assertThat(ext).hasSize(1);
		assertThat(ext.getTyped("foo")).isEqualTo(value);
	}

	@Test
	public void testMergAndSuffixes() throws IOException {
		try (Processor p = new Processor()) {
			p.setProperty("foo+", "a,b,c");
			p.setProperty("foo+.1", "x,y,z");
			p.setProperty("foo++", "d,e,f");

			String plus = p.mergeProperties("foo+");
			assertThat(plus).isEqualTo("a,b,c,x,y,z");

			String plusplus = p.mergeProperties("foo++");
			assertThat(plusplus).isEqualTo("d,e,f");
		}

	}

	@Test
	public void testIncludeItself() throws IOException {
		File foo = IO.getFile("generated/foo.bnd");
		IO.store("-include ./foo.bnd\nfoo=1\n", foo);
		try (Processor p = new Processor()) {
			p.setBase(foo.getParentFile());
			p.setProperties(foo);
			assertTrue(p.check("Cyclic or multiple include of"));
		}
	}

}
