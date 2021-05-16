/**
 * 
 */
package nz.cri.gns.NZSHM22.opensha.ruptures;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

/**
 * @author chrisbc
 *
 */
public class DownDipFaultSection extends FaultSectionPrefData {

	/**
	 * Represents a downdip fault section, that is used in the 2-axes section
	 * structures used by subduction zones (i.e. the Hikurangi.)
	 */
	private static final long serialVersionUID = -4004985886997575136L;
	private int rowIndex = Integer.MAX_VALUE;
	private int colIndex = Integer.MAX_VALUE;
//	/**
//	 * 
//	 */
//	public DownDipFaultSection() {
//		// TODO Auto-generated constructor stub
//	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

	/**
	 * @param rowIndex
	 * @return
	 */
	public DownDipFaultSection setRowIndex(int rowIndex) {
		this.rowIndex = rowIndex;
		return this;
	}

	/**
	 * @param
	 * @return rowIndex
	 */
	public int getRowIndex() {
		return this.rowIndex;
	}

	/**
	 * @param colIndex
	 * @return
	 */
	public DownDipFaultSection setColIndex(int colIndex) {
		this.colIndex = colIndex;
		return this;
	}

	/**
	 * @param
	 * @return colIndex
	 */
	public int getColIndex() {
		return this.colIndex;
	}

	public String toString() {
		String str = "type = DownDipFaultSection\n";
		str += "rowIndex: " + Integer.toString(this.rowIndex) + "\n";
		str += "colIndex: " + Integer.toString(this.colIndex) + "\n";
		str += super.toString();
		return str;
	}

	@Override
	public DownDipFaultSection clone() {
		DownDipFaultSection section = new DownDipFaultSection();
		section.setFaultSectionPrefData(this);
		return section;
	}
}
