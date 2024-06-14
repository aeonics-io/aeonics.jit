package aeonics.jit;

import javax.tools.StandardLocation;
import javax.tools.JavaFileManager.Location;

class DynamicLocation 
{
	private DynamicLocation() {}
	
	static class Plugin implements Location
	{
		private String module;
		public Plugin(String module) { this.module = module; }
		public boolean isOutputLocation() { return false; }
		public String getName() { return module; }
		public String toString() { return StandardLocation.MODULE_PATH + "::" + getName(); }
	}
	
	static class Source implements Location
	{
		private DynamicFileObject.Source source;
		public Source(DynamicFileObject.Source source) { this.source = source; }
		public boolean isOutputLocation() { return false; }
		public String getName() { return source.getName(); }
		public String toString() { return getName(); }
		public DynamicFileObject.Source source() { return source; }
	}
	
	static class Output implements Location
	{
		public Output() { }
		public String getName() { return null; }
		public boolean isOutputLocation() { return false; }
	}
}
