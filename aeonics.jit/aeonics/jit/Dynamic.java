package aeonics.jit;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.template.Factory;
import aeonics.template.Item;
import aeonics.template.Parameter;
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
				.description("This property should contain the Java code of a class to compile. The compiled class should be a supplier of entity."));
			
		}
	}
	
	@Override
	public Dynamic.Template template()
	{
		Dynamic.Template t = new Dynamic.Template(target(), this.getClass())
			.creator(creator())
			.builder((data, instance) ->
			{
				Registry.add(instance);
				if( instance instanceof Dynamic.Type )
					((Dynamic.Type)instance).compile();
			})
			.modifier((data, instance) ->
			{
				if( instance instanceof Dynamic.Type )
				{
					Entity e = ((Dynamic.Type)instance).entity.get();
					if( e != null )
						Registry.of(e.category()).remove(e.id());
					((Dynamic.Type)instance).compile();
				}
			});
		return (Dynamic.Template) Factory.add(t);
	}
	
	public static class Type extends Entity
	{
		private AtomicReference<Entity> entity = new AtomicReference<>();
		
		public Entity entity() { return entity.get(); }
		
		/**
		 * Compiles some code at runtime and returns an instance of the compiled class.
		 * This method should only be called once.
		 * @return an instance of the compiled class
		 */
		@SuppressWarnings("unchecked")
		private synchronized Entity compile()
		{
			Entity e = entity.get();
			if( e != null ) return e;
				
			try
			{
				String code = valueOf("code").asString();
				Object instance = Compiler.compile(code);
				Supplier<Entity> supplier = (Supplier<Entity>) instance;
				e = supplier.get();

				Registry.add(e);
				entity.set(e);
				
				return e;
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
				Manager.of(Logger.class).warning(Dynamic.class, ex);
				return null;
			}
		}
		
		/**
		 * Hardcoded category to the {@link Dynamic} class
		 */
		@Override
		public final String category() { return StringUtils.toLowerCase(Dynamic.class); }
	}
	
	protected Class<? extends Type> defaultTarget() { return Dynamic.Type.class; }
	protected Supplier<? extends Type> defaultCreator() { return Dynamic.Type::new; }
	protected Class<? extends Item<? super Type>> category() { return Dynamic.class; }
}
