package local;

import aeonics.Plugin;
import aeonics.data.Data;
import aeonics.entity.Action;
import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.http.Router;
import aeonics.jit.Dynamic;
import aeonics.manager.Config;
import aeonics.manager.Lifecycle;
import aeonics.manager.Lifecycle.Phase;
import aeonics.manager.Manager;
import aeonics.util.Callback;

public class Main extends Plugin
{
	public String summary() { return "Jit v1.0"; }
	public String description() { return "Provides dynamic runtime compilation and execution of components from simple source files."; }
	
	public void start()
	{
		Manager.of(Lifecycle.class).on(Phase.LOAD, Callback.once(() -> onLoad()));
		Manager.of(Lifecycle.class).on(Phase.RUN, Callback.once(() -> onRun()));
	}
	
	private static void onLoad()
	{
	}
	
	private static void onRun()
	{
		String code = ""
		+ "import aeonics.http.Endpoint;\n"
		+ "import aeonics.data.Data;\n"
		+ "import aeonics.entity.*;\n"
		+ "import java.util.function.Supplier;\n"
		+ "public class Microservice implements Supplier<Endpoint.Rest.Type> {\n"
		+ "    public Endpoint.Rest.Type get() {\n"
		+ "        return new Endpoint.Rest() { }\n"
		+ "            .template()"
		+ "            .summary(\"test\")"
		+ "            .description(\"test\")"
		+ "            .build().<Endpoint.Rest.Type>cast().process((parameters) -> {\n"
		+ " 				Data users = Data.list();\n"
		+ " 				for( Entity e : Registry.of(\"aeonics.entity.security.user\") ) users.add(e.export());\n"
		+ "					return users;\n"
		+ "            })\n"
		+ "            .url(\"/api/test\")"
		+ "            .method(\"GET\");"
		+ "    }\n"
		+ "}";
		Entity e = new Dynamic().template()
			.build(Data.map().put("code", code))
			.entity();
		
		Action.Type router = Registry.of(Action.class).get(Manager.of(Config.class).get(Router.class, "default").asString());
		router.addRelation("endpoints", e);
	}
}
