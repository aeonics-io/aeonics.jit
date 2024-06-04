package aeonics.jit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.Arrays;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import aeonics.data.Data;

public class Compiler
{
	public static class CompileException extends RuntimeException
	{
		public Data data;
		public CompileException(Data d) { data = d; }
		public String toString() { return data.toString(); }
	}
	
	public static <T> T compile(String code, ClassLoader context)
	{
		return compile(null, code, context);
	}
	
	public static <T> T compile(String code)
	{
		return compile(null, code, null);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T compile(String className, String code, ClassLoader context)
	{
		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		JavaFileManager fileManager = javac.getStandardFileManager(null, null, null);
		
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
		
		if( javac.getTask(logwriter, null, diagnostics, null, null, Arrays.asList(new Source(className, code))).call() )
		{
			try
			{
				ClassLoader c = fileManager.getClassLoader(javax.tools.StandardLocation.CLASS_PATH);
				Class<?> z = c.loadClass(className);
				Constructor<?> x = z.getConstructor();
				return ((T)x.newInstance());
			}
			catch(Exception e)
			{
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
	
	private static class Source extends SimpleJavaFileObject
	{
		final String code;
		Source(String name, String code) { super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE); this.code = code; }
		public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
	}
	
	private static Writer logwriter = new Writer()
	{
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		public void write(char[] cbuf, int off, int len) throws IOException { data.write(new String(cbuf).getBytes(), off, len); }
		public synchronized void flush() throws IOException { if( data.size() == 0 ) return; System.out.println(data.toString()); data.reset(); }
		public void close() throws IOException { flush(); }
	};
}
