# JABS - Modified Version

This repository contains a modified version of the [JABS simulator](https://github.com/hyajam/jabs), a Java-based blockchain simulator used for simulating decentralised systems. In this version, I have implemented several changes and enhancements to the original code to better suit my PhD project on decentralised consensus for extreme-scale blockchain systems.

## Modifications

- **Added an implementation of PAXOS**: Implemented the classic Paxos consensus algorithm for fault-tolerant distributed systems.
- **Added an implementation of RAFT**: Implemented the Raft consensus algorithm, designed for easier understandability and practical implementation.
- **Added an implementation of PBFT**: Implemented Practical Byzantine Fault Tolerance (PBFT) for Byzantine fault tolerance in distributed systems.
- **Added an implementation of Snow families**: Implemented the Snow family protocols including Slush, Snowflake, Snowball, Avalanche, Snowflake+, and Snowman for scalable and secure consensus.
- **Added an implementation of DESC (Decentralised Extra-scale Consensus)**: Introduced DESC, also referred to as BECP (Blockchain Epidemic Consensus Protocol) in the code. It includes:
  - SSEP (System Size Estimation Protocol)
  - NCP (Node Cache Protocol)
  - REAP and REAP+ (Robust Epidemic Aggregation Protocols)
  - PTP (Phase Transition Protocol)
- **Added ARP, an adaptive restart protocol**: Introduced ARP to enable adaptive restart of consensus protocols in dynamic environments.
- **Added membership protocols EMP and EMP+**: Implemented EMP (Expander Membership Protocol) and its enhanced version EMP+ to handle dynamic membership in decentralised networks.
- **Added consensus protocol ECP**: Introduced the ECP (Epidemic Consensus Protocol), which is based on the Data Aggregation consensus protocol, for decentralised systems.
- **Added new network overlay topologies**: Added support for new network topologies, including Regular Random Graph, Regular Ring Lattice, Ring of Communities, and Undirected Regular Graph.
- **Other changes to fit with new protocols**: Several internal changes were made to accommodate the new protocols and ensure compatibility within the simulator.

## Installation

To install and use this simulator, follow the instructions below:

1. Clone this repository:
   ```bash
   git clone https://github.com/abdi-siamak/JABS-PROJECT

2. Install Java and Maven if you haven't already:
   
   [Download and install Java (version 8 or higher)](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html).
   
   [Install Maven](https://maven.apache.org/install.html).

4. Navigate to the repository directory and build the project with Maven:
   ```bash
   cd jabs-modified
   mvn clean install

## Usage

To run the simulator, execute the following command:
   
   mvn exec:java -Dexec.mainClass="Main"

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

Original JABS simulator by [hyajam](https://github.com/hyajam/jabs).
The simulator was a valuable tool for my research on decentralized consensus for blockchain systems.

