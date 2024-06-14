package aeonics.jit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import aeonics.Plugin;
import aeonics.data.Data;
import aeonics.manager.Logger;
import aeonics.manager.Manager;

public class Compiler
{
	public static class CompileException extends RuntimeException
	{
		public Data data;
		public CompileException(Data d) { data = d; }
		public String toString() { return data.toString(); }
	}
	
	private static String generateUniqueModuleName()
	{
		long now = System.nanoTime();
		if( now < 0 ) now *= -1;
		return "_m_" + now + "_";
	}
	
	private static Writer getLogWriter()
	{
		return new Writer()
		{
			ByteArrayOutputStream data = new ByteArrayOutputStream();
			public void write(char[] cbuf, int off, int len) throws IOException { data.write(new String(cbuf).getBytes(), off, len); }
			public synchronized void flush() throws IOException { if( data.size() == 0 ) return; System.out.println(data.toString()); data.reset(); }
			public void close() throws IOException { flush(); }
		};
	}
	
	private static String getModuleInfo(String moduleName)
	{
		String moduleInfo = "module " + moduleName + " { ";
		for(String p : Plugin.all()) moduleInfo += "requires " + p + "; ";
		moduleInfo += "}";
		
		return moduleInfo;
	}
	
	private static List<String> getCompilerOptions(String module)
	{
		List<String> options = new ArrayList<>();
		// we need to specify the module name here to force the compiler to compile as a module
		options.add("--module"); options.add(module);
		// we need to specify the output directory even though we do not use it
		options.add("-d"); options.add("X");
		// we need to specify the module source directory even though we do not use it
		options.add("--module-source-path"); options.add("X");
		// we need to include all modules so that the compiler is aware of them
		options.add("--add-modules");
		StringJoiner j = new StringJoiner(",");
		for(String p : Plugin.all()) j.add(p);
		options.add(j.toString());
		
		return options;
	}
	
	public static <T> T compile(String code)
	{
		String className = null;
		
		// try to guess the className. This is not bullet proof but should allow "normal" code.
		if( className == null )
		{
			className = "";
			int ic = code.indexOf("class ");
			int ip = code.indexOf("package ");
			if( ip > -1 && ip < ic ) className = code.substring(ip+8, code.indexOf(';', ip+9)) + ".";
			if( ic > -1 )
			{
				ip = code.length();
				int tmp = code.indexOf(' ', ic+7);
				if( tmp > -1 && tmp < ip ) ip = tmp;
				tmp = code.indexOf('<', ic+7);
				if( tmp > -1 && tmp < ip ) ip = tmp;
				tmp = code.indexOf('{', ic+7);
				if( tmp > -1 && tmp < ip ) ip = tmp;
				tmp = code.indexOf('\r', ic+7);
				if( tmp > -1 && tmp < ip ) ip = tmp;
				tmp = code.indexOf('\n', ic+7);
				if( tmp > -1 && tmp < ip ) ip = tmp;
				tmp = code.indexOf('/', ic+7);
				if( tmp > -1 && tmp < ip ) ip = tmp;
				className += code.substring(ic+6, ip);
			}
		}
		
		return compile(className, code, aeonics.Plugin.class.getClassLoader());
	}
	
	@SuppressWarnings("unchecked")
	private static <T> T compile(String className, String code, ClassLoader context)
	{
		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		if( javac == null )
		{
			Manager.of(Logger.class).warning(Dynamic.class, "Compilation is not possible: no Java compiler available");
			throw new IllegalStateException("No Java compiler available");
		}
		
		String module = generateUniqueModuleName();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		VolatileFileManager fileManager = new VolatileFileManager(module, javac.getStandardFileManager(null, null, null));
		
		CompilationTask task = javac.getTask(
			getLogWriter(), 
			fileManager, 
			diagnostics, 
			getCompilerOptions(module), 
			null, 
			Arrays.asList(
				new DynamicFileObject.Source("module-info", getModuleInfo(module)), 
				new DynamicFileObject.Source(className, "package " + module + ";\n" + code)
				)
			);
		
		if( task.call() )
		{
			try
			{
				Class<?> z = fileManager.getClassLoader(null).loadClass(module + "." + className);
				Constructor<?> x = z.getConstructor();
				T instance = (T)x.newInstance();
				return instance;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				throw new IllegalArgumentException(e);
			}
		}
		else
		{
			Data error = Data.list();
			for( Diagnostic<?> d : diagnostics.getDiagnostics() )
				error.add(Data.list().add(d.getKind()).add(d.getLineNumber()).add(d.getColumnNumber()).add(d.getMessage(null)));
			throw new CompileException(error);
		}
	}
}
