package local;

import aeonics.Plugin;
import aeonics.jit.Dynamic;
import aeonics.jit.policy.Policy;
import aeonics.manager.Lifecycle;
import aeonics.manager.Lifecycle.Phase;
import aeonics.template.Factory;

public class Main extends Plugin
{
	public String summary() { return "Jit v1.0.0"; }
	public String description() { return "Aeonics Just-In-Time Compilation"; }
	
	public void start()
	{
		Lifecycle.on(Phase.LOAD, this::onLoad);
		Lifecycle.on(Phase.RUN, this::onRun);
	}
	
	private void onLoad()
	{
		Factory.add(new Dynamic());
		Factory.add(new Policy());
	}
	
	private void onRun()
	{
		aeonics.jit.endpoint.Endpoints.register();
	}
}
