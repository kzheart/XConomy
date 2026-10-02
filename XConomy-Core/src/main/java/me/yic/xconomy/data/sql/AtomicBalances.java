package me.yic.xconomy.data.sql;
import java.math.BigDecimal;import java.sql.*;import java.util.UUID;
/** The database row is the authority for shared PostgreSQL accounts. */
public final class AtomicBalances {
 private AtomicBalances(){}
 public static final class Result {
  public final boolean successful;public final String name;public final BigDecimal before,balance;
  Result(boolean ok,String name,BigDecimal before,BigDecimal balance){this.successful=ok;this.name=name;this.before=before;this.balance=balance;}
  public boolean successful(){return successful;}public BigDecimal balance(){return balance;}
 }
 @FunctionalInterface public interface Recorder {void record(Result result)throws SQLException;}
 public static Result apply(Connection c,String table,UUID id,BigDecimal amount,Boolean add,BigDecimal maximum,Recorder recorder)throws SQLException{
  if(amount.signum()<0)throw new IllegalArgumentException("Negative amount");
  c.setAutoCommit(false);
  try {
   String name;BigDecimal before;
   try(PreparedStatement s=c.prepareStatement("select player,balance from "+table+" where UID=? for update")){s.setString(1,id.toString());try(ResultSet rs=s.executeQuery()){if(!rs.next()){c.rollback();return new Result(false,null,BigDecimal.ZERO,BigDecimal.ZERO);}name=rs.getString(1);before=rs.getBigDecimal(2);}}
   BigDecimal balance=add==null?amount:add?before.add(amount):before.subtract(amount);
   if(balance.signum()<0||balance.compareTo(maximum)>0){c.rollback();return new Result(false,name,before,before);}
   try(PreparedStatement s=c.prepareStatement("update "+table+" set balance=? where UID=?")){s.setBigDecimal(1,balance);s.setString(2,id.toString());if(s.executeUpdate()!=1)throw new SQLException("Missing balance row");}
   Result result=new Result(true,name,before,balance);recorder.record(result);c.commit();return result;
  }catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException r){e.addSuppressed(r);}throw e;}
  finally{c.setAutoCommit(true);}
 }
}
