package me.yic.xconomy.data.sql;
import me.yic.xconomy.XConomyLoad;import me.yic.xconomy.info.RecordInfo;import me.yic.xconomy.data.syncdata.PlayerData;import me.yic.xconomy.utils.StorageWrites;
import java.math.BigDecimal;import java.sql.*;import java.util.UUID;import java.util.concurrent.CompletableFuture;
/** No Bukkit objects cross this asynchronous database boundary. */
public final class PgWallet extends SQL {
 private PgWallet(){}
 public static boolean enabled(){return XConomyLoad.DConfig.isPostgreSQL();}
 public static CompletableFuture<AtomicBalances.Result> change(UUID id,BigDecimal amount,Boolean add,RecordInfo info){
  if(!enabled())throw new IllegalStateException("PostgreSQL required");
  return StorageWrites.call(()->{
   Connection c=database.getConnectionAndCheck();if(c==null)throw new IllegalStateException("PG unavailable");
   try{return AtomicBalances.apply(c,tableName,id,amount,add,me.yic.xconomy.data.DataFormat.maxNumber.min(new BigDecimal("999999999999999999.99")),r->record(c,new PlayerData(id,r.name,r.balance),add,amount,r.balance,info));}
   catch(SQLException e){throw new IllegalStateException("Atomic PostgreSQL balance update failed",e);}
   finally{database.closeHikariConnection(c);}
  });
 }
 public static java.util.Map<UUID,BigDecimal> balances(java.util.Set<UUID> ids){
  java.util.Map<UUID,BigDecimal> result=new java.util.HashMap<>();Connection c=database.getConnectionAndCheck();if(c==null)throw new IllegalStateException("PG unavailable");
  try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select UID,balance from "+tableName)){while(r.next()){UUID id=UUID.fromString(r.getString(1));if(ids.contains(id))result.put(id,r.getBigDecimal(2));}return result;}
  catch(SQLException e){throw new IllegalStateException("PG cache refresh failed",e);}finally{database.closeHikariConnection(c);}
 }
}
