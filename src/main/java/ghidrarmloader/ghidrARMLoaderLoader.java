/* ###
 * IP: GHIDRA
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
package ghidrarmloader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import ghidra.app.util.Option;
import ghidra.app.util.bin.ByteProvider;
import ghidra.app.util.cparser.C.CParser;
import ghidra.app.util.cparser.C.ParseException;
import ghidra.app.util.importer.MessageLog;
import ghidra.app.util.opinion.AbstractLibrarySupportLoader;
import ghidra.app.util.opinion.LoadSpec;
import ghidra.framework.model.DomainObject;
import ghidra.program.model.listing.Program;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;
import org.apache.commons.lang3.ArrayUtils;

import ghidra.app.util.Option;
import ghidra.app.util.bin.ByteProvider;
import ghidra.app.util.importer.MessageLog;
import ghidra.app.util.opinion.AbstractLibrarySupportLoader;
import ghidra.app.util.opinion.LoadSpec;
import ghidra.framework.model.DomainObject;
import ghidra.framework.store.LockException;
import ghidra.program.flatapi.FlatProgramAPI;
import ghidra.program.model.address.AddressOverflowException;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeConflictHandler;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.lang.LanguageCompilerSpecPair;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryAccessException;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.mem.MemoryConflictException;
import ghidra.util.exception.CancelledException;
import ghidra.util.exception.DuplicateNameException;
import ghidra.util.task.TaskMonitor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ghidrARMLoaderLoader extends AbstractLibrarySupportLoader {

	private static class CortexInterruptVector {
		String name;
		int addr;

		private CortexInterruptVector(String name, int addr) {
			this.name = name;
			this.addr = addr;
		}
	}

	private static final CortexInterruptVector[] CortexIVT = { new CortexInterruptVector("RESET", 0x04),
			new CortexInterruptVector("NMI", 0x08), new CortexInterruptVector("HardFault", 0x0C),
			new CortexInterruptVector("MemManage", 0x10), new CortexInterruptVector("BusFault", 0x14),
			new CortexInterruptVector("UsageFault", 0x18), new CortexInterruptVector("RESERVED", 0x1C),
			new CortexInterruptVector("RESERVED", 0x20), new CortexInterruptVector("RESERVED", 0x24),
			new CortexInterruptVector("RESERVED", 0x28), new CortexInterruptVector("SVCall", 0x2C),
			new CortexInterruptVector("DebugMonitor", 0x30), new CortexInterruptVector("RESERVED", 0x34),
			new CortexInterruptVector("PendSV", 0x38), new CortexInterruptVector("SysTick", 0x3C) };

	ARMDataCollection adc = new ARMDataCollection();

	String Vendor;
	String Chip;
	Integer ChipId;
	Integer BaseAddress = 0;

	Boolean isCortex = false;
	Boolean knowEndian = false;

	ByteBuffer bb;

	@Override
	public String getName() {

		// TODO: Name the loader. This name must match the name of the loader in the
		// .opinion
		// files.

		return "ghidrARMLoader";
	}

	@Override
	public Collection<LoadSpec> findSupportedLoadSpecs(ByteProvider provider) throws IOException {
		List<LoadSpec> loadSpecs = new ArrayList<>();

		// if it is a cortex read what should be the standard IVT
		ByteBuffer bb = ByteBuffer.wrap(provider.readBytes(0, 0x40));
		Integer firsti = bb.getInt();

		if ((firsti & 0xff) == 0 || (firsti & 0xff000000) == 0) {
			// it's a cortex, top of SP
			isCortex = true;

			Integer arrsz = bb.remaining();
			int[] countarr = new int[256];
			byte[] arr = new byte[arrsz];
			bb.get(arr);
			Integer i;
			for (i = 0; i < arrsz / 4; i++) {
				// System.out.println(Byte.toUnsignedInt(arr[i*4+3]));
				countarr[Byte.toUnsignedInt(arr[i * 4 + 3])]++;
			}
			if (countarr[0] == (arrsz / 4)) {
				loadSpecs.add(new LoadSpec(this, 0, new LanguageCompilerSpecPair("ARM:LE:32:Cortex", "default"), true));
				knowEndian = true;
			} else {
				for (i = 1; i < 256; i++) {
					if (countarr[i] > 0 && (countarr[i] + countarr[0]) == (arrsz / 4)) {
						BaseAddress = i * 0x01000000;
						// dataload is LE or every IRQH resets...
						loadSpecs.add(new LoadSpec(this, 0, new LanguageCompilerSpecPair("ARM:LE:32:Cortex", "default"),
								true));

						knowEndian = true;
					}
				}
			}
			if (!knowEndian) {
				loadSpecs.add(new LoadSpec(this, 0, new LanguageCompilerSpecPair("ARM:BE:32:Cortex", "default"), true));
				for (i = 0; i < arrsz / 4; i++) {
					// System.out.println(Byte.toUnsignedInt(arr[i*4+3]));
					countarr[Byte.toUnsignedInt(arr[i * 4])]++;
				}
				for (i = 1; i < 256; i++) {
					if (countarr[i] > 0 && (countarr[i] + countarr[0]) == (arrsz / 4)) {
						BaseAddress = i * 0x01000000;
					}
				}
			}

		} else {

			// not a cortex so instruction LE but the data fetch endianness we can't know
			// no clue, do it manually...
			loadSpecs.add(new LoadSpec(this, 0, new LanguageCompilerSpecPair("ARM:BE:32:v8", "default"), true));
			loadSpecs.add(new LoadSpec(this, 0, new LanguageCompilerSpecPair("ARM:BE:32:v8T", "default"), true));
			loadSpecs.add(new LoadSpec(this, 0, new LanguageCompilerSpecPair("ARM:LE:32:v8", "default"), true));
			loadSpecs.add(new LoadSpec(this, 0, new LanguageCompilerSpecPair("ARM:LE:32:v8T", "default"), true));

		}
		return loadSpecs;

	}

	@Override
	protected void load(ByteProvider provider, LoadSpec loadSpec, List<Option> options, Program program,
			TaskMonitor monitor, MessageLog log) throws CancelledException, IOException {

		// TODO: Load the bytes from 'provider' into the 'program'.

		adc.loadChip(ChipId);

		FlatProgramAPI api = new FlatProgramAPI(program, monitor);
		InputStream inStream = provider.getInputStream(0);
		Memory mem = program.getMemory();
		DataTypeManager DTM = program.getDataTypeManager();
		CParser cp = new CParser(DTM);

		for (Integer pba : adc.getPeripheralSet()) {
			ARMPeripheral p = adc.getTargetChipPeripherals().get(pba);
			for (ARMRegister r : p.getRegisters()) {
				try {
				//	mem.createUninitializedBlock(p.getName() + "_" + r.getName(), api.toAddr(r.getAddress()),r.getSize(), false);
					mem.createInitializedBlock(p.getName() + "_" + r.getName(), api.toAddr(r.getAddress()),r.getSize(),(byte)0,monitor,false);
					api.createLabel(api.toAddr(r.getAddress()), p.getName() + "_" + r.getName(), false);
					if (r.haveFlags()) {
						DataType regDataType = cp.parse(r.getCStruct());
						regDataType.setName(r.getCStructName());
						DTM.addDataType(regDataType, DataTypeConflictHandler.DEFAULT_HANDLER);
						program.getListing().createData(api.toAddr(r.getAddress()), regDataType);
					} else {
						Data regData = api.createDWord(api.toAddr(r.getAddress()));
					}
					mem.setInt(api.toAddr(r.getAddress()), r.getInitVal());

				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();

				} catch (LockException | DuplicateNameException | AddressOverflowException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MemoryConflictException e) {
					// TODO Auto-generated catch block
					System.err.println(r.getName() + " " + e.getMessage());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}
		try {
			mem.createInitializedBlock("Main Memory", api.toAddr(BaseAddress), inStream, inStream.available(), monitor,
					false);
		} catch (LockException | MemoryConflictException | AddressOverflowException | CancelledException
				| DuplicateNameException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (isCortex) {
			try {
				// Top of stack is first value in memory, see page 59 of datasheet
				// Make pointer, label it as stack start
				int stackAddr = mem.getInt(api.toAddr(BaseAddress));
				Data stackAddrData = api.createDWord(api.toAddr(BaseAddress));
				api.createLabel(api.toAddr(stackAddr), "_STACK", true);
				api.createMemoryReference(stackAddrData, api.toAddr(stackAddr),
						ghidra.program.model.symbol.RefType.DATA);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			for (CortexInterruptVector vector : CortexIVT) {
				int ptrVal = 0;
				try {
					ptrVal = mem.getInt(api.toAddr(BaseAddress + vector.addr));
					Data ptrData = api.createDWord(api.toAddr(BaseAddress + vector.addr));
					api.createDWord(api.toAddr(BaseAddress + vector.addr));
					api.createLabel(api.toAddr(BaseAddress + vector.addr), vector.name, true);
					api.createMemoryReference(ptrData, api.toAddr(ptrVal), ghidra.program.model.symbol.RefType.DATA);
				} catch (Exception e) {
					// This is ugly, need to fix
					System.err.println(vector.name + "@" + String.format("0x%08X", (BaseAddress + vector.addr)) + " : "
							+ String.format("0x%08X", ptrVal) + " " + e.getMessage());
					continue;
				}

			}
			try {
				ArrayList<Long> SuspectsIVT = new ArrayList<Long>();
				Long SuspectValue = Integer.toUnsignedLong(mem.getInt(api.toAddr(BaseAddress + 0x40)));

				int i = 0;
				int j = 0;
				while (((SuspectValue & BaseAddress) == BaseAddress) // if not a 0x0 base address have a bit in common
																		// at least
						&& ((SuspectValue & (~BaseAddress)) < 0xffffff) // when he hit instructions the chances are in
																		// thumb..

				) {
					SuspectsIVT.add(SuspectValue);
					Data ptrData = api.createDWord(api.toAddr(BaseAddress + 0x40 + i * 4));
					if (SuspectValue > 0) {

						api.createLabel(api.toAddr(BaseAddress + 0x40 + i * 4), "IVT_IRQ_" + j, true);
						api.createMemoryReference(ptrData, api.toAddr(SuspectValue),
								ghidra.program.model.symbol.RefType.DATA);
						j++;
					}
					i++;
					SuspectValue = Integer.toUnsignedLong(mem.getInt(api.toAddr(BaseAddress + 0x40 + i * 4)));

				}
				i = 0;
				HashMap<Long, Integer> CntIRQ = new HashMap<Long, Integer>();
				for (Long Val : SuspectsIVT) {
					// System.out.println(String.format("0x%08X",BaseAddress +
					// 0x40+i*4)+":"+String.format("0x%08X",Val));
					if (Val > 0) {
						if (CntIRQ.containsKey(Val)) {
							CntIRQ.put(Val, CntIRQ.get(Val) + 1);
						} else {
							CntIRQ.put(Val, 1);
						}
					}
				}
				// most popular IRQ handler while be while(1)
				i = 0;
				for (Long k : CntIRQ.keySet()) {
					if (CntIRQ.get(k) > i) {
						i = CntIRQ.get(k);
						SuspectValue = k;
					}
				}
				// thumb = -1
				api.createLabel(api.toAddr(SuspectValue - 1), "IRQ_Default_Handler", true);

			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	@Override
	public List<Option> getDefaultOptions(ByteProvider provider, LoadSpec loadSpec, DomainObject domainObject,
			boolean isLoadIntoProgram) {
		List<Option> list = super.getDefaultOptions(provider, loadSpec, domainObject, isLoadIntoProgram);

		// TODO: If this loader has custom options, add them to 'list'

		GhidrARMLoader_ddoption ddoption_vendor = new GhidrARMLoader_ddoption("Vendor", 0, adc);
		GhidrARMLoader_ddoption ddoption_chip = new GhidrARMLoader_ddoption("Chip", 1, adc);

		list.add(new Option("Base Address", String.format("0x%08X", BaseAddress)));
		list.add(ddoption_vendor);
		list.add(ddoption_chip);
		return list;
	}

	@Override
	public String validateOptions(ByteProvider provider, LoadSpec loadSpec, List<Option> options, Program program) {

		// TODO: If this loader has custom options, validate them here. Not all options
		// require
		// validation.

		for (Option o : options) {
			System.out.println(o.getName() + " " + o.getValue() + " ");
			if (o.getName().equals("Base Address")) {
				if (o.getValue().toString().length() > 0)
					try {
						BaseAddress = Integer.decode(o.getValue().toString());
					} catch (Exception e) {
						// TODO Auto-generated catch block
						return ("wrong format base address");
					}

			}
			if (o.getName().equals("Vendor")) {
				if (o.getValue() != null)
					Vendor = o.getValue().toString();
			}
			if (o.getName().equals("Chip")) {
				if (o.getValue() != null) {
					Chip = o.getValue().toString();
					ChipId = adc.getChipID(Vendor, Chip);
					if (ChipId != null)
						System.out.println(ChipId + " " + ChipId.toString());
				}
			}
		}

		return super.validateOptions(provider, loadSpec, options, program);
	}
}
