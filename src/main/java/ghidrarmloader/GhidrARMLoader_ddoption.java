package ghidrarmloader;

import ghidra.app.util.Option;
import ghidra.app.util.OptionListener;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class GhidrARMLoader_ddoption extends Option implements OptionListener, ItemListener {
	static ARMDataCollection adc = null;
	static String selectedVendor = null;
	static String selectedChip = null;
	static GhidrARMLoader_ddoption vopt = null;
	static GhidrARMLoader_ddoption copt = null;
	Integer mtype;
	Choice cb;

	protected static void setAdc(ARMDataCollection adc) {
		GhidrARMLoader_ddoption.adc = adc;
	}

	protected static void setSelectedVendor(String selectedVendor) {
		GhidrARMLoader_ddoption.selectedVendor = selectedVendor;
	}

	protected static void setVopt(GhidrARMLoader_ddoption vopt) {
		GhidrARMLoader_ddoption.vopt = vopt;
	}

	protected static void setCopt(GhidrARMLoader_ddoption copt) {
		GhidrARMLoader_ddoption.copt = copt;
	}

	protected void setMtype(Integer mtype) {
		this.mtype = mtype;
	}

	public GhidrARMLoader_ddoption(String name, Integer type, ARMDataCollection adc_p) {
		super(name, String.class);
		if (adc == null)
			adc = adc_p;
		mtype = type;

		if (type == 0)
			vopt = this;
		if (type == 1)
			copt = this;
		setOptionListener(this);
		// TODO Auto-generated constructor stub
	}

	@Override
	public java.awt.Component getCustomEditorComponent() {

		cb = new Choice();
		if (mtype == 0) {
			for (String s : adc.getVendors()) {
				cb.add(s);
			}
			selectedVendor = vopt.cb.getSelectedItem();
		}
		if (mtype == 1) {
			for (String s : adc.getChipsforVendors(vopt.cb.getSelectedItem())) {
				cb.add(s);
			}
			selectedChip = copt.cb.getSelectedItem();
		}
		cb.addItemListener(this);

		return cb;
	}

	@Override
	public GhidrARMLoader_ddoption copy() {
		GhidrARMLoader_ddoption c = new GhidrARMLoader_ddoption(getName(), mtype, adc);
		c.setCopt(copt);
		c.setVopt(vopt);
		c.setSelectedVendor(selectedVendor);

		return (c);
	}



	static boolean lockevents = false;
	@Override
	public void optionChanged(Option option) {
		// TODO Auto-generated method stub
		// System.out.println("Opt Changed");
		if (lockevents)
			return;

		if (option == vopt) {
			selectedVendor = vopt.cb.getSelectedItem();
			this.setValue(selectedVendor);
			lockevents = true;
			copt.cb.removeAll();
			for (String s : adc.getChipsforVendors(vopt.cb.getSelectedItem())) {
				copt.cb.add(s);
			}
			lockevents = false;
		}
		if (option == copt) {
			selectedChip = copt.cb.getSelectedItem();
			this.setValue(selectedChip);
		}

	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getSource() == vopt.cb)
			optionChanged(vopt);
		if (e.getSource() == copt.cb)
			optionChanged(copt);
	}

}
