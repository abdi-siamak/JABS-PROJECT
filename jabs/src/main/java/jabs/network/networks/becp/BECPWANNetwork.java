package jabs.network.networks.becp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import jabs.consensus.algorithm.BECP;
import jabs.consensus.config.ConsensusAlgorithmConfig;
import jabs.network.networks.Network;
import jabs.network.stats.wan.WANNetworkStats;
import jabs.network.stats.wan.SingleNodeType;
import jabs.network.node.nodes.becp.BECPNode;
import jabs.simulator.randengine.RandomnessEngine;
import jabs.simulator.Simulator;


public class BECPWANNetwork extends Network{
	//********* Network Type Setting **********
	private NETWORK_OVERLAY_TOPOLOGY network_overlay_topology = NETWORK_OVERLAY_TOPOLOGY.REGULAR_RANDOM_GRAPH;
	private int communitySize = 4;
	
	private enum NETWORK_OVERLAY_TOPOLOGY {
		REGULAR_RANDOM_GRAPH,
		REGULAR_RING_LATTICE,
		RING_OF_COMMUNITIES,
		UNDIRECTED_REGULAR_GRAPH
	}
    public BECPWANNetwork(RandomnessEngine randomnessEngine) {
        super(randomnessEngine, new WANNetworkStats(randomnessEngine));
    }

    @Override
    public void populateNetwork(Simulator simulator, ConsensusAlgorithmConfig becpConsensusConfig) {

    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig consensusAlgorithmConfig) {

    }

    @Override
    public void populateNetwork(Simulator simulator, int numNodes, ConsensusAlgorithmConfig becpConsensusConfig, int neighborCacheSize, double value_i, double weight_i, double value, double weight, double vDataAggregation, double wDataAggregation, double vDataConvergence, double vDataAgreement, double weightValue) {
    	// Step 1: Generating nodes
        this.addNode(new BECPNode(simulator, this, 0, 
                this.sampleDownloadBandwidth(SingleNodeType.WAN_NODE),
                this.sampleUploadBandwidth(SingleNodeType.WAN_NODE), value_i, weight_i, vDataAggregation, wDataAggregation, vDataConvergence, vDataAgreement, weightValue), SingleNodeType.WAN_NODE);
    	for (int i = 1; i < numNodes; i++) {
            this.addNode(createNewBECPNode(simulator, i, value, weight, vDataAggregation, wDataAggregation, vDataConvergence, vDataAgreement, weightValue), SingleNodeType.WAN_NODE);
        }
        // Step 2: Building the initial overlay network-assigning neighbors to the nodes' neighbors Local Cache and Connecting the nodes to the network.
        int degree = neighborCacheSize;
        List<BECPNode> nodes = this.getAllNodes();
        
        switch (network_overlay_topology){
		case REGULAR_RANDOM_GRAPH:
			buildRegularRandomGraph(nodes, degree); // building a k-regular graph
			break;
		case REGULAR_RING_LATTICE:
			buildRegularRINGLATTICE(nodes, degree);
			break;
		case RING_OF_COMMUNITIES:
			buildRingOfCommunities(nodes, communitySize, degree, randomnessEngine);
			break;
		case UNDIRECTED_REGULAR_GRAPH:
			buildUndirectedRegularRandomGraph(nodes, degree, randomnessEngine);
        }
    }
    
