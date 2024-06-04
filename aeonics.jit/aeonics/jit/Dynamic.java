package aeonics.jit;

import aeonics.entity.Entity;
import aeonics.template.Item;
import aeonics.template.Parameter;
import aeonics.template.Template;
import aeonics.util.StringUtils;

/**
 * This item allows a component to dynamically compile code and return an instance of the compiled class at runtime.
 */
@SuppressWarnings("rawtypes")
public class Dynamic implements Item<Dynamic.Type>
{
	private static Template<Dynamic.Type> template = new Template<Dynamic.Type>(Dynamic.Type.class, Dynamic.class, Dynamic.class)
	.creator(Dynamic.Type::new)
	.summary("This entity models a dynamic instance provided at runtime")
	.description("The code property of this entity will be compiled at runtime and an instance of the class will be created exactly once.")
	.add(new Parameter("code")
		.bindable(false)
		.summary("The class source code")
		.description("This property should contain the Java code of a class to compile."));
		
	public Template<Dynamic.Type> template()  { return template; }
	
	public static class Type<T extends Item> extends Entity
	{
		/**
		 * Compiles some code at runtime and returns an instance of the compiled class.
		 * This method should only be called once.
		 * @return an instance of the compiled class
		 */
		@SuppressWarnings("unchecked")
		public T compile()
		{
			try
			{
				String code = valueOf("code").asString();
				Object instance = Compiler.compile(code);
				return (T) instance;
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		
		/**
		 * Hardcoded category to the {@link Dynamic} class
		 */
		public final String category() { return StringUtils.toLowerCase(Dynamic.class); }
	}
	
	public Class<Dynamic.Type> entity() { return Dynamic.Type.class; }
}
