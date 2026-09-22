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
import aeonics.jit.policy.References;
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
	 * Compiles the given code and hands the caller the members and interfaces the resulting bytecode
	 * references, before the class is loaded or instantiated. Throwing from the inspector aborts the
	 * whole compilation and nothing is loaded or run.
	 * @param <T> the compiled instance type
	 * @param code the source code to compile
	 * @param inspector receives the references and may throw to reject the code, or null to skip inspection
	 * @return the compiled instance and its generated module name
	 * @throws Exception if the inspector rejects the code, or compilation fails
	 */
	public static <T> Tuple<T, String> compile(String code, Consumer<References> inspector) throws Exception
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
	private static <T> Tuple<T, String> compile(String className, String code, ClassLoader context, Consumer<References> inspector) throws Exception
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
			// hand the references of every compiled class to the inspector before anything is loaded
			if( inspector != null )
			{
				Set<String> invoked = new HashSet<>();
				Set<String> interfaces = new HashSet<>();
				DynamicClassLoader loader = (DynamicClassLoader) fileManager.getClassLoader(null);
				for( DynamicFileObject.Output o : loader.classes.values() )
					scan(o.bytecode.toByteArray(), invoked, interfaces);
				inspector.accept(new References(module, invoked, interfaces));
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
	 * Reads a compiled class and collects the members it invokes and the interfaces it implements.
	 * <p>
	 * Only these two axes are collected. Types named in descriptors are deliberately ignored: a
	 * parameter, return or field type can only be acted upon through a call, which is reported here
	 * against its own owner. Bare class constants are ignored for the same reason, and because the
	 * {@code InnerClasses} attribute forces the enclosing class of every nested type named anywhere
	 * into the pool, which makes bare constants report types the code never touches. The compiler
	 * emits such an entry for {@code java.lang.invoke.MethodHandles} in every class holding a lambda
	 * or a string concatenation, purely to record that {@code Lookup} nests inside it.
	 * @param bytecode the compiled class bytes
	 * @param invoked collects the invoked members as {@code package.Class.member} in dot form
	 * @param interfaces collects the implemented interface names in dot form
	 * @throws IOException if the bytecode cannot be read
	 */
	private static void scan(byte[] bytecode, Set<String> invoked, Set<String> interfaces) throws IOException
	{
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytecode));
		in.readInt();           // 0xCAFEBABE magic
		in.readUnsignedShort(); // minor version
		in.readUnsignedShort(); // major version
		int count = in.readUnsignedShort();
		String[] utf8 = new String[count];
		int[] classNameIndex = new int[count];
		int[] refClassIndex = new int[count];
		int[] refNameAndTypeIndex = new int[count];
		int[] nameIndex = new int[count];
		for( int i = 1; i < count; i++ )
		{
			int tag = in.readUnsignedByte();
			switch( tag )
			{
				case 1:  utf8[i] = in.readUTF(); break;                                              // Utf8
				case 7:  classNameIndex[i] = in.readUnsignedShort(); break;                          // Class -> name
				case 12: nameIndex[i] = in.readUnsignedShort(); in.readUnsignedShort(); break;       // NameAndType -> name, descriptor
				case 9: case 10: case 11:                                                            // Field/Method/InterfaceMethodref -> class, name and type
					refClassIndex[i] = in.readUnsignedShort();
					refNameAndTypeIndex[i] = in.readUnsignedShort(); break;
				case 8: case 16: case 19: case 20: in.readUnsignedShort(); break;                    // String, MethodType, Module, Package
				case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;                       // MethodHandle
				case 3: case 4: case 17: case 18: in.readInt(); break;                               // Integer, Float, Dynamic, InvokeDynamic
				case 5: case 6: in.readLong(); i++; break;                                           // Long, Double occupy two pool slots
				default: throw new IOException("Unexpected constant pool tag " + tag);
			}
		}

		// a MethodHandle constant points back at a Field/Method/InterfaceMethodref, so lambda bodies,
		// method references and the bootstrap methods behind lambdas and string concatenation are all
		// already covered by the references collected above
		for( int i = 1; i < count; i++ )
		{
			if( refClassIndex[i] == 0 ) continue;
			String type = utf8[classNameIndex[refClassIndex[i]]];
			String member = utf8[nameIndex[refNameAndTypeIndex[i]]];
			if( type == null || member == null ) continue;
			invoked.add(owner(type) + "." + member);
		}

		in.readUnsignedShort(); // access flags
		in.readUnsignedShort(); // this class
		in.readUnsignedShort(); // super class, already covered by the constructor it is chained to
		int n = in.readUnsignedShort();
		for( int i = 0; i < n; i++ )
		{
			String name = utf8[classNameIndex[in.readUnsignedShort()]];
			if( name != null ) interfaces.add(owner(name));
		}
	}

	/**
	 * Converts an owner name in internal {@code java/lang/Foo} form to dot form. An array owner is
	 * reduced to its element type, and a primitive array to {@link Object} since it declares no
	 * member of its own.
	 * @param name the internal owner name
	 * @return the owner name in dot form
	 */
	private static String owner(String name)
	{
		int dims = 0;
		while( dims < name.length() && name.charAt(dims) == '[' ) dims++;
		if( dims == 0 ) return name.replace('/', '.');
		if( dims < name.length() && name.charAt(dims) == 'L' && name.endsWith(";") )
			return name.substring(dims + 1, name.length() - 1).replace('/', '.');
		return "java.lang.Object";
	}
}