    private void buildRingOfCommunities(List<BECPNode> nodes, int communitySize, int maxNeighbors, RandomnessEngine rng) {
        int numCommunities = nodes.size() / communitySize;
        for (int i = 0; i < numCommunities; i++) {
            int start = i * communitySize;
            int end = Math.min(start + communitySize, nodes.size());
            List<BECPNode> community = nodes.subList(start, end);

            for (BECPNode node : community) {
                Set<BECPNode> neighborSet = new HashSet<>();

                // Add neighbors from the same community (excluding self)
                List<BECPNode> intra = new ArrayList<>(community);
                intra.remove(node);
                Collections.shuffle(intra, new Random(rng.nextLong()));
                int intraCount = Math.min(maxNeighbors - 1, intra.size());
                neighborSet.addAll(intra.subList(0, intraCount));

                // Add 1 neighbor from the next community in the ring
                int nextCommunityIndex = (i + 1) % numCommunities;
                int nextStart = nextCommunityIndex * communitySize;
                int nextEnd = Math.min(nextStart + communitySize, nodes.size());
                List<BECPNode> nextCommunity = nodes.subList(nextStart, nextEnd);

                BECPNode bridgeNeighbor = nextCommunity.get(rng.nextInt(nextCommunity.size()));
                neighborSet.add(bridgeNeighbor);

                // If size is still < maxNeighbors, fill from rest of the network (or same community)
                if (neighborSet.size() < maxNeighbors) {
                    List<BECPNode> fillers = new ArrayList<>(nodes);
                    fillers.remove(node);
                    fillers.removeAll(neighborSet);
                    Collections.shuffle(fillers, new Random(rng.nextLong()));
                    for (BECPNode extra : fillers) {
                        neighborSet.add(extra);
                        if (neighborSet.size() == maxNeighbors) break;
                    }
                }

                // Trim if necessary (very unlikely)
                while (neighborSet.size() > maxNeighbors) {
                    List<BECPNode> temp = new ArrayList<>(neighborSet);
                    Collections.shuffle(temp, new Random(rng.nextLong()));
                    neighborSet = new HashSet<>(temp.subList(0, maxNeighbors));
                }
                if (BECP.NCP || BECP.EMP) {
                    node.getNeighborsLocalCache().clear();
                    node.getNeighborsLocalCache().addAll(neighborSet);
                    node.getP2pConnections().connectToNetwork(this);
                }
                if (BECP.EMP_PLUS) {
                	   node.getMainCache().clear();
                	   for (BECPNode neighbor : neighborSet) {
                		    node.getMainCache().put(neighbor, 0);
                		}
                       node.getP2pConnections().connectToNetwork(this);
                }
            }
        }
    }

	private void buildRegularRINGLATTICE(List<BECPNode> nodes, int degree) {
		if (BECP.NCP||BECP.EMP) {
		    for (int i = 0; i < nodes.size(); i++) {
		        BECPNode node = nodes.get(i);

		        for (int j = 1; j <= degree; j++) {
		            int neighborIndex1 = (i + j) % nodes.size(); // Forward neighbor
		            int neighborIndex2 = (i - j + nodes.size()) % nodes.size(); // Backward neighbor

		            BECPNode neighbor1 = nodes.get(neighborIndex1);
		            BECPNode neighbor2 = nodes.get(neighborIndex2);

		            if (!node.getNeighborsLocalCache().contains(neighbor1)) {
		                node.getNeighborsLocalCache().add(neighbor1); 
		            }

		            if (!node.getNeighborsLocalCache().contains(neighbor2)) {
		                node.getNeighborsLocalCache().add(neighbor2); 
		            }
		        }

		        node.getP2pConnections().connectToNetwork(this);
		    }
		}
		if (BECP.EMP_PLUS) {
		    for (int i = 0; i < nodes.size(); i++) {
		        BECPNode node = nodes.get(i);

		        for (int j = 1; j <= degree; j++) {
		            int neighborIndex1 = (i + j) % nodes.size(); // Forward neighbor
		            int neighborIndex2 = (i - j + nodes.size()) % nodes.size(); // Backward neighbor

		            BECPNode neighbor1 = nodes.get(neighborIndex1);
		            BECPNode neighbor2 = nodes.get(neighborIndex2);

		            if (!node.getMainCache().containsKey(neighbor1)) {
		                node.getMainCache().put(neighbor1, 0); 
		            }

		            if (!node.getMainCache().containsKey(neighbor2)) {
		                node.getMainCache().put(neighbor2, 0); 
		            }
		        }

		        node.getP2pConnections().connectToNetwork(this);
		    }
		}
	}

