package ghidrarmloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class ARMPeripheral {
	String Name;
	Integer BaseAddress;
	Integer Dbid;
	Boolean IsDevicePeripheral;
	ArrayList<ARMRegister> Registers; 
	HashMap<Integer,ARMRegister> RegistersByDbid = new HashMap<Integer,ARMRegister>();
	HashMap<Integer,ARMRegister> RegistersByAddress = new HashMap<Integer,ARMRegister>();
	
	public void isDevicePeripheral(Boolean b) {
		IsDevicePeripheral=b;
	}
	public Boolean isDevicePeripheral() {
		return IsDevicePeripheral;
	}
	
	public ARMPeripheral(String name, Integer baseAddress,	Integer dbid) {
		super();
		Name = name;
		BaseAddress = baseAddress;
		Dbid=dbid;
		Registers = new 	ArrayList<ARMRegister>(); 
	}
	
	public ARMRegister addRegister(ARMRegister r) {
		Registers.add(r);
		RegistersByDbid.put(r.getDbid(), r);
		RegistersByAddress.put(r.getAddress(), r);
		return r;
	}
	
	public ARMRegister addRegister(String rname, Integer roffset, Integer raddress, Integer rsize,Integer dbid, Integer initval) {
		ARMRegister r =new ARMRegister(Name,rname, roffset, raddress, rsize,dbid,initval); 
		Registers.add(r );
		RegistersByDbid.put(dbid, r);
		RegistersByAddress.put(raddress, r);
		return r;
	}
	
	public ArrayList<ARMRegister> getRegisters(){
		return Registers;		
	}

	protected String getName() {
		return Name;
	}

	protected Integer getBaseAddress() {
		return BaseAddress;
	}
	
	Set<Integer> getRegistersDbids(){			
		return RegistersByDbid.keySet();
	}
	
	

}
