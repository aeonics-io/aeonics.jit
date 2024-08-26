package aeonics.jit;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.JavaFileObject.Kind;

import aeonics.Plugin;
import aeonics.manager.Logger;
import aeonics.manager.Manager;

class VolatileFileManager extends ForwardingJavaFileManager<JavaFileManager>
{
	private String currentModuleName;
	private DynamicClassLoader currentClassLoader;
	private ModuleFinder pluginsFinder;
	
	public VolatileFileManager(String moduleName, JavaFileManager parent)
	{
		super(parent);
		currentModuleName = moduleName;
		currentClassLoader = new DynamicClassLoader(moduleName);
		
		String path = System.getProperty("AEONICS_PLUGIN_PATH");
		if( path == null || path.isBlank() ) path = System.getenv("AEONICS_PLUGIN_PATH");
		if( path == null || path.isBlank() ) path = "plugins";
		
		// locate all modules for compilation only
		pluginsFinder = ModuleFinder.of(Paths.get(path));
		pluginsFinder.findAll();
	}
	
	@Override
	public Location getLocationForModule(Location location, String moduleName) throws IOException
	{
		Location l = super.getLocationForModule(location, moduleName);
		if( l != null ) return l;
		
		return new DynamicLocation.Plugin(moduleName);
	}
	
	@Override
	public Location getLocationForModule(Location location, JavaFileObject fo) throws IOException
	{
		if( location == StandardLocation.MODULE_SOURCE_PATH && fo instanceof DynamicFileObject.Source )
			return new DynamicLocation.Source((DynamicFileObject.Source)fo);
		if( location == StandardLocation.CLASS_OUTPUT && fo instanceof DynamicFileObject.Source )
			return new DynamicLocation.Source((DynamicFileObject.Source)fo);
		return super.getLocationForModule(location, fo);
	}

	@Override
	public boolean contains(Location location, FileObject fo) throws IOException
	{
		if( location instanceof DynamicLocation.Plugin && fo instanceof JavaFileObject )
			return getLocationForModule(location, (JavaFileObject) fo) != null;
	    return super.contains(location, fo);
	}
	
	@Override
	public String inferModuleName(Location location) throws IOException
	{
		if(location instanceof DynamicLocation.Plugin )
			return location.getName();
		if( location instanceof DynamicLocation.Source )
			return currentModuleName;
	    return super.inferModuleName(location);
	}
	
	@Override
	public Iterable<Set<Location>> listLocationsForModules(Location location) throws IOException
	{
		if( location == StandardLocation.MODULE_PATH )
		{
			List<Set<Location>> locations = new ArrayList<>();
			for( Set<Location> l : fileManager.listLocationsForModules(location) )
				locations.add(l);
			Set<Location> pl = new HashSet<Location>();
			for( Plugin p : Plugin.all() )
			{
				Location ml = getLocationForModule(location, p.name());
				if( ml != null )
					pl.add(ml);
			}
			if( pl.size() > 0 )
				locations.add(pl);
			
			return locations;
		}
		return fileManager.listLocationsForModules(location);
    }
	
	@Override
	public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException
	{
		if( location instanceof DynamicLocation.Plugin && kinds.contains(JavaFileObject.Kind.CLASS) )
		{
			Path root = Path.of(packageName.replace('.', '/'));
			
			List<JavaFileObject> list = new LinkedList<>();
			
			pluginsFinder.findAll().forEach(mr ->
			{
				if( !mr.descriptor().name().equals(location.getName()) ) return;
				
				try
				{
					List<JavaFileObject> l = mr.open().list()
						.filter(name -> name.endsWith(".class"))
						.filter(name -> Path.of(name).startsWith(root) && (recurse || Path.of(name).getParent().equals(root)) )
						.map(name -> {
							try { return new DynamicFileObject.Plugin(mr, name); }
							catch(Exception e) { return null; }
						})
						.filter(Objects::nonNull)
						.collect(Collectors.toList());
					
					if( !l.isEmpty() )
						list.addAll(l);
				}
				catch(Exception e)
				{
					Manager.of(Logger.class).warning(Compiler.class, e);
				}
			});
			
			if( list != null && !list.isEmpty() ) return list;
		}
		
		return super.list(location, packageName, kinds, recurse);
	}
	
	@Override
	public String inferBinaryName(Location location, JavaFileObject file)
	{
		if( file instanceof DynamicFileObject.Plugin )
			return ((DynamicFileObject.Plugin) file).binaryName();
		else 
			return fileManager.inferBinaryName(location, file);
	}
	
	@Override
	public boolean hasLocation(Location location)
	{
		if( location instanceof DynamicLocation.Plugin ) return true;
		if( location instanceof DynamicLocation.Source ) return true;
		if( location instanceof DynamicLocation.Output ) return true;
		if( location == StandardLocation.CLASS_OUTPUT ) return true;
		if( location == StandardLocation.MODULE_SOURCE_PATH ) return true;
		
		return fileManager.hasLocation(location);
	}
	
	@Override
	public ClassLoader getClassLoader(final Location location)
	{
		return currentClassLoader;
	}
	
	@Override
	public JavaFileObject getJavaFileForOutput(final Location location, final String className, final JavaFileObject.Kind kind, final FileObject sibling) throws IOException
	{
		return currentClassLoader.add(new DynamicFileObject.Output(className));
	}
	
	@Override
	public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException
	{
		if( location instanceof DynamicLocation.Source )
		{
			if( location.getName().equals("/" + className + kind.extension) )
				return ((DynamicLocation.Source)location).source();
			else
				return null;
		}
		
		if( location instanceof DynamicLocation.Plugin )
		{
			return new DynamicFileObject.Plugin(pluginsFinder.find(((DynamicLocation.Plugin)location).getName()).get(), className);
		}
		
		return super.getJavaFileForInput(location, className, kind);
	}
	
	@Override
	public boolean isSameFile(FileObject a, FileObject b)
	{
		return a != null && b != null && (a.equals(b) || super.isSameFile(a, b));
	}
}