package aeonics.jit;

import java.util.HashMap;
import java.util.Map;

import aeonics.Plugin;

class DynamicClassLoader extends ClassLoader
{
	Map<String, DynamicFileObject.Output> classes = new HashMap<>();
	
	public DynamicFileObject.Output add(DynamicFileObject.Output file)
	{
		String name = file.getName().replace('/', '.');
		name = name.substring(1, name.length()-6);
		classes.put(name, file);
		return file;
	}
	
	public DynamicClassLoader(String moduleName)
	{
		super(moduleName, Plugin.class.getClassLoader());
	}
	
	protected Class<?> findClass(String name) throws ClassNotFoundException 
	{
		Class<?> existing = findLoadedClass(name);
		if( existing != null ) return existing;
		
		if( classes.containsKey(name) )
		{
			final byte[] b = classes.get(name).bytecode.toByteArray();
			existing = super.defineClass(name, b, 0, b.length);
			
			return existing;
		}
		
        return super.findClass(name);
    }
	
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
	{
		Class<?> c;
		
		try { c = super.loadClass(name, resolve); return c; }
		catch (ClassNotFoundException e) { /* ignore */ }
		
		c = recursiveLoadClass(Plugin.getModuleLayer(), name);
		if( c != null ) return c;
		
		throw new ClassNotFoundException(name);
	}
	
	private Class<?> recursiveLoadClass(ModuleLayer layer, String name)
	{
		Class<?> c;
		
		// first check own modules
		
		for( Module module : layer.modules() )
		{
			ClassLoader cl = module.getClassLoader();
			if( cl == null ) continue;
			
			try { c = module.getClassLoader().loadClass(name); return c; }
			catch (ClassNotFoundException e) { /* ignore */ }
		}
		
		// then try in parent layers
		
		for( ModuleLayer parent : layer.parents() )
		{
			c = recursiveLoadClass(parent, name);
			if( c != null ) return c;
		}
		
		return null;
	}
}
