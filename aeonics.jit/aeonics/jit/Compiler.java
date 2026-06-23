package aeonics.jit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import aeonics.util.Functions.Consumer;
import aeonics.util.Tuples.Tuple;

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
		for(Plugin p : Plugin.all()) 
			moduleInfo += "requires " + p.name() + "; ";
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
		for(Plugin p : Plugin.all())
			j.add(p.name());
		options.add(j.toString());
		
		return options;
	}
	
	public static <T> Tuple<T, String> compile(String code) throws Exception
	{
		return compile(code, null);
	}

	/**
	 * Compiles the given code and hands the caller every class the resulting bytecode references,
	 * before the class is loaded or instantiated. The inspector receives the referenced class names
	 * in dot form; throwing from it aborts the whole compilation and nothing is loaded or run.
	 * @param <T> the compiled instance type
	 * @param code the source code to compile
	 * @param inspector receives the referenced class names and may throw to reject the code, or null to skip inspection
	 * @return the compiled instance and its generated module name
	 * @throws Exception if the inspector rejects the code, or compilation fails
	 */
	public static <T> Tuple<T, String> compile(String code, Consumer<Set<String>> inspector) throws Exception
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
		
		return compile(className, code, aeonics.Plugin.class.getClassLoader(), inspector);
	}

	@SuppressWarnings("unchecked")
	private static <T> Tuple<T, String> compile(String className, String code, ClassLoader context, Consumer<Set<String>> inspector) throws Exception
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
				new DynamicFileObject.Source(module + "." + className, "package " + module + "; " + code)
				)
			);
		
		if( task.call() )
		{
			// hand every class the compiled bytecode references to the inspector before anything is loaded
			if( inspector != null )
			{
				Set<String> involved = new HashSet<>();
				DynamicClassLoader loader = (DynamicClassLoader) fileManager.getClassLoader(null);
				for( DynamicFileObject.Output o : loader.classes.values() )
					involved.addAll(referencedClasses(o.bytecode.toByteArray()));
				inspector.accept(involved);
			}

			try
			{
				Class<?> z = fileManager.getClassLoader(null).loadClass(module + "." + className);
				Constructor<?> x = z.getConstructor();
				T instance = (T)x.newInstance();
				return Tuple.of(instance, module);
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

	/**
	 * Reads the constant pool of a compiled class and returns the dot-form names of every class it
	 * references: supertypes, interfaces, field and method owners, and the object types named in
	 * field and method descriptors.
	 * @param bytecode the compiled class bytes
	 * @return the referenced class names in dot form
	 * @throws IOException if the bytecode cannot be read
	 */
	private static Set<String> referencedClasses(byte[] bytecode) throws IOException
	{
		Set<String> out = new HashSet<>();
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytecode));
		in.readInt();           // 0xCAFEBABE magic
		in.readUnsignedShort(); // minor version
		in.readUnsignedShort(); // major version
		int count = in.readUnsignedShort();
		String[] utf8 = new String[count];
		int[] classNameIndex = new int[count];
		int[] descriptorIndex = new int[count];
		for( int i = 1; i < count; i++ )
		{
			int tag = in.readUnsignedByte();
			switch( tag )
			{
				case 1:  utf8[i] = in.readUTF(); break;                                              // Utf8
				case 7:  classNameIndex[i] = in.readUnsignedShort(); break;                          // Class -> name
				case 8:  in.readUnsignedShort(); break;                                              // String
				case 16: descriptorIndex[i] = in.readUnsignedShort(); break;                         // MethodType -> descriptor
				case 19: case 20: in.readUnsignedShort(); break;                                     // Module, Package
				case 12: in.readUnsignedShort(); descriptorIndex[i] = in.readUnsignedShort(); break; // NameAndType -> name, descriptor
				case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;                       // MethodHandle
				case 3: case 4: case 9: case 10: case 11: case 17: case 18: in.readInt(); break;     // Integer, Float, Field/Method/InterfaceMethodref, Dynamic, InvokeDynamic
				case 5: case 6: in.readLong(); i++; break;                                           // Long, Double occupy two pool slots
				default: throw new IOException("Unexpected constant pool tag " + tag);
			}
		}
		for( int i = 1; i < count; i++ )
		{
			if( classNameIndex[i] != 0 ) addClassName(out, utf8[classNameIndex[i]]);
			if( descriptorIndex[i] != 0 ) addDescriptorTypes(out, utf8[descriptorIndex[i]]);
		}
		return out;
	}

	/**
	 * Adds a class name in internal {@code java/lang/Foo} or array {@code [Ljava/lang/Foo;} form to
	 * the set in dot form, skipping primitive arrays.
	 * @param out the set to add to
	 * @param name the internal class name
	 */
	private static void addClassName(Set<String> out, String name)
	{
		if( name == null || name.isEmpty() ) return;
		int dims = 0;
		while( dims < name.length() && name.charAt(dims) == '[' ) dims++;
		if( dims > 0 )
		{
			if( dims < name.length() && name.charAt(dims) == 'L' && name.endsWith(";") )
				out.add(name.substring(dims + 1, name.length() - 1).replace('/', '.'));
			return;
		}
		out.add(name.replace('/', '.'));
	}

	/**
	 * Adds every {@code L...;} object type named in a field or method descriptor to the set in dot form.
	 * @param out the set to add to
	 * @param descriptor the field or method descriptor
	 */
	private static void addDescriptorTypes(Set<String> out, String descriptor)
	{
		if( descriptor == null ) return;
		int i = 0;
		while( (i = descriptor.indexOf('L', i)) >= 0 )
		{
			int end = descriptor.indexOf(';', i);
			if( end < 0 ) break;
			out.add(descriptor.substring(i + 1, end).replace('/', '.'));
			i = end + 1;
		}
	}
}
