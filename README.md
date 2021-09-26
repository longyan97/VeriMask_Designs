
# VeriMask_Designs
 
*VeriMask* is a wireless sensor platform for automatic verification of N95 masks' moist-heat decontamination. It comes with the ACM IMWUT 2021 publication ["VeriMask: Facilitating Decontamination of N95 Masks in the COVID-19 Pandemic: Challenges, Lessons Learned, and Safeguarding the Future"](https://dl.acm.org/doi/abs/10.1145/3478105?af=R)

The design contains three parts: VeriMask hardware sensor nodes, nodes firmware, and Android application. Please find the detailed descroption of the design in the IMWUT paepr. 

The open-source design aims to inform the design space of mask decontamination verification systems and provide a prototype for future emergency-use systems. Please feel free to contact the authors if you have questions regarding the usage. 

Some details: 
1) The sensor nodes use the Laird653 wireless module with Nordic nRF52833 SoC (PCA10100)
2) Two temperature and relative humidity sensors are supported: Si7021 and SHT35
3) The sensor nodes and App communicate via BLE passive advertisement 
