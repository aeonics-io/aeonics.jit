package aeonics.jit.policy;

import java.util.Collections;
import java.util.Set;

/**
 * The references a compiled unit makes to the outside world, as extracted from its bytecode.
 * <p>
 * Two distinct axes are reported because they answer different questions and a class name alone
 * cannot separate them. {@link #invoked()} lists the members the code actually calls or reads and is
 * the set that carries capability. {@link #interfaces()} lists the interfaces the code implements,
 * which leave no trace in {@link #invoked()} at all because interfaces have no constructor and
 * inherited members are compiled against the subclass rather than the declaring type.
 * <p>
 * Superclasses are deliberately absent: every subclass constructor chains to a superclass
 * constructor, so {@code extends Foo} always surfaces as {@code Foo.<init>} in {@link #invoked()}.
 */
public class References
{
	private final String module;
	private final Set<String> invoked;
	private final Set<String> interfaces;

	/**
	 * @param module the generated module name holding the compiled unit
	 * @param invoked the invoked members, each as {@code package.Class.member} in dot form
	 * @param interfaces the implemented interface names in dot form
	 */
	public References(String module, Set<String> invoked, Set<String> interfaces)
	{
		this.module = module;
		this.invoked = Collections.unmodifiableSet(invoked);
		this.interfaces = Collections.unmodifiableSet(interfaces);
	}

	/**
	 * Returns the generated module name holding the compiled unit. Every class the unit declares
	 * lives in this module, which lets a policy tell the code's own members from foreign ones.
	 * @return the generated module name
	 */
	public String module() { return module; }

	/**
	 * Returns every member the compiled unit calls or reads, as {@code package.Class.member} in dot
	 * form. Constructors appear as {@code <init>}. This covers explicit calls, field access, object
	 * creation, method references and the bootstrap methods behind lambdas and string concatenation.
	 * <p>
	 * The owner is the type the call was compiled against, not the type declaring the member. An
	 * inherited member invoked from a subclass is reported against the subclass.
	 * @return the invoked members
	 */
	public Set<String> invoked() { return invoked; }

	/**
	 * Returns the interfaces implemented by the classes of the compiled unit, in dot form.
	 * @return the implemented interfaces
	 */
	public Set<String> interfaces() { return interfaces; }
}
