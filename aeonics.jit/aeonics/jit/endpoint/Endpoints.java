package aeonics.jit.endpoint;

import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.http.Endpoint;
import aeonics.http.Endpoint.Rest;
import aeonics.http.HttpException;
import aeonics.jit.Compiler.CompileException;
import aeonics.jit.Dynamic;
import aeonics.template.Factory;
import aeonics.template.Parameter;

@SuppressWarnings("unused")
public class Endpoints
{
	private Endpoints() { /* no instances */ }
	
	private static final String ROOT = "/api/jit/";
	
	public static void register()
	{
		// calling this method will force initialization of all private static members
		// all endpoints will be added to the registry automatically
	}
	
	private static final Endpoint.Rest.Type dynamic = new Endpoint.Rest() { }
		.template()
		.summary("Create a new entity from source code")
		.description("This endpoint compiles the provided source code and registers the entity instance in the registry."
			+ "The endpoint returns the newly created entity id and category.")
		.add(new Parameter("code").optional(false).min(1).max(512000)
			.summary("The source code")
			.description("The source code must implement the Supplier<Entity> interface.")
			.format(Parameter.Format.CODE)
			)
		.add(new Parameter("id").optional(true).rule(Parameter.Rule.ID)
			.summary("Dynamic entity id")
			.description("If provided, this parameter specifies the existing dynamic entity to update.")
			.format(Parameter.Format.TEXT)
			.rule(Parameter.Rule.ID)
			)
		.create()
		.<Rest.Type>cast()
		.process((parameters) ->
		{
			try
			{
				Dynamic.Type instance = null;
				if( parameters.isEmpty("id") )
				{
					instance = Registry.of(Dynamic.class).get(parameters.asString("id"));
					if( instance == null ) throw new HttpException(413, "Invalid dynamic entity id to update");
					Factory.of(Dynamic.class).get(Dynamic.class).update(Data.map().put("code", parameters.get("code")), instance);
				}
				else
					instance = Factory.of(Dynamic.class).get(Dynamic.class).create(Data.map().put("code", parameters.get("code")));
				
				Entity entity = instance.entity();
				Registry.add(entity);
				
				return Data.map()
					.put("id", instance.id())
					.put("entity_category", entity.category())
					.put("entity_id", entity.id());
			}
			catch(CompileException ce)
			{
				throw new HttpException(413, ce.data);
			}
			catch(Exception e)
			{
				throw new HttpException(400, e);
			}
		})
		.url(ROOT + "entity")
		.method("POST")
		;
}
