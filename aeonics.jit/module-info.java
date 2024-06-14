module aeonics.jit
{
	requires aeonics.boot;
	requires aeonics.core;
	requires aeonics.http;
	requires java.compiler;
	
	provides aeonics.Plugin with local.Main;
}
