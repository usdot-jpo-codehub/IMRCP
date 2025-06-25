package imrcp.system;

/**
 * Runnable target interface
 *
 * @param <E> Template type. Implementations must specify a concrete type at
 * declaration time in place of type: T.
 * @author bryan.krueger
 * @version 1.0
 */
public interface IRunTarget<E>
{

	/**
	 * Run method for target object.
	 *
	 * @param e target object to run
	 */
	public void run(E e);
}
