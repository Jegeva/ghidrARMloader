package ghidrarmloader;

public class ARMField {
	Integer Dbid;
	Integer BitWidth;
	Integer Offset;
	Integer Access;
	String Name;
	String Description;
	
	public ARMField(Integer dbid, Integer bitWidth, Integer offset, Integer access, String name, String description) {
		super();
		Dbid = dbid;
		BitWidth = bitWidth;
		Offset = offset;
		Access = access;
		Name = name;
		Description = description;
	}

	protected Integer getDbid() {
		return Dbid;
	}

	protected Integer getBitWidth() {
		return BitWidth;
	}

	protected Integer getOffset() {
		return Offset;
	}

	protected Integer getAccess() {
		return Access;
	}

	protected String getName() {
		return Name;
	}

	protected String getDescription() {
		return Description;
	}
	
	

}
