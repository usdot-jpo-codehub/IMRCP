package imrcp.web;

/**
 * ObsRequest implementation used for chart requests from the IMRCP Map UI
 * @author aaron.cherney
 */
public class ObsChartRequest extends ObsRequest
{
	/**
	 * IMRCP observation type id of the request
	 */
	private int m_nObstypeId;

	/**
	 * Gets the IMRCP observation type id of the request
	 * @return The IMRCP observation type id of the request
	 */
	public int getObstypeId()
  {
    return m_nObstypeId;
  }

	/**
	 * Sets the IMRCP observation type id of the request
	 * @param m_nObstypeId The desired IMRCP observation type id of the request
	 */
	public void setObstypeId(int m_nObstypeId)
  {
    this.m_nObstypeId = m_nObstypeId;
  }

}
