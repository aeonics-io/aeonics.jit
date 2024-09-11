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
	
	private static final Endpoint.Rest.Type list = new Endpoint.Rest() { }
		.template()
		.returns("A list of dynamic entities repesentation.")
		.summary("Search dynamic entities by category")
		.description("Returns the list of dynamic elemnts that have a generated entity part of the specified category.")
		.add(new Parameter("category")
			.summary("Category")
			.description("The related entity category")
			.format(Parameter.Format.TEXT))
		.create()
		.<Rest.Type>cast()
		.process((params) -> {
			Data list = Data.list();
			for( Dynamic.Type d : Registry.of(Dynamic.class) )
				if( params.asString("category").equals(d.relatedCategory()) )
					list.add(d.export());
			return list;
		})
		.url(ROOT + "{category}")
		.method("GET")
		;
	
	private static final Endpoint.Rest.Type dynamic = new Endpoint.Rest() { }
		.template()
		.returns("In case the compilation is successful, the response contains the id of the new dynamic entity, the genetated entity category, and the generated entity id. "
				+ "In case of error, a 413 status code is returned with the error property set to the compilation error details.")
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
			boolean isNewDynamicEntity = true;
			Dynamic.Type instance = null;
			
			try
			{
				if( !parameters.isEmpty("id") )
				{
					instance = Registry.of(Dynamic.class).get(parameters.asString("id"));
					if( instance == null ) throw new HttpException(413, "Invalid dynamic entity id to update");
					instance.parameter("code", parameters.get("code"));
					isNewDynamicEntity = false;
				}
				else
					instance = Factory.of(Dynamic.class).get(Dynamic.class).create(Data.map().put("parameters", Data.map().put("code", parameters.get("code"))));
				
				instance.compile();
				Entity entity = instance.entity();
				
				return Data.map()
					.put("id", instance.id())
					.put("entity_category", entity.category())
					.put("entity_id", entity.id());
			}
			catch(CompileException ce)
			{
				// in case it is a new entity, delete it if it failed
				if( isNewDynamicEntity && instance != null )
					Registry.of(Dynamic.class).remove(instance.id());
				throw new HttpException(413, Data.map().put("error", ce.data));
			}
			catch(Exception e)
			{
				// in case it is a new entity, delete it if it failed
				if( isNewDynamicEntity && instance != null )
					Registry.of(Dynamic.class).remove(instance.id());
				throw new HttpException(400, e);
			}
		})
		.url(ROOT + "entity")
		.method("POST")
		;
}
