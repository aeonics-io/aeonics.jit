package aeonics.jit.policy;

import java.util.Set;
import java.util.function.Supplier;

import aeonics.entity.Entity;
import aeonics.jit.Dynamic;
import aeonics.template.Item;
import aeonics.template.Template;
import aeonics.util.Functions.Consumer;
import aeonics.util.Snapshotable.SnapshotMode;
import aeonics.util.StringUtils;

/**
 * This item carries a compilation policy applied by {@link Dynamic} when it compiles code.
 * <p>
 * The policy holds an inspector that receives the set of class names, in dot form, referenced by the
 * compiled bytecode and throws to reject the compilation. A policy is created in code, given an
 * inspector, and linked to a {@link Dynamic} entity through its {@code policy} relationship.
 */
public class Policy extends Item<Policy.Type>
{
	/**
	 * Superclass for all policy entities.
	 */
	public static class Type extends Entity
	{
		private Consumer<Set<String>> inspector;

		/**
		 * Sets the inspector applied during compilation.
		 * @param value receives the referenced class names and throws to reject the compilation
		 * @return this
		 */
		public Policy.Type inspector(Consumer<Set<String>> value) { this.inspector = value; return this; }

		/**
		 * Returns the inspector applied during compilation.
		 * @return the inspector, or null if none is set
		 */
		public Consumer<Set<String>> inspector() { return inspector; }

		/**
		 * Hardcoded category to the {@link Policy} class
		 */
		@Override
		public final String category() { return StringUtils.toLowerCase(Policy.class); }

		@Override
		public final SnapshotMode snapshotMode() { return SnapshotMode.NONE; }
	}

	protected Class<? extends Policy.Type> defaultTarget() { return Policy.Type.class; }
	protected Supplier<? extends Policy.Type> defaultCreator() { return Policy.Type::new; }
	protected Class<? extends Policy> category() { return Policy.class; }

	@Override
	public Template<? extends Policy.Type> template()
	{
		return super.template()
			.summary("Compilation Policy")
			.description("Inspects the classes referenced by compiled code and may reject the compilation.");
	}
}
