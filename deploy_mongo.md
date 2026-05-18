# MongoDB 多版本测试集群部署与管理

本文档记录 2026-05-18 在附件服务器上部署 MongoDB v4/v5/v6/v7 的 Replica Set 与 Sharding 测试集群的实际命令、连接信息和 `compat-test` 验证结果。

> 安全说明：服务器账号密码来自本地附件 `/Users/lan/Downloads/machineinfo`，本文档不记录明文密码。当前已在可登录服务器写入本机 SSH 公钥，后续管理优先使用 SSH key。

## 服务器状态

| 服务器 | 登录状态 | 容器运行时 | 用途 | 备注 |
|---|---:|---|---|---|
| `10.2.106.188` | 可 SSH key 登录 | `/usr/bin/docker`，实际为 Podman 3.3.1 Docker 兼容模式 | 4 套 Replica Set | CPU 支持 AVX/AVX2，可运行 MongoDB 5+ |
| `10.2.106.48` | 可 SSH key 登录 | Docker 27.1.1 | 4 套 Sharding | CPU 支持 AVX/AVX2，可运行 MongoDB 5+ |
| `10.2.106.166` | 可 SSH key 登录 | 未安装 Docker/Podman | 未使用 | 可作为后续扩容节点 |
| `10.2.106.93` | TCP 22 可达，但 SSH banner exchange 超时 | 未确认 | 未使用 | 需要先修复 SSH 登录问题 |

## 部署规划

所有集群均使用 Docker/Podman host network，避免容器内副本集地址与外部客户端地址不一致。测试集群未启用认证，仅用于兼容性测试和工具验证，不建议暴露到生产网络。

数据目录统一放在远端：

```bash
/data/mongo-usage-report-lab
```

### Replica Set 集群

部署在 `10.2.106.188`。

| 集群 | MongoDB 实际版本 | 容器 | Replica Set | 端口 | URI | compat-test |
|---|---|---|---|---:|---|---|
| v4.4 RS | 4.4.30 | `muc-v44-rs` | `rs44` | 44017 | `mongodb://10.2.106.188:44017/admin?replicaSet=rs44&directConnection=true` | 120/120 通过 |
| v5.0 RS | 5.0.33 | `muc-v50-rs` | `rs50` | 50017 | `mongodb://10.2.106.188:50017/admin?replicaSet=rs50&directConnection=true` | 120/120 通过 |
| v6.0 RS | 6.0.28 | `muc-v60-rs` | `rs60` | 60017 | `mongodb://10.2.106.188:60017/admin?replicaSet=rs60&directConnection=true` | 120/120 通过 |
| v7.0 RS | 7.0.34 | `muc-v70-rs` | `rs70` | 61017 | `mongodb://10.2.106.188:61017/admin?replicaSet=rs70&directConnection=true` | 120/120 通过 |

### Sharding 集群

部署在 `10.2.106.48`。每套集群包含 1 个 config server、1 个 shard replica set、1 个 mongos。

| 集群 | MongoDB 实际版本 | mongos | Config RS | Shard RS | 端口 | URI | compat-test |
|---|---|---|---|---|---:|---|---|
| v4.4 Sharding | 4.4.30 | `muc-v44-mongos` | `cfg44` / `muc-v44-cfg:44119` | `shard44` / `muc-v44-shard:44118` | 44117 | `mongodb://10.2.106.48:44117/admin?directConnection=true` | 118/120 通过 |
| v5.0 Sharding | 5.0.33 | `muc-v50-mongos` | `cfg50` / `muc-v50-cfg:50119` | `shard50` / `muc-v50-shard:50118` | 50117 | `mongodb://10.2.106.48:50117/admin?directConnection=true` | 118/120 通过 |
| v6.0 Sharding | 6.0.28 | `muc-v60-mongos` | `cfg60` / `muc-v60-cfg:60119` | `shard60` / `muc-v60-shard:60118` | 60117 | `mongodb://10.2.106.48:60117/admin?directConnection=true` | 118/120 通过 |
| v7.0 Sharding | 7.0.34 | `muc-v70-mongos` | `cfg70` / `muc-v70-cfg:61119` | `shard70` / `muc-v70-shard:61118` | 61117 | `mongodb://10.2.106.48:61117/admin?directConnection=true` | 118/120 通过 |

