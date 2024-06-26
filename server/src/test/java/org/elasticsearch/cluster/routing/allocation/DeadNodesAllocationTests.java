/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cluster.routing.allocation;

import static org.elasticsearch.cluster.routing.ShardRoutingState.INITIALIZING;
import static org.elasticsearch.cluster.routing.ShardRoutingState.RELOCATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.cluster.routing.ShardRoutingState.STARTED;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ESAllocationTestCase;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.elasticsearch.cluster.routing.allocation.command.AllocationCommands;
import org.elasticsearch.cluster.routing.allocation.command.MoveAllocationCommand;
import org.elasticsearch.cluster.routing.allocation.decider.ClusterRebalanceAllocationDecider;
import org.elasticsearch.common.settings.Settings;

public class DeadNodesAllocationTests extends ESAllocationTestCase {
    private final Logger logger = LogManager.getLogger(DeadNodesAllocationTests.class);

    public void testSimpleDeadNodeOnStartedPrimaryShard() {
        AllocationService allocation = createAllocationService(Settings.builder()
                                                                   .put("cluster.routing.allocation.node_concurrent_recoveries", 10)
                                                                   .put(ClusterRebalanceAllocationDecider.CLUSTER_ROUTING_ALLOCATION_ALLOW_REBALANCE_SETTING.getKey(), "always")
                                                                   .build());

        logger.info("--> building initial routing table");
        Metadata metadata = Metadata.builder()
            .put(IndexMetadata.builder("test").settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(1))
            .build();
        RoutingTable routingTable = RoutingTable.builder()
            .addAsNew(metadata.index("test"))
            .build();
        ClusterState clusterState = ClusterState.builder(org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING
                                                             .getDefault(Settings.EMPTY)).metadata(metadata).routingTable(routingTable).build();

        logger.info("--> adding 2 nodes on same rack and do rerouting");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder()
                                                                    .add(newNode("node1"))
                                                                    .add(newNode("node2"))
        ).build();

        clusterState = allocation.reroute(clusterState, "reroute");

        // starting primaries
        clusterState = startInitializingShardsAndReroute(allocation, clusterState);
        // starting replicas
        clusterState = startInitializingShardsAndReroute(allocation, clusterState);

        logger.info("--> verifying all is allocated");
        assertThat(clusterState.getRoutingNodes().node("node1").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node1").iterator().next().state()).isEqualTo(STARTED);
        assertThat(clusterState.getRoutingNodes().node("node2").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node2").iterator().next().state()).isEqualTo(STARTED);

        logger.info("--> fail node with primary");
        String nodeIdToFail = clusterState.routingTable().index("test").shard(0).primaryShard().currentNodeId();
        String nodeIdRemaining = nodeIdToFail.equals("node1") ? "node2" : "node1";
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder()
                                                                    .add(newNode(nodeIdRemaining))
        ).build();

        clusterState = allocation.disassociateDeadNodes(clusterState, true, "reroute");

        assertThat(clusterState.getRoutingNodes().node(nodeIdRemaining).iterator().next().primary()).isEqualTo(true);
        assertThat(clusterState.getRoutingNodes().node(nodeIdRemaining).iterator().next().state()).isEqualTo(STARTED);
    }

    public void testDeadNodeWhileRelocatingOnToNode() {
        AllocationService allocation = createAllocationService(Settings.builder()
                                                                   .put("cluster.routing.allocation.node_concurrent_recoveries", 10)
                                                                   .put(ClusterRebalanceAllocationDecider.CLUSTER_ROUTING_ALLOCATION_ALLOW_REBALANCE_SETTING.getKey(), "always")
                                                                   .build());

        logger.info("--> building initial routing table");
        Metadata metadata = Metadata.builder()
            .put(IndexMetadata.builder("test").settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(1))
            .build();
        RoutingTable routingTable = RoutingTable.builder()
            .addAsNew(metadata.index("test"))
            .build();
        ClusterState clusterState = ClusterState.builder(org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING
                                                             .getDefault(Settings.EMPTY)).metadata(metadata).routingTable(routingTable).build();

        logger.info("--> adding 2 nodes on same rack and do rerouting");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder()
                                                                    .add(newNode("node1"))
                                                                    .add(newNode("node2"))
        ).build();

        clusterState = allocation.reroute(clusterState, "reroute");

        // starting primaries
        clusterState = startInitializingShardsAndReroute(allocation, clusterState);
        // starting replicas
        clusterState = startInitializingShardsAndReroute(allocation, clusterState);

        logger.info("--> verifying all is allocated");
        assertThat(clusterState.getRoutingNodes().node("node1").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node1").iterator().next().state()).isEqualTo(STARTED);
        assertThat(clusterState.getRoutingNodes().node("node2").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node2").iterator().next().state()).isEqualTo(STARTED);

        logger.info("--> adding additional node");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes())
                                                                    .add(newNode("node3"))
        ).build();
        clusterState = allocation.reroute(clusterState, "reroute");

