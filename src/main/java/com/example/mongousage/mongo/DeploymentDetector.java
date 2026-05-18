package com.example.mongousage.mongo;

import com.example.mongousage.model.DeploymentInfo;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DeploymentDetector {
    private DeploymentDetector() {
    }

    static DeploymentInfo detectTopology(Document hello, Document serverStatus) {
        DeploymentInfo info = new DeploymentInfo();
        Document safeHello = hello == null ? new Document() : hello;
        Document safeServerStatus = serverStatus == null ? new Document() : serverStatus;
        String processType = string(safeServerStatus.get("process"));
        info.setProcessType(processType);

        if (safeHello.getString("setName") != null) {
            info.setDeploymentMode("replicaSet");
            info.setReplicaSetName(safeHello.getString("setName"));
        } else if (isMongos(safeHello, processType)) {
            info.setDeploymentMode("sharded");
            info.setSharded(true);
        } else if (!safeHello.isEmpty()) {
            info.setDeploymentMode("standalone");
        }

        info.setPrimary(safeHello.getString("primary"));
        info.setHosts(strings(safeHello.get("hosts")));
        info.setArbiters(strings(safeHello.get("arbiters")));
        info.setSharded(info.isSharded() || isMongos(safeHello, processType));
        info.setNodeRole(nodeRole(safeHello, info, processType));
        info.setDeploymentSignals(topologySignals(safeHello, safeServerStatus, info));
        return info;
    }

    static void detectHosting(DeploymentInfo info, String target, Document buildInfo, Document hello,
                              Document serverStatus, Document getCmdLineOpts, Document hostInfo) {
        String signal = (target + " " + json(buildInfo) + " " + json(hello) + " " + json(serverStatus)
                + " " + json(getCmdLineOpts) + " " + json(hostInfo)).toLowerCase(Locale.ROOT);

        if (containsAny(signal, "mongodb.net", "atlas")) {
            info.setProvider("atlas");
            info.setHostingType("managed");
            info.setManagedService(true);
            info.setAtlasHint("possible Atlas-managed deployment");
        } else if (containsAny(signal, "docdb.amazonaws.com", "documentdb", "amazon documentdb")) {
            info.setProvider("amazon-documentdb");
            info.setHostingType("managed-compatible");
            info.setManagedService(true);
        } else if (containsAny(signal, "cosmos.azure.com", "cosmosdb", "azure cosmos")) {
            info.setProvider("azure-cosmosdb-mongodb");
            info.setHostingType("managed-compatible");
            info.setManagedService(true);
        } else if (containsAny(signal, "tencent", "mongodb.tencentcloudapi", "qcloud")) {
            info.setProvider("tencent-cloud-mongodb");
            info.setHostingType("managed");
            info.setManagedService(true);
        } else if (containsAny(signal, "aliyun", "alicloud", "dds.aliyuncs.com")) {
            info.setProvider("aliyun-mongodb");
            info.setHostingType("managed");
            info.setManagedService(true);
        } else if (containsAny(signal, "huaweicloud", "huawei cloud")) {
            info.setProvider("huawei-cloud-mongodb");
            info.setHostingType("managed");
            info.setManagedService(true);
        } else {
            info.setProvider("self-managed");
            info.setHostingType("self-managed");
            info.setManagedService(false);
        }

        List<String> signals = new ArrayList<>(info.getDeploymentSignals());
        signals.add("provider=" + info.getProvider());
        signals.add("hostingType=" + info.getHostingType());
        info.setDeploymentSignals(signals);
    }

    static void enrichReplicaSetStatus(DeploymentInfo info, Document replSetStatus) {
        List<String> members = new ArrayList<>();
        Object rawMembers = replSetStatus == null ? null : replSetStatus.get("members");
        if (rawMembers instanceof List<?> memberList) {
            for (Object rawMember : memberList) {
                if (rawMember instanceof Document member) {
                    String name = string(member.get("name"));
                    String state = string(member.get("stateStr"));
                    if (!name.isBlank()) {
                        members.add(state.isBlank() ? name : name + ":" + state);
                    }
                }
            }
        }
        info.setReplicaSetMembers(members);
        info.setReplicaSetMemberCount(members.size());
    }

    static void enrichShardList(DeploymentInfo info, Document shardList) {
        List<String> shardNames = new ArrayList<>();
        Object shards = shardList == null ? null : shardList.get("shards");
        if (shards instanceof List<?> shardDocuments) {
            for (Object rawShard : shardDocuments) {
                if (rawShard instanceof Document shard) {
                    String shardName = string(shard.get("_id"));
                    if (!shardName.isBlank()) {
                        shardNames.add(shardName);
                    }
                }
            }
        }
        info.setShardNames(shardNames);
        info.setShardCount(shardNames.size());
    }

    private static boolean isMongos(Document hello, String processType) {
        String msg = string(hello.get("msg")).toLowerCase(Locale.ROOT);
        return "isdbgrid".equals(msg) || msg.contains("mongos") || "mongos".equalsIgnoreCase(processType);
    }

    private static String nodeRole(Document hello, DeploymentInfo info, String processType) {
        if (info.isSharded() || "mongos".equalsIgnoreCase(processType)) {
            return "mongos";
        }
        if ("replicaSet".equals(info.getDeploymentMode())) {
            if (Boolean.TRUE.equals(hello.get("isWritablePrimary")) || Boolean.TRUE.equals(hello.get("ismaster"))) {
                return "primary";
            }
            if (Boolean.TRUE.equals(hello.get("secondary"))) {
                return "secondary";
            }
            if (Boolean.TRUE.equals(hello.get("arbiterOnly"))) {
                return "arbiter";
            }
            return "replicaSetMember";
        }
        if ("standalone".equals(info.getDeploymentMode())) {
            return "standalone";
        }
        return "";
    }

    private static List<String> topologySignals(Document hello, Document serverStatus, DeploymentInfo info) {
        List<String> signals = new ArrayList<>();
        signals.add("mode=" + info.getDeploymentMode());
        if (!info.getProcessType().isBlank()) {
            signals.add("process=" + info.getProcessType());
        }
        if (!info.getNodeRole().isBlank()) {
            signals.add("nodeRole=" + info.getNodeRole());
        }
        if (hello.getString("setName") != null) {
            signals.add("hello.setName");
        }
        if (info.isSharded()) {
            signals.add("hello.msg=isdbgrid/process=mongos");
        }
        Document storageEngine = serverStatus.get("storageEngine", Document.class);
        if (storageEngine != null && storageEngine.getString("name") != null) {
            signals.add("storageEngine=" + storageEngine.getString("name"));
        }
        return signals;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String json(Document document) {
        return document == null ? "" : document.toJson();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return new ArrayList<>();
    }
}