Sharding 的 2 个失败项在四个版本一致：

| 用例编号 | 命令 | 失败原因 |
|---|---|---|
| `command-getparameter-fcv` | `db.adminCommand({ getParameter: 1, featureCompatibilityVersion: 1 })` | mongos 不支持该参数，返回 `no option found to get` |
| `command-top` | `db.adminCommand({ top: 1 })` | mongos 不支持 `top`，返回 `no such cmd: top` |

这两个结果属于 mongos 层命令支持差异，不是容器或集群部署失败。

## 部署命令

### Replica Set 部署命令

以下命令在本机执行，通过 SSH 在 `10.2.106.188` 部署四套 Replica Set：

```bash
ssh root@10.2.106.188 'bash -s' <<'REMOTE'
set -euo pipefail
HOST_IP=10.2.106.188
ROOT=/data/mongo-usage-report-lab
mkdir -p "$ROOT"

mongo_shell() {
  local container="$1" port="$2" js="$3"
  docker exec "$container" bash -lc 'set -e; shell=$(command -v mongosh || command -v mongo); "$shell" --quiet --port "$0" --eval "$1"' "$port" "$js"
}

wait_mongo() {
  local container="$1" port="$2"
  for i in $(seq 1 90); do
    if mongo_shell "$container" "$port" 'db.adminCommand({ping:1}).ok' >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  docker logs --tail 120 "$container" >&2 || true
  return 1
}

wait_primary() {
  local container="$1" port="$2"
  for i in $(seq 1 90); do
    state=$(mongo_shell "$container" "$port" 'try { rs.status().myState } catch (e) { 0 }' 2>/dev/null | tail -1 | tr -d '\r[:space:]' || true)
    if [ "$state" = "1" ]; then return 0; fi
    sleep 2
  done
  return 1
}

deploy_rs() {
  local key="$1" image_tag="$2" port="$3" rs="$4"
  local name="muc-v${key}-rs"
  local image="docker.io/library/mongo:${image_tag}"
  docker pull "$image"
  docker rm -f "$name" >/dev/null 2>&1 || true
  rm -rf "$ROOT/$name"
  mkdir -p "$ROOT/$name"
  chmod 777 "$ROOT/$name"
  docker run -d --name "$name" --restart=unless-stopped --network host \
    -v "$ROOT/$name:/data/db:Z" \
    "$image" --replSet "$rs" --bind_ip_all --port "$port" --dbpath /data/db --oplogSize 128
  wait_mongo "$name" "$port"
  mongo_shell "$name" "$port" "try { rs.initiate({_id:'$rs', members:[{_id:0, host:'$HOST_IP:$port'}]}) } catch (e) { if (!String(e).match(/already initialized|already initiated/i)) { throw e; } }" >/dev/null
  wait_primary "$name" "$port"
}

deploy_rs 44 4.4 44017 rs44
deploy_rs 50 5.0 50017 rs50
deploy_rs 60 6.0 60017 rs60
deploy_rs 70 7.0 61017 rs70
REMOTE
```

### Sharding 部署命令

以下命令在本机执行，通过 SSH 在 `10.2.106.48` 部署四套 Sharding 集群：