        assertThat(clusterState.getRoutingNodes().node("node1").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node1").iterator().next().state()).isEqualTo(STARTED);
        assertThat(clusterState.getRoutingNodes().node("node2").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node2").iterator().next().state()).isEqualTo(STARTED);
        assertThat(clusterState.getRoutingNodes().node("node3").size()).isEqualTo(0);

        String origPrimaryNodeId = clusterState.routingTable().index("test").shard(0).primaryShard().currentNodeId();
        String origReplicaNodeId = clusterState.routingTable().index("test").shard(0).replicaShards().get(0).currentNodeId();

        logger.info("--> moving primary shard to node3");
        AllocationService.CommandsResult commandsResult = allocation.reroute(clusterState, new AllocationCommands(
            new MoveAllocationCommand("test", 0, clusterState.routingTable().index("test")
                .shard(0).primaryShard().currentNodeId(), "node3")), false, false);
        assertThat(commandsResult.getClusterState(), not(equalTo(clusterState)));
        clusterState = commandsResult.getClusterState();
        assertThat(clusterState.getRoutingNodes().node(origPrimaryNodeId).iterator().next().state()).isEqualTo(RELOCATING);
        assertThat(clusterState.getRoutingNodes().node("node3").iterator().next().state()).isEqualTo(INITIALIZING);

        logger.info("--> fail primary shard recovering instance on node3 being initialized by killing node3");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder()
                                                                    .add(newNode(origPrimaryNodeId))
                                                                    .add(newNode(origReplicaNodeId))
        ).build();
        clusterState = allocation.disassociateDeadNodes(clusterState, true, "reroute");

        assertThat(clusterState.getRoutingNodes().node(origPrimaryNodeId).iterator().next().state()).isEqualTo(STARTED);
        assertThat(clusterState.getRoutingNodes().node(origReplicaNodeId).iterator().next().state()).isEqualTo(STARTED);
    }

    public void testDeadNodeWhileRelocatingOnFromNode() {
        AllocationService allocation = createAllocationService(Settings.builder()
                                                                   .put("cluster.routing.allocation.node_concurrent_recoveries", 10)
                                                                   .put(ClusterRebalanceAllocationDecider.CLUSTER_ROUTING_ALLOCATION_ALLOW_REBALANCE_SETTING.getKey(), "always")
                                                                   .build());

        logger.info("--> building initial routing table");
        Metadata metadata = Metadata.builder()
            .put(IndexMetadata.builder("test").settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(1))
            .build();
        RoutingTable routingTable = RoutingTable.builder()
            .addAsNew(metadata.index("test"))
            .build();
        ClusterState clusterState = ClusterState.builder(org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING
                                                             .getDefault(Settings.EMPTY)).metadata(metadata).routingTable(routingTable).build();

        logger.info("--> adding 2 nodes on same rack and do rerouting");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder()
                                                                    .add(newNode("node1"))
                                                                    .add(newNode("node2"))
        ).build();

        clusterState = allocation.reroute(clusterState, "reroute");

        // starting primaries
        clusterState = startInitializingShardsAndReroute(allocation, clusterState);
        // starting replicas
        clusterState = startInitializingShardsAndReroute(allocation, clusterState);

        logger.info("--> verifying all is allocated");
        assertThat(clusterState.getRoutingNodes().node("node1").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node1").iterator().next().state()).isEqualTo(STARTED);
        assertThat(clusterState.getRoutingNodes().node("node2").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node2").iterator().next().state()).isEqualTo(STARTED);

        logger.info("--> adding additional node");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes())
                                                                    .add(newNode("node3"))
        ).build();
        clusterState = allocation.reroute(clusterState, "reroute");

        assertThat(clusterState.getRoutingNodes().node("node1").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node1").iterator().next().state()).isEqualTo(STARTED);
        assertThat(clusterState.getRoutingNodes().node("node2").size()).isEqualTo(1);
        assertThat(clusterState.getRoutingNodes().node("node2").iterator().next().state()).isEqualTo(STARTED);
        assertThat(clusterState.getRoutingNodes().node("node3").size()).isEqualTo(0);

        String origPrimaryNodeId = clusterState.routingTable().index("test").shard(0).primaryShard().currentNodeId();
        String origReplicaNodeId = clusterState.routingTable().index("test").shard(0).replicaShards().get(0).currentNodeId();

        logger.info("--> moving primary shard to node3");
        AllocationService.CommandsResult commandsResult = allocation.reroute(clusterState, new AllocationCommands(
            new MoveAllocationCommand("test",0 , clusterState.routingTable().index("test")
                .shard(0).primaryShard().currentNodeId(), "node3")), false, false);
        assertThat(commandsResult.getClusterState(), not(equalTo(clusterState)));
        clusterState = commandsResult.getClusterState();
        assertThat(clusterState.getRoutingNodes().node(origPrimaryNodeId).iterator().next().state()).isEqualTo(RELOCATING);
        assertThat(clusterState.getRoutingNodes().node("node3").iterator().next().state()).isEqualTo(INITIALIZING);

        logger.info("--> fail primary shard recovering instance on 'origPrimaryNodeId' being relocated");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder()
                                                                    .add(newNode("node3"))
                                                                    .add(newNode(origReplicaNodeId))
        ).build();
        clusterState = allocation.disassociateDeadNodes(clusterState, true, "reroute");

        assertThat(clusterState.getRoutingNodes().node(origReplicaNodeId).iterator().next().state()).isEqualTo(STARTED);
        assertThat(clusterState.getRoutingNodes().node("node3").iterator().next().state()).isEqualTo(INITIALIZING);
    }
}