	private void buildRegularRandomGraph(List<BECPNode> nodes, int degree) {
		if (BECP.NCP||BECP.EMP) {
			for (BECPNode node:nodes) {
                while (node.getNeighborsLocalCache().size() < degree) { 
                    BECPNode randomNeighbor = (BECPNode) getRandomNode();
                    if (!node.getNeighborsLocalCache().contains(randomNeighbor) && randomNeighbor != node) {
                        node.getNeighborsLocalCache().add(randomNeighbor);
                    }
                }
                node.getP2pConnections().connectToNetwork(this);
            }
		}
		if (BECP.EMP_PLUS) {
            for (BECPNode node:nodes) {
                while (node.getMainCache().size() < degree) {
                    BECPNode randomNeighbor = (BECPNode) getRandomNode();
                    if (!node.getMainCache().containsKey(randomNeighbor) && randomNeighbor != node) {
                        node.getMainCache().put(randomNeighbor, 0); // key: [nodeID, value: createdTime]
                    }
                }
                node.getP2pConnections().connectToNetwork(this);
            }
		}
	}
	
	private void buildUndirectedRegularRandomGraph(List<BECPNode> nodes, int degree, RandomnessEngine rng) {
	    int n = nodes.size();

	    // Degree must be even × number of nodes
	    if ((n * degree) % 2 != 0) {
	        throw new IllegalArgumentException("n * degree must be even for a regular graph");
	    }

	    // Create stubs (each node appears 'degree' times)
	    List<BECPNode> stubs = new ArrayList<>();
	    for (BECPNode node : nodes) {
	        for (int i = 0; i < degree; i++) {
	            stubs.add(node);
	        }
	    }

	    // Try to pair stubs randomly
	    int maxTries = 100;
	    boolean success = false;
	    if (BECP.NCP||BECP.EMP) {
		    for (int attempt = 0; attempt < maxTries; attempt++) {
		        // Clear all caches
		        for (BECPNode node : nodes) {
		            node.getNeighborsLocalCache().clear();
		        }

		        List<BECPNode> tempStubs = new ArrayList<>(stubs);
		        Collections.shuffle(tempStubs, new Random(rng.nextLong()));
		        boolean valid = true;

		        for (int i = 0; i < tempStubs.size(); i += 2) {
		            BECPNode a = tempStubs.get(i);
		            BECPNode b = tempStubs.get(i + 1);

		            if (a == b || a.getNeighborsLocalCache().contains(b)) {
		                valid = false;
		                break;
		            }

		            a.getNeighborsLocalCache().add(b);
		            b.getNeighborsLocalCache().add(a);
		        }

		        if (valid) {
		            success = true;
		            break;
		        }
		    }
	    }
	    
	    if (BECP.EMP_PLUS) {
		    for (int attempt = 0; attempt < maxTries; attempt++) {
		    	// Clear all caches
		        for (BECPNode node : nodes) {
		            node.getMainCache().clear();
		        }
		        List<BECPNode> tempStubs = new ArrayList<>(stubs);
		        Collections.shuffle(tempStubs, new Random(rng.nextLong()));
		        boolean valid = true;

		        for (int i = 0; i < tempStubs.size(); i += 2) {
		            BECPNode a = tempStubs.get(i);
		            BECPNode b = tempStubs.get(i + 1);

		            if (a == b || a.getMainCache().containsKey(b)) {
		                valid = false;
		                break;
		            }

		            a.getMainCache().put(b, 0);
		            b.getMainCache().put(a, 0);
		        }

		        if (valid) {
		            success = true;
		            break;
		        }
		    }
	    }

	    if (!success) {
	        throw new RuntimeException("Failed to generate a regular graph after multiple attempts");
	    }

	    for (BECPNode node : nodes) {
	        node.getP2pConnections().connectToNetwork(this);
	    }
	}

	/**
     * @param simulator
     * @param nodeID
     * @return
     */
    public BECPNode createNewBECPNode(Simulator simulator, int nodeID, double value, double weight, double vDataAggregation, double wDataAggregation, double vDataConvergence, double vDataAgreement, double weightValue) {
        return new BECPNode(simulator, this, nodeID,
                this.sampleDownloadBandwidth(SingleNodeType.WAN_NODE),
                this.sampleUploadBandwidth(SingleNodeType.WAN_NODE), value, weight, vDataAggregation, wDataAggregation, vDataConvergence, vDataAgreement, weightValue);
    }
}