```bash
ssh root@10.2.106.48 'bash -s' <<'REMOTE'
set -euo pipefail
HOST_IP=10.2.106.48
ROOT=/data/mongo-usage-report-lab
mkdir -p "$ROOT"

mongo_shell() {
  local container="$1" port="$2" js="$3"
  docker exec "$container" bash -lc 'set -e; shell=$(command -v mongosh || command -v mongo); "$shell" --quiet --port "$0" --eval "$1"' "$port" "$js"
}

wait_mongo() {
  local container="$1" port="$2"
  for i in $(seq 1 90); do
    if mongo_shell "$container" "$port" 'db.adminCommand({ping:1}).ok' >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  docker logs --tail 120 "$container" >&2 || true
  return 1
}

wait_primary() {
  local container="$1" port="$2"
  for i in $(seq 1 90); do
    state=$(mongo_shell "$container" "$port" 'try { rs.status().myState } catch (e) { 0 }' 2>/dev/null | tail -1 | tr -d '\r[:space:]' || true)
    if [ "$state" = "1" ]; then return 0; fi
    sleep 2
  done
  return 1
}

deploy_sharded() {
  local key="$1" image_tag="$2" mongos_port="$3" shard_port="$4" cfg_port="$5" cfg_rs="$6" shard_rs="$7"
  local image="docker.io/library/mongo:${image_tag}"
  local cfg="muc-v${key}-cfg"
  local shard="muc-v${key}-shard"
  local mongos="muc-v${key}-mongos"
  docker pull "$image"
  docker rm -f "$mongos" "$shard" "$cfg" >/dev/null 2>&1 || true
  rm -rf "$ROOT/$cfg" "$ROOT/$shard"
  mkdir -p "$ROOT/$cfg" "$ROOT/$shard"
  docker run -d --name "$cfg" --restart=unless-stopped --network host \
    -v "$ROOT/$cfg:/data/db" \
    "$image" --configsvr --replSet "$cfg_rs" --bind_ip_all --port "$cfg_port" --dbpath /data/db
  docker run -d --name "$shard" --restart=unless-stopped --network host \
    -v "$ROOT/$shard:/data/db" \
    "$image" --shardsvr --replSet "$shard_rs" --bind_ip_all --port "$shard_port" --dbpath /data/db --oplogSize 128
  wait_mongo "$cfg" "$cfg_port"
  wait_mongo "$shard" "$shard_port"
  mongo_shell "$cfg" "$cfg_port" "try { rs.initiate({_id:'$cfg_rs', configsvr:true, members:[{_id:0, host:'$HOST_IP:$cfg_port'}]}) } catch (e) { if (!String(e).match(/already initialized|already initiated/i)) { throw e; } }" >/dev/null
  mongo_shell "$shard" "$shard_port" "try { rs.initiate({_id:'$shard_rs', members:[{_id:0, host:'$HOST_IP:$shard_port'}]}) } catch (e) { if (!String(e).match(/already initialized|already initiated/i)) { throw e; } }" >/dev/null
  wait_primary "$cfg" "$cfg_port"
  wait_primary "$shard" "$shard_port"
  docker run -d --name "$mongos" --restart=unless-stopped --network host \
    "$image" mongos --configdb "$cfg_rs/$HOST_IP:$cfg_port" --bind_ip_all --port "$mongos_port"
  wait_mongo "$mongos" "$mongos_port"
  mongo_shell "$mongos" "$mongos_port" "try { sh.addShard('$shard_rs/$HOST_IP:$shard_port') } catch (e) { if (!String(e).match(/already exists|already.*shard/i)) { throw e; } }" >/dev/null
}

deploy_sharded 44 4.4 44117 44118 44119 cfg44 shard44
deploy_sharded 50 5.0 50117 50118 50119 cfg50 shard50
deploy_sharded 60 6.0 60117 60118 60119 cfg60 shard60
deploy_sharded 70 7.0 61117 61118 61119 cfg70 shard70
REMOTE
```

## 统一管理命令

### 查看状态

```bash
ssh root@10.2.106.188 'docker ps --format "{{.Names}} {{.Image}} {{.Status}}" | grep "muc-v.*-rs"'
ssh root@10.2.106.48 'docker ps --format "{{.Names}} {{.Image}} {{.Status}}" | grep "muc-v"'
```

### 启停集群

```bash
# Replica Set
ssh root@10.2.106.188 'docker stop muc-v44-rs muc-v50-rs muc-v60-rs muc-v70-rs'
ssh root@10.2.106.188 'docker start muc-v44-rs muc-v50-rs muc-v60-rs muc-v70-rs'

# Sharding
ssh root@10.2.106.48 'docker stop muc-v44-mongos muc-v44-shard muc-v44-cfg muc-v50-mongos muc-v50-shard muc-v50-cfg muc-v60-mongos muc-v60-shard muc-v60-cfg muc-v70-mongos muc-v70-shard muc-v70-cfg'
ssh root@10.2.106.48 'docker start muc-v44-cfg muc-v44-shard muc-v44-mongos muc-v50-cfg muc-v50-shard muc-v50-mongos muc-v60-cfg muc-v60-shard muc-v60-mongos muc-v70-cfg muc-v70-shard muc-v70-mongos'
```

### 查看日志

```bash
ssh root@10.2.106.188 'docker logs --tail 100 muc-v70-rs'
ssh root@10.2.106.48 'docker logs --tail 100 muc-v70-mongos'
```

### 删除集群

