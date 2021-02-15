# ghidrARMloader

This loads raw dumps of arm firmware in ghidra

IT WILL NOT WORK ON ITS OWN, it needs these components :

The ARM chip database available here :
https://github.com/Jegeva/idarm-resultdb-nodescription
(The db is built with https://github.com/Jegeva/idarm/)
copied (or symlinked) in the data directory

AND the sqlite3 jdbc driver available here :
https://github.com/xerial/sqlite-jdbc
installed in your build path

Once you are there, try to load any raw ARM dump.

It should detect :
- If it is a Cortex dump (by identifiying the interrupt vector table
- The base adress (for cortexes)

![autodetect](./pics/GAL_1.png?raw=true)

You can then just select the vendor and the MCU in the loading option menu

![vendors+chips](./pics/GAL_2.png?raw=true)

Once loaded :
- Every peripheral register address is named
- Every register is declared as a bitfield struct with the bits named as in the chip's datasheet.
- the IVT is named to the appropriate Standard peripheral (or number when unknown (it is the case in a lot of SVDs))
- The peripheral bits are defined as the appropriate struct

![vendors+chips](./pics/GAL_3.png?raw=true)


This supports ~650 chips from
*Toshiba
*Nordic
*Fujitsu
*NXP
*Atmel
*Holtek
*STMicro
*Freescale
*Nuvoton
*TexasInstruments
*Cypress
*Spansion
*SiliconLabs 

