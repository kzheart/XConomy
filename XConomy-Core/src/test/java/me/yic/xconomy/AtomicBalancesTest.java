package me.yic.xconomy;
import me.yic.xconomy.data.sql.AtomicBalances;import org.junit.Test;import org.junit.Assume;import static org.junit.Assert.*;import java.sql.*;import java.math.BigDecimal;import java.util.*;import java.util.concurrent.*;
public class AtomicBalancesTest {
 @Test public void realPostgresConcurrentDebitsRefundsSetsAndRollback()throws Exception{
  String url=System.getenv("XCONOMY_TEST_PG_URL");Assume.assumeTrue(url!=null&&!url.isEmpty());String user=System.getenv("XCONOMY_TEST_PG_USER"),pass=System.getenv("XCONOMY_TEST_PG_PASSWORD");String table="xc_atomic_"+UUID.randomUUID().toString().replace("-","");UUID id=UUID.randomUUID();BigDecimal max=new BigDecimal("9999999999999999.99");ExecutorService workers=Executors.newFixedThreadPool(8);
  try(Connection setup=DriverManager.getConnection(url,user,pass)){
   try(Statement s=setup.createStatement()){s.execute("create table "+table+"(uid varchar(50) primary key,player varchar(50),balance numeric(20,2))");s.execute("insert into "+table+" values('"+id+"','test',10)");}
   try{
    List<Future<Boolean>> requests=new ArrayList<>();CountDownLatch start=new CountDownLatch(1);
    for(int i=0;i<40;i++)requests.add(workers.submit(()->{start.await();try(Connection c=DriverManager.getConnection(url,user,pass)){return AtomicBalances.apply(c,table,id,BigDecimal.TEN,false,max,r->{}).successful;}}));start.countDown();int accepted=0;for(Future<Boolean> f:requests)if(f.get(15,TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);
    List<Future<?>> updates=new ArrayList<>();for(int n=0;n<8;n++)updates.add(workers.submit(()->{try(Connection c=DriverManager.getConnection(url,user,pass)){for(int i=0;i<25;i++){assertTrue(AtomicBalances.apply(c,table,id,new BigDecimal("0.10"),true,max,r->{}).successful);assertTrue(AtomicBalances.apply(c,table,id,new BigDecimal("0.05"),false,max,r->{}).successful);}}catch(SQLException e){throw new RuntimeException(e);}}));for(Future<?> f:updates)f.get(30,TimeUnit.SECONDS);
    assertEquals(new BigDecimal("10.00"),balance(setup,table,id));
    assertEquals(new BigDecimal("50.00"),AtomicBalances.apply(setup,table,id,new BigDecimal("50.00"),null,max,r->{}).balance);
    try{AtomicBalances.apply(setup,table,id,BigDecimal.ONE,false,max,r->{throw new SQLException("record failed");});fail();}catch(SQLException expected){}
    assertEquals(new BigDecimal("50.00"),balance(setup,table,id));
   }finally{workers.shutdownNow();try(Statement s=setup.createStatement()){s.execute("drop table "+table);}}
  }
 }
 private static BigDecimal balance(Connection c,String table,UUID id)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select balance from "+table+" where UID='"+id+"'")){assertTrue(r.next());return r.getBigDecimal(1);}}
}
