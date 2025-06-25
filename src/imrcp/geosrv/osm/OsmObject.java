/*
 * Copyright 2018 Synesis-Partners.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package imrcp.geosrv.osm;

import imrcp.system.Id;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Base class that contains field common to Open Street Map Nodes and Ways. Implemented
 * as a hash map to store the key/value pairs of the nodes and ways
 * @author aaron.cherney
 */
public class OsmObject extends HashMap<String, String>
{
	/**
	 * Osm id
	 */
	public long m_lId;

	
	/**
	 * File position. This field is used when creating and parsing IMRCP binary
	 * representation of OSM files
	 */
	public int m_nFp;

	
	/**
	 * IMRCP Id
	 */
	public Id m_oId;

	
	/**
	 * Compares OsmObjects by File position
	 */
	public static Comparator<OsmObject> FPCOMP = (OsmObject o1, OsmObject o2) -> {return Integer.compare(o1.m_nFp, o2.m_nFp);};

	
	/**
	 * Compare OsmObjects by Osm Id
	 */
	public static Comparator<OsmObject> LONGID = (OsmObject o1, OsmObject o2) -> {return Long.compare(o1.m_lId, o2.m_lId);};

	
	/**
	 * Default constructor. Does nothing.
	 */
	public OsmObject()
	{
	}

	
	/**
	 * Constructs an OsmObject with the given OSM id. 
	 * @param lId Osm id of the object
	 */
	public OsmObject(long lId)
	{
		super(1);
		m_lId = lId;
	}
}
