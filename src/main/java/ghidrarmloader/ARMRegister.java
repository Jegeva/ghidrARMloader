package ghidrarmloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class ARMRegister {
	String Name;
	String PeriphName;
	Integer Offset;
	Integer Address;
	Integer Size;
	Integer Dbid;
	Integer InitVal;
	Boolean HaveFlags = false;
	ArrayList<ARMField> Fields;
	HashMap<Integer, ARMField> FieldsByDbid = new HashMap<Integer, ARMField>();

	protected Boolean haveFlags() {
		return HaveFlags;
	}

	public ARMRegister(String periphname, String name, Integer offset, Integer address, Integer size, Integer dbid,Integer initval) {
		super();
		Name = name;
		Offset = offset;
		Address = address;
		Dbid = dbid;
		Size = size;
		PeriphName = periphname;
		InitVal = initval;
		Fields = new ArrayList<ARMField>();
	}

	protected Integer getInitVal() {
		return InitVal;
	}

	public String getCStructName() {
		if (Name.length() < PeriphName.length())
			return PeriphName + "_" + Name;
		if (Name.substring(0, PeriphName.length()) != PeriphName) 
			return PeriphName + "_" + Name;
		return Name;

	}

	public String getCStruct() {
		// Apparently Ghidra doesn't decompile bitfields access so well.. yet...
		// Still this is not a reason to be ready for the moment it does...
		String ret = "struct {\n";
		Integer curroff = 0;
		Integer reservedcnt = 0;
		// by construction this is "order by offset"
		if (!HaveFlags)
			return "";

		for (ARMField f : Fields) {
			if (f.getOffset() > curroff) {
				ret += "unsigned int RESERVED_" + reservedcnt.toString() 
						+ " :"
						+ Integer.toString(f.getOffset() - curroff) + ";\n";
				curroff = f.getOffset() ;
				reservedcnt++;
				
			}
			ret += "unsigned int " + f.getName() + " :" + f.getBitWidth() + ";\n";
			curroff += f.getBitWidth();
		}

		ret += "} " + getCStructName() + " ;";
		///System.out.println(ret);
		return ret;
	}

	public void addField(String fname, Integer foffset, Integer fbitwidth, Integer faccess, String fdescr,
			Integer dbid) {
		ARMField f = new ARMField(dbid, fbitwidth, foffset, faccess, fname, fdescr);
		Fields.add(f);
		FieldsByDbid.put(dbid, f);
		HaveFlags = true;
	}

	public void addField(String fname, Integer foffset, Integer fbitwidth, Integer faccess, Integer dbid) {
		addField(fname, foffset, fbitwidth, faccess, "", dbid);
	}

	protected String getName() {
		return Name;
	}

	protected Integer getOffset() {
		return Offset;
	}

	protected Integer getAddress() {
		return Address;
	}

	protected Integer getSize() {
		return Size;
	}

	protected Integer getDbid() {
		return Dbid;
	}

	Set<Integer> getFieldsDbids() {
		return FieldsByDbid.keySet();
	}
}
