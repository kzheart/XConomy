package me.yic.xconomy.utils;
import me.yic.xconomy.data.caches.Cache;import me.yic.xconomy.data.sql.PgWallet;import me.yic.xconomy.data.syncdata.PlayerData;import me.yic.xconomy.XConomy;
import org.bukkit.Bukkit;import org.bukkit.scheduler.BukkitTask;import java.util.*;import java.math.BigDecimal;
/** PG is authoritative. Refresh views on the main thread without overwriting newer local operations. */
public final class PgCacheRefresher {
 private static BukkitTask task;private static volatile boolean open;
 public static void start(){if(!PgWallet.enabled())return;open=true;task=Bukkit.getScheduler().runTaskTimer(XConomy.getInstance(),()->{
  if(StorageWrites.pending()!=0||!StorageWrites.healthy())return;
  Set<UUID> ids=new HashSet<>(Cache.pds.keySet());if(ids.isEmpty())return;long epoch=StorageWrites.generation();
  StorageWrites.observe(()->{Map<UUID,BigDecimal> balances=PgWallet.balances(ids);if(!open)return;Bukkit.getScheduler().runTask(XConomy.getInstance(),()->{
   if(!open||StorageWrites.pending()!=0||StorageWrites.generation()!=epoch)return;
   balances.forEach((id,amount)->{PlayerData pd=Cache.pds.get(id);if(pd!=null)Cache.updateIntoCache(id,pd,amount,amount);});
  });});
 },20,20);}
 public static void close(){open=false;if(task!=null){task.cancel();task=null;}}
}