```bash
ssh root@10.2.106.188 'docker rm -f muc-v44-rs muc-v50-rs muc-v60-rs muc-v70-rs'
ssh root@10.2.106.48 'docker rm -f muc-v44-mongos muc-v44-shard muc-v44-cfg muc-v50-mongos muc-v50-shard muc-v50-cfg muc-v60-mongos muc-v60-shard muc-v60-cfg muc-v70-mongos muc-v70-shard muc-v70-cfg'
```

如需同时清理数据目录：

```bash
ssh root@10.2.106.188 'rm -rf /data/mongo-usage-report-lab/muc-v*-rs'
ssh root@10.2.106.48 'rm -rf /data/mongo-usage-report-lab/muc-v*-cfg /data/mongo-usage-report-lab/muc-v*-shard'
```

## compat-test 验证命令

测试命令在本地仓库 `/Users/lan/Documents/mongo` 下执行，报告输出到 `target/remote-compat`。

```bash
java -jar target/mongo-usage-collector.jar compat-test --mongo-version 4.4 --uri "mongodb://10.2.106.188:44017/admin?replicaSet=rs44&directConnection=true" --out target/remote-compat/v44-rs
java -jar target/mongo-usage-collector.jar compat-test --mongo-version 5.0 --uri "mongodb://10.2.106.188:50017/admin?replicaSet=rs50&directConnection=true" --out target/remote-compat/v50-rs
java -jar target/mongo-usage-collector.jar compat-test --mongo-version 6.0 --uri "mongodb://10.2.106.188:60017/admin?replicaSet=rs60&directConnection=true" --out target/remote-compat/v60-rs
java -jar target/mongo-usage-collector.jar compat-test --mongo-version 7.0 --uri "mongodb://10.2.106.188:61017/admin?replicaSet=rs70&directConnection=true" --out target/remote-compat/v70-rs

java -jar target/mongo-usage-collector.jar compat-test --mongo-version 4.4 --uri "mongodb://10.2.106.48:44117/admin?directConnection=true" --out target/remote-compat/v44-sharded
java -jar target/mongo-usage-collector.jar compat-test --mongo-version 5.0 --uri "mongodb://10.2.106.48:50117/admin?directConnection=true" --out target/remote-compat/v50-sharded
java -jar target/mongo-usage-collector.jar compat-test --mongo-version 6.0 --uri "mongodb://10.2.106.48:60117/admin?directConnection=true" --out target/remote-compat/v60-sharded
java -jar target/mongo-usage-collector.jar compat-test --mongo-version 7.0 --uri "mongodb://10.2.106.48:61117/admin?directConnection=true" --out target/remote-compat/v70-sharded
```

每套集群会生成：

```bash
compat-test-report.json
compat-test-results.xlsx
```

本次输出路径：

```bash
target/remote-compat/v44-rs/compat-test-results.xlsx
target/remote-compat/v50-rs/compat-test-results.xlsx
target/remote-compat/v60-rs/compat-test-results.xlsx
target/remote-compat/v70-rs/compat-test-results.xlsx
target/remote-compat/v44-sharded/compat-test-results.xlsx
target/remote-compat/v50-sharded/compat-test-results.xlsx
target/remote-compat/v60-sharded/compat-test-results.xlsx
target/remote-compat/v70-sharded/compat-test-results.xlsx
```

## 本次验证结果

| 集群 | 总用例 | 通过 | 失败 | 结论 |
|---|---:|---:|---:|---|
| v4.4 Replica Set | 120 | 120 | 0 | 通过 |
| v5.0 Replica Set | 120 | 120 | 0 | 通过 |
| v6.0 Replica Set | 120 | 120 | 0 | 通过 |
| v7.0 Replica Set | 120 | 120 | 0 | 通过 |
| v4.4 Sharding | 120 | 118 | 2 | mongos 层不支持 `getParameter.featureCompatibilityVersion` 和 `top` |
| v5.0 Sharding | 120 | 118 | 2 | mongos 层不支持 `getParameter.featureCompatibilityVersion` 和 `top` |
| v6.0 Sharding | 120 | 118 | 2 | mongos 层不支持 `getParameter.featureCompatibilityVersion` 和 `top` |
| v7.0 Sharding | 120 | 118 | 2 | mongos 层不支持 `getParameter.featureCompatibilityVersion` 和 `top` |

