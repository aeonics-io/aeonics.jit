package local;

import aeonics.Plugin;
import aeonics.jit.Dynamic;
import aeonics.manager.Lifecycle;
import aeonics.manager.Lifecycle.Phase;
import aeonics.manager.Manager;
import aeonics.template.Factory;
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
		Factory.add(new Dynamic());
	}
	
	private static void onRun()
	{
		aeonics.jit.endpoint.Endpoints.register();
	}
}
