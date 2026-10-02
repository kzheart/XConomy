# 木兰 XConomy 分支

Fork 自 YiC200333/XConomy，基于 2.26.3 标签，版本为 2.26.3-mulan.3。保留原 GPL-3.0 许可，修改源码发布在 kzheart/XConomy 的 perf/mulan-postgresql 分支。

## 存储与线程

新增 PostgreSQL 后端。余额列使用 numeric(20,2)，JDBC 使用 BigDecimal；Vault 的公开接口仍为 double。适配建表、大小写查找、重复创建忽略、UUID/link 和登录时间 upsert、时间戳及交易记录。驱动随 Bukkit/Paper 产物打包并重定位。原 MySQL/MariaDB/SQLite 配置保留。

启用缓存时，玩家/非玩家余额快照交给插件持有的单线程有界队列（10,000 个待处理操作），保持相对操作和绝对设置顺序；SQLite 也使用该队列及独立 JDBC 连接。缓存更新和账户事件保留原线程，不向工作线程传递可变缓存对象。正常关闭等待最多 30 秒排空再关闭数据库。单次玩家余额更新与交易记录在同一事务提交，数据库写失败可见并拒绝后续入队，不能把它当成落盘成功。故障后须人工对账缓存/数据库；队列不作为耐久日志，强杀、断电或连接中断下不承诺跨商店/货币的 exactly-once。

SQL 缓存未命中、账户删除和全体余额操作使用队列屏障，避免读取旧余额或让后提交的设置越过未完成写入。屏障可能等待，不代表所有冷读取/管理员批量操作无阻塞。Vault 调用者仍应遵守平台与缓存线程要求，MySQL/SQLite 的多子服同步保留上游机制；PG 玩家钱包由数据库行锁核验扣款，在线余额展示通过下述刷新机制同步。

PostgreSQL 玩家钱包在数据库行锁内读取、核验和更新余额，余额不足时原子拒绝。新增异步钱包方法，MulanShop 等待提交成功后才执行其他扣费与奖励；Vault 的同步方法保持同步返回合约，因此普通第三方 Vault 调用及原生管理/转账指令可能等待 PG。离线账户从提交后的数据库读取，在线缓存在空闲时每秒异步刷新，刷新检查本地提交代次以避开覆盖新写入。余额缓存仅用于展示，PG 钱包扣款不信任该缓存。

PG 玩家退出时先排空本节点余额写入，再移除账户缓存；进入节点时废弃该账户旧缓存，避免正常切服读取尚未落盘的旧余额。切服边界可能等待数据库，该退出屏障不能恢复强杀源节点遗失的本地排队任务；同一 PG 玩家账户的并发加减款另外受数据库行锁保护。

## PostgreSQL 配置

在 database.yml 中设置 Settings.storage-type 为 PostgreSQL，Settings.usepool 为 true，保留 config.yml 的 Settings.disable-cache 为 false，并填写 PostgreSQL 段：host、port、database、user、pass、table-suffix、sslmode；也可用 jdbc-url 覆盖连接地址。例子在打包的 database.yml。凭据不提交仓库。建议每个业务库独立角色；修改数据库配置需完整重启。

table-suffix 仅允许字母、数字、下划线，展开 %sign% 后最长 45 字符。仅支持受信任管理员配置的 SQL 标识符。SQLite 转 PG 不自动覆盖：停写、备份、导入全量账户、逐行核对，再切换配置。回滚前先把 PG 的最新余额导出并核对，不能直接恢复旧 SQLite 丢弃切换后的交易。

## 构建与验证

使用 Maven 3.9 / JDK 17+：`mvn -B -ntp -pl XConomy-Paper -am package`。上游 1.15-SNAPSHOT 已不可获取，编译用 BungeeCord 1.16-R0.4；PAPI 使用可获取的 2.11.6 和官方 releases 仓库。移除 compiler 的测试跳过配置。仅验证目标 Paper 1.21.1 / Java 21，未验证 Sponge/Folia/旧 Bukkit 全部组合。

Core 共 7 项 JUnit，覆盖顺序/排空、有界队列、失败拒绝、SQL 翻译与原子余额；设置 XCONOMY_TEST_PG_URL、XCONOMY_TEST_PG_USER、XCONOMY_TEST_PG_PASSWORD 可运行真实 PostgreSQL 场景，未配置 URL 会跳过实库部分。只能指向专用测试库，测试临时 schema 会被删除。

已在木兰原 Spawn 与 Lobby 节点验证共享 PG、同账户并发扣款、加减款、在线展示刷新、扣款中切服补偿，以及全服限购竞争。具体容量与验收数据见本分支 docs/mulan-load-test-20261002.md。
