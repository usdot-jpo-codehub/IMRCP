/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.system;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author aaron.cherney
 */
public abstract class JSONUtil
{
	public static JSONArray optJSONArray(JSONObject oObj, String sKey)
	{
		JSONArray oRet = oObj.optJSONArray(sKey);
		if (oRet == null)
		{
			oRet = new JSONArray();
		}
		return oRet;
	}
	
	
	public static String[] getStringArray(JSONObject oObj, String sKey)
	{
		JSONArray oArr = optJSONArray(oObj, sKey);
		String[] sRet = new String[oArr.length()];
		for (int nIndex = 0; nIndex < sRet.length; nIndex++)
			sRet[nIndex] = oArr.getString(nIndex);
		
		return sRet;
	}
	
	
	public static String[] optStringArray(JSONObject oObj, String sKey, String... sDefault)
	{
		String[] sRet = getStringArray(oObj, sKey);
		if (sRet.length == 0 && !oObj.has(sKey))
			sRet = sDefault;
		
		return sRet;
	}
	
	
	public static int[] getIntArray(JSONObject oObj, String sKey)
	{
		JSONArray oArr = optJSONArray(oObj, sKey);
		int[] nRet = new int[oArr.length()];
		for (int nIndex = 0; nIndex < nRet.length; nIndex++)
			nRet[nIndex] = oArr.getInt(nIndex);
		
		return nRet;
	}
	
	
	public static int[] optIntArray(JSONObject oObj, String sKey, int... nDefault)
	{
		int[] nRet = getIntArray(oObj, sKey);
		if (nRet.length == 0 && !oObj.has(sKey))
			nRet = nDefault;
		
		return nRet;
	}
	
	
	public static double[] getDoubleArray(JSONObject oObj, String sKey)
	{
		JSONArray oArr = optJSONArray(oObj, sKey);
		double[] nRet = new double[oArr.length()];
		for (int nIndex = 0; nIndex < nRet.length; nIndex++)
			nRet[nIndex] = oArr.getDouble(nIndex);
		
		return nRet;
	}
	
	
	public static boolean[] getBooleanArray(JSONObject oObj, String sKey)
	{
		JSONArray oArr = optJSONArray(oObj, sKey);
		boolean[] nRet = new boolean[oArr.length()];
		for (int nIndex = 0; nIndex < nRet.length; nIndex++)
			nRet[nIndex] = oArr.getBoolean(nIndex);
		
		return nRet;
	}
}
