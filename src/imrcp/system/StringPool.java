package imrcp.system;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Object used to store references of String for commonly used String to lower
 * the amount of memory used.
 * @author aaron.cherney
 */
public class StringPool extends ArrayList<StringPool.Group>
{
	/**
	 * Convenience search object
	 */
	protected char[] m_oSearch = new char[2];

	
	/**
	 * Default constructor. Does nothing.
	 */
	public StringPool()
	{
	}

	
	/**
	 * If the given String is not in the String pool, it is added. The reference
	 * to the String from the String pool is then returned
	 * @param sVal the String to get the reference of
	 * @return The reference of the given String from the String pool
	 */
	public String intern(String sVal)
	{
		int nIndex = m_oSearch.length;
		while (nIndex-- > 0) // create search key
		{
			if (nIndex < sVal.length())
				m_oSearch[nIndex] = Character.toUpperCase(sVal.charAt(nIndex));
			else
				m_oSearch[nIndex] = 0;
		}

		nIndex = Collections.binarySearch(this, m_oSearch); // first find the group it belongs to
		if (nIndex < 0) // completely new String group array
		{
			nIndex = ~nIndex;
			Group oGroup = new Group(m_oSearch);
			add(nIndex, oGroup);
		}

		Group oGroup = get(nIndex);
		nIndex = Collections.binarySearch(oGroup, sVal); // then find its reference
		if (nIndex < 0)
		{
			oGroup.add(~nIndex, sVal); // add to the String pool if the reference doesn't exist
			return sVal;
		}
		return oGroup.get(nIndex);
	}

	
	/**
	 * Converts all of the String groups into a single list
	 * @return a list containing all of the Strings in the String pool
	 */
	public ArrayList<String> toList()
	{
		int nSize = length(); // determine space requirements

		ArrayList<String> oList = new ArrayList(nSize);
		for (Group oGroup : this)
			oList.addAll(oGroup);

		Collections.sort(oList); // correct pool grouping order
		return oList;
	}
	
	
	/**
	 * Gets the number of total Strings in the String pool by adding the size of
	 * each group
	 * @return The number of Strings in the String pool
	 */
	public int length()
	{
		int nSize = 0; // determine space requirements
		for (Group oGroup : this)
			nSize += oGroup.size();
		
		return nSize;
	}


	/**
	 * Calls {@link ArrayList#clear()} for each group and then {@link ArrayList#clear()}
	 * on itself
	 */
	@Override
	public void clear()
	{
		for (Group oGroup : this)
			oGroup.clear();

		super.clear();
	}

	
	/**
	 * A list of Strings that all start with the same characters
	 */
	class Group extends ArrayList<String> implements Comparable<char[]>
	{
		/**
		 * The characters all the Strings in the this group start with
		 */
		char[] m_oKey;

		
		/**
		 * Default constructor. Does nothing
		 */
		private Group()
		{
		}

		
		/**
		 * Constructs a Group with the given character key
		 * @param oKey
		 */
		Group(char[] oKey)
		{
			m_oKey = new char[]{oKey[0], oKey[1]};
		}


		/**
		 * Compares character arrays by the first character, then the second 
		 */
		@Override
		public int compareTo(char[] oRhs)
		{
			int nComp = m_oKey[0] - oRhs[0];
			if (nComp == 0)
				nComp = m_oKey[1] - oRhs[1];
				
			return nComp;
		}
	}
}
