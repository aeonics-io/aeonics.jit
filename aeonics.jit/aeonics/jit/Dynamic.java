package aeonics.jit;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;

import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.jit.Compiler.CompileException;
import aeonics.template.Factory;
import aeonics.template.Item;
import aeonics.template.Parameter;
import aeonics.template.Relationship;
import aeonics.util.Functions.Supplier;
import aeonics.util.Tuples.Tuple;
import aeonics.util.StringUtils;

/**
 * This item allows a component to dynamically compile code and return an instance of the compiled entity at runtime.
 */
public class Dynamic extends Item<Dynamic.Type>
{
	public static class Template extends aeonics.template.Template<Dynamic.Type>
	{
		public Template(Class<? extends Dynamic.Type> target, Class<? extends Dynamic> type)
		{
			super(target, type, Dynamic.class);
			summary("This entity models a dynamic instance provided at runtime");
			description("The code property of this entity will be compiled at runtime and an instance of the class will be created exactly once.");
			add(new Parameter("code")
				.bindable(false)
				.summary("The class source code")
				.description("This property should contain the Java code of a class to compile. The compiled class should be a supplier of entity.")
				.format(Parameter.Format.CODE)
				);
			add(new Relationship("child")
				.summary("Linked entity")
				.description("Link to the dynamically generated entity. The target category will be modified at runtime to reflect the category of the generated entity.")
				.category(Dynamic.class)
				.min(0).max(1));
			onCreate((data, instance) ->
			{
				if( !data.get("parameters").isEmpty("code") )
					instance.compile();
			});
			onUpdate((data, instance) ->
			{
				if( !data.get("parameters").isEmpty("code") )
					instance.compile();
			});
		}
	}
	
	@Override
	public Dynamic.Template template()
	{
		return new Dynamic.Template(target(), this.getClass())
			.creator(creator());
	}
	
	public static class Type extends Entity implements Closeable
	{
		public void close() { cleanup(); }
		private void cleanup()
		{
			// cleanup the entity from the registry
			// and cleanup the factory in case it is linked to this entity
			Entity e = entity.getAndSet(null);
			compileError = null;
			if( e != null )
			{
				removeRelation("child", e);
				Registry.of(e.category()).remove(e.id());
				
				if( e.type().startsWith(module()) )
					Factory.of(e.category()).remove(e.type());
			}
		}
		
		private AtomicReference<Entity> entity = new AtomicReference<>();
		private String module = null;
		private Data compileError = null;
		
		/**
		 * Returns the last compilation error, or null if everything went well.
		 * @return the last compilation error, or null if everything went well.
		 */
		public Data error() { return compileError; } 
		
		/**
		 * Returns the last compiled entity, or null if an error happened
		 * @return the last compiled entity, or null if an error happened
		 */
		public Entity entity() { return entity.get(); }
		
		/**
		 * Returns the generated module name of the new entity
		 * @return the generated module name of the new entity
		 */
		public String module() { return module; }
		
		/**
		 * Returns the category of the newly created entity or null if an error happened
		 * @return the category of the newly created entity or null if an error happened
		 */
		public String relatedCategory()
		{
			Entity e = entity.get();
			if( e == null ) return null;
			else return e.category();
		}
		
		/**
		 * Compiles some code at runtime and returns an instance of the compiled class.
		 * This method should only be called once the source code has changed.
		 * In case of compilation error, the {@link #error()} method returns the error.
		 * @return an instance of the compiled class or null if an error happened.
		 * @throws CompileException if the compilation fails. The details are provided in the exception data.
		 * @throws Exception if something else happens
		 */
		@SuppressWarnings("unchecked")
		public synchronized Entity compile() throws CompileException, Exception
		{
			cleanup();
			
			// compile the new code
			try
			{
				String code = valueOf("code").asString();
				Tuple<Object, String> instance = Compiler.compile(code);
				Supplier<Entity> supplier = (Supplier<Entity>) instance.a;
				Entity e = supplier.get();
				if( e == null ) return null;
				
				module = instance.b;
	
				entity.set(e);
				
				// update the relationship to reflect this entity's category
				this.removeRelation("child");
				this.defineRelation(new Relationship("child").category(e.category()));
				this.addRelation("child", e);
				
				return e;
			}
			catch(CompileException ce)
			{
				compileError = ce.data;
				return null;
			}
		}
		
		/**
		 * Hardcoded category to the {@link Dynamic} class
		 */
		@Override
		public final String category() { return StringUtils.toLowerCase(Dynamic.class); }
		
		@Override
		public Data export()
		{
			return super.export()
				.put("related_module", module())
				.put("related_category", relatedCategory())
				;
		}
		
		@Override
		public Data snapshot()
		{
			Data s = super.snapshot()
				.put("related_module", module());
			s.remove("relationships");
			return s;
		}
	}
	
	protected Class<? extends Type> defaultTarget() { return Dynamic.Type.class; }
	protected java.util.function.Supplier<? extends Type> defaultCreator() { return Dynamic.Type::new; }
	protected Class<? extends Item<? super Type>> category() { return Dynamic.class; }
}
