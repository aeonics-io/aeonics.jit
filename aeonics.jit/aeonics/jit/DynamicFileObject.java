package aeonics.jit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.module.ModuleReference;
import java.net.URI;

import javax.tools.SimpleJavaFileObject;

class DynamicFileObject
{
	private DynamicFileObject() {}
	
	static class Source extends SimpleJavaFileObject
	{
		private String code;
		
		Source(String name, String code)
		{
			super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
			this.code = code;
		}
		
		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors)
		{
			return code;
		}
		
		@Override
		public boolean equals(Object o)
		{
			if( o == this ) return true;
			if( o == null || !(o instanceof Source) ) return false;
			return getName().equals(((Source)o).getName()) && ((Source)o).code.equals(this.code);
		}
	}
	
	static class Plugin extends SimpleJavaFileObject
	{
		private ModuleReference reference;
		
		public Plugin(ModuleReference reference, String className)
		{
			this(URI.create("jit://" + reference.descriptor().name() + "/" + 
				(className.endsWith(Kind.CLASS.extension) ? 
					className :
					className.replace('.', '/') + Kind.CLASS.extension
				)
			));
			this.reference = reference;
		}
		
		private Plugin(URI uri)
		{
			super(uri, Kind.CLASS);
		}
		
		public String binaryName() 
		{
			String n = getName();
			return n.substring(1, n.length()-6).replace('/', '.');
		}
		
		@Override
		public InputStream openInputStream() throws IOException
		{
			String url = "jar:" + reference.location().get() + "!" + toUri().getPath();
			return URI.create(url).toURL().openStream();
		}
		
		@Override
		public Reader openReader(boolean ignoreEncodingErrors) throws IOException
		{
			return new InputStreamReader(openInputStream());
		}
		
		@Override
		public boolean equals(Object o)
		{
			if( o == this ) return true;
			if( o == null || !(o instanceof Plugin) ) return false;
			return this.toUri().equals(((Plugin)o).toUri());
		}
	}
	
	static class Output extends SimpleJavaFileObject
	{
		ByteArrayOutputStream bytecode = new ByteArrayOutputStream();
		
		Output(String name)
		{
			super(URI.create("string:///" + name.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
		}
		
		@Override
		public OutputStream openOutputStream() throws IOException
		{
			return bytecode;
		}
		
		@Override
		public boolean equals(Object o)
		{
			if( o == this ) return true;
			if( o == null || !(o instanceof Output) ) return false;
			return getName().equals(((Output)o).getName());
		}
	}
}
