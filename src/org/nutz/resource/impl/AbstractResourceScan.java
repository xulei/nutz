package org.nutz.resource.impl;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.nutz.lang.util.Disks;
import org.nutz.lang.util.FileVisitor;
import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.nutz.plugin.Plugin;
import org.nutz.resource.NutResource;
import org.nutz.resource.ResourceScan;

public abstract class AbstractResourceScan implements ResourceScan, Plugin {
	
	private static final Log log = Logs.getLog(AbstractResourceScan.class);
	
	protected List<NutResource> scanInJar(String src, Pattern regex, String jarPath) {
		List<NutResource> list = new ArrayList<NutResource>();
		try {
			JarFile jar = new JarFile(jarPath);
			JarEntry rootEntry = jar.getJarEntry(src);
			if (rootEntry == null)
				return list;
			Enumeration<JarEntry> ens = jar.entries();
			while (ens.hasMoreElements()) {
				JarEntry jen = ens.nextElement();
				if (jen.isDirectory())
					continue;
				String name = jen.getName();
				if (name.startsWith(src) && regex.matcher(name).find()) {
					list.add(new JarEntryResource(jar, jen));
				}
			}
		}
		catch (Throwable e) {
			if (log.isDebugEnabled())
				log.debug("Fail to scan path '" + jarPath + "'!", e);
		}
		return list;
	}
	
	protected List<NutResource> scanInDir(final Pattern regex,final String base,
	          							File f,
	        							final boolean ignoreHidden) {
		final List<NutResource> list = new ArrayList<NutResource>();
		if (null == f || (ignoreHidden && f.isHidden()))
			return list;

		if (!f.isDirectory())
			f = f.getParentFile();

		Disks.visitFile(f, new FileVisitor() {
			public void visit(File file) {
				list.add(new FileResource(base, file));
			}
		}, new FileFilter() {
			public boolean accept(File theFile) {
				if (ignoreHidden && theFile.isHidden())
					return false;
				if (theFile.isDirectory())
					return true;
				return regex == null || regex.matcher(theFile.getName()).find();
			}
		});
		
		return list;
	}
	
	protected static String checkSrc(String src){
		if (src == null)
			return null;
		src = src.replace('\\', '/');
		if (! src.endsWith("/"))
			src += "/";
		return src;
	}
}
