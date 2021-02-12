package ghidrarmloader;

import java.sql.*;
import java.util.*;

public class ARMDataCollection {
	static Connection conn = null;
	static AbstractMap<String, AbstractMap<String, Integer>> VendorChips_hm = new HashMap<String, AbstractMap<String, Integer>>();

	static AbstractMap<Integer, ARMPeripheral> TargetChipPeripherals = new HashMap<Integer, ARMPeripheral>();
	static AbstractMap<String, ARMPeripheral> TargetChipPeripheralsByName = new HashMap<String, ARMPeripheral>();
	static AbstractMap<Integer, ARMPeripheral> TargetChipPeripheralsByDbid = new HashMap<Integer, ARMPeripheral>();
	static AbstractMap<Integer, ARMRegister> TargetChipRegistersByDbid = new HashMap<Integer, ARMRegister>();

	public ARMDataCollection() {
		/*
		 * at first we only build the vendor/ chip relationship, no need to load
		 * everything, we will only need the periph & register relation for 1 chip in
		 * the end
		 */
		if (conn == null) {
			try {
				Class.forName("org.sqlite.JDBC");
				conn = DriverManager.getConnection("jdbc:sqlite:data/db.sqlite3");
			} catch (Exception e) {
				System.err.println(e.getClass().getName() + ": " + e.getMessage());
				System.exit(0);
			}
			System.out.println("Opened database successfully");
			Statement stmt;
			PreparedStatement pstmt;
			String sql = "select vendor,chip,chips.id from vendors,chips where chips.vendorid = vendors.id;;";
			try {
				stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql);
				while (rs.next()) {
					// System.out.println(rs.getString("vendor"));
					if (VendorChips_hm.get(rs.getString("vendor")) == null)
						VendorChips_hm.put(rs.getString("vendor"), new HashMap<String, Integer>());
					VendorChips_hm.get(rs.getString("vendor")).put(rs.getString("chip"), rs.getInt("id"));

				}
				rs.close();
				stmt.close();
				sql = "select * from chips where vendorid = ?;";

			} catch (SQLException e) {
				System.out.println(e.getMessage());
			}
		}

	}

	public void loadChip(Integer chipid) {
		// chip's periphs
		String sql = "select peripherals.id as pid,registers.id as rid,peripherals.name as pname,registers.name as rname,base_address,registers.offset,registers.sz/8 as sz,initval from peripherals,chip_peripherals,peripheral_registers,registers "
				+ "where " + "chip_peripherals.cid = ? and " + "peripherals.id = chip_peripherals.pid and "
				+ "peripheral_registers.pid = chip_peripherals.pid and " + "peripheral_registers.rid = registers.id "
				+ "order by " + "peripherals.name,registers.offset;";
		// chip's core's periphs
		String sql2 = "select peripherals.name as pname,peripherals.id as pid,registers.id as rid,registers.name as rname,base_address,registers.offset,registers.sz/8 as sz,initval from "
				+ " core_chips, peripherals, core_peripherals, peripheral_registers, registers" + " where "
				+ " core_chips.chid = ? and" + " core_peripherals.cid = core_chips.coid and "
				+ " peripherals.id = core_peripherals.pid and "
				+ " peripheral_registers.pid = core_peripherals.pid and " + " peripheral_registers.rid = registers.id "
				+ " order by " + " peripherals.name,registers.offset;";
		// fields
		String sql3 = "select registers.name,rid,fid,fields.name as fname,bitw,fields.offset,access,fields.description from registers,register_fields,fields WHERE "
				+ "register_fields.fid = fields.id AND " + "register_fields.rid = registers.id and "
				+ "register_fields.rid in " + "(" + "select rid from registers,peripheral_registers where "
				+ "registers.id = peripheral_registers.rid AND " + "peripheral_registers.pid in (" + "SELECT pid FROM "
				+ "peripherals,chip_peripherals " + "WHERE " + "chip_peripherals.cid = ? and "
				+ "peripherals.id=chip_peripherals.pid " + ")) " + "order by registers.id,fields.offset;";

		PreparedStatement pstmt;
		try {
			// add chip's periphs and registers
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, chipid);
			ResultSet rs = pstmt.executeQuery();
			ARMPeripheral currp;
			while (rs.next()) {
				if (TargetChipPeripherals.get(rs.getInt("base_address")) == null) {
					currp = new ARMPeripheral(rs.getString("pname"), rs.getInt("base_address"), rs.getInt("pid"));
					TargetChipPeripherals.put(rs.getInt("base_address"), currp);
					TargetChipPeripheralsByDbid.put(rs.getInt("pid"), currp);
					TargetChipPeripheralsByName.put(rs.getString("pname"), currp);
					currp.isDevicePeripheral(true);
				} else {
					currp = TargetChipPeripherals.get(rs.getInt("base_address"));
				}
				ARMRegister r = currp.addRegister(rs.getString("rname"), rs.getInt("offset"),
						currp.getBaseAddress() + rs.getInt("offset"), rs.getInt("sz"), rs.getInt("rid"),
						rs.getInt("initval"));
				TargetChipRegistersByDbid.put(r.getDbid(), r);
			}
			rs.close();
			pstmt.close();
			// add chip's core's periphs and registers
			pstmt = conn.prepareStatement(sql2);
			pstmt.setInt(1, chipid);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				// avoid duplicate periphs if defined in the device SVD AND the core SVD, device takes precedence
				if (!TargetChipPeripheralsByName.containsKey(rs.getString("pname"))) {
					if (TargetChipPeripherals.get(rs.getInt("base_address")) == null) {

						currp = new ARMPeripheral(rs.getString("pname"), rs.getInt("base_address"), rs.getInt("pid"));
						TargetChipPeripherals.put(rs.getInt("base_address"), currp);
						currp.isDevicePeripheral(false);
					} else {
						currp = TargetChipPeripherals.get(rs.getInt("base_address"));
					}
					if (!currp.isDevicePeripheral()) {
						ARMRegister r = currp.addRegister(rs.getString("rname"), rs.getInt("offset"),
								currp.getBaseAddress() + rs.getInt("offset"), rs.getInt("sz"), rs.getInt("rid"),
								rs.getInt("initval"));
						TargetChipRegistersByDbid.put(r.getDbid(), r);
					}
				}
			}
			rs.close();
			pstmt.close();
			// add all registers' fields
			pstmt = conn.prepareStatement(sql3);
			pstmt.setInt(1, chipid);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				ARMRegister r = TargetChipRegistersByDbid.get(rs.getInt("rid"));
				if(r!=null){
				r.addField(rs.getString("fname"), rs.getInt("offset"), rs.getInt("bitw"), rs.getInt("access"),
						rs.getString("description"), rs.getInt("fid"));
				}
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public SortedSet<String> getVendors() {
		return new TreeSet<String>(VendorChips_hm.keySet());
	};

	public SortedSet<String> getChipsforVendors(String v) {
		return new TreeSet<String>(VendorChips_hm.get(v).keySet());
	};

	public Integer getChipID(String v, String c) {
		return VendorChips_hm.get(v).get(c);
	}

	public AbstractMap<Integer, ARMPeripheral> getTargetChipPeripherals() {
		return TargetChipPeripherals;
	}

	public SortedSet<Integer> getPeripheralSet() {
		return new TreeSet<Integer>(TargetChipPeripherals.keySet());
	};

}
