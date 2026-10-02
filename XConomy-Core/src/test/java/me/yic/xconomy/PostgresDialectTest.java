package me.yic.xconomy;

import me.yic.xconomy.data.sql.PostgresDialect;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;

public class PostgresDialectTest {
 @Test public void translatesLegacyDialectWithoutChangingPlaceholderCount() {
  String s=PostgresDialect.translate("INSERT INTO xconomyuuid(UUID,DUUID) values(?,?) ON DUPLICATE KEY UPDATE DUUID = ?",false);
  assertTrue(s.contains("ON CONFLICT (UUID) DO UPDATE")); assertEquals(3,s.chars().filter(c->c=='?').count());
  assertEquals("select * from xconomy where player COLLATE \"C\" = ?",PostgresDialect.translate("select * from xconomy where binary player = ?",false));
  assertEquals("select * from xconomy where lower(player) = lower(?)",PostgresDialect.translate("select * from xconomy where player = ? COLLATE NOCASE",true));
 }
 @Test public void realPostgresSchemaBalancesUpsertsAndRollback() throws Exception {
  String url=System.getenv("XCONOMY_TEST_PG_URL");Assume.assumeTrue("Real PostgreSQL URL required",url!=null&&!url.isEmpty());
  String schema="xc_test_"+UUID.randomUUID().toString().replace("-","");
  try(Connection raw=DriverManager.getConnection(url,System.getenv("XCONOMY_TEST_PG_USER"),System.getenv("XCONOMY_TEST_PG_PASSWORD"))) {
   try(Statement s=raw.createStatement()){s.execute("create schema "+schema);s.execute("set search_path to "+schema);}
   try {
    Connection c=PostgresDialect.wrap(raw,false);
    try(Statement s=c.createStatement()) {
     for(int repeat=0;repeat<2;repeat++){
      s.execute("create table if not exists xconomy(UID varchar(50) not null, player varchar(50) not null, balance double(20,2) not null, hidden int(5) not null, primary key (UID)) default charset = utf8;");
      s.execute("create table if not exists xconomynon(account varchar(50) not null,balance double(20,2) not null,primary key (account)) default charset = utf8;");
      s.execute("create table if not exists xconomyuuid(UUID varchar(50) not null,DUUID varchar(50) not null,primary key (UUID)) default charset = utf8;");
      s.execute("create table if not exists xconomyrecord(id int(20) not null auto_increment,type varchar(50) not null,uid varchar(50) not null,player varchar(50) not null,balance double(20,2),amount double(20,2) not null,operation varchar(50) not null,command varchar(255) not null,comment varchar(255) not null,datetime datetime not null,primary key (id)) default charset = utf8;");
      s.execute("create table if not exists xconomylogin(UUID varchar(50) not null,last_time datetime not null,primary key (UUID)) default charset = utf8;");
     }
    }
    try(PreparedStatement s=c.prepareStatement("INSERT IGNORE INTO xconomy(UID,player,balance,hidden) values(?,?,?,?)")) {
     s.setString(1,"a");s.setString(2,"木兰Alice");s.setBigDecimal(3,new BigDecimal("9999999999999999.99"));s.setInt(4,0);
     assertEquals(1,s.executeUpdate());assertEquals(0,s.executeUpdate());
    }
    c.setAutoCommit(false);
    try(PreparedStatement s=c.prepareStatement("update xconomy set balance=balance-? where UID=?")){s.setBigDecimal(1,new BigDecimal("0.01"));s.setString(2,"a");s.executeUpdate();}
    c.commit();
    try(PreparedStatement s=c.prepareStatement("update xconomy set balance=0 where UID=?")){s.setString(1,"a");s.executeUpdate();}c.rollback();c.setAutoCommit(true);
    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select balance from xconomy where UID='a'")){assertTrue(r.next());assertEquals(new BigDecimal("9999999999999999.98"),r.getBigDecimal(1));}
    try(PreparedStatement s=c.prepareStatement("INSERT INTO xconomyuuid(UUID,DUUID) values(?,?) ON DUPLICATE KEY UPDATE DUUID = ?")){
     s.setString(1,"link");s.setString(2,"old");s.setString(3,"old");s.executeUpdate();s.setString(2,"a");s.setString(3,"a");s.executeUpdate();
    }
    try(PreparedStatement s=c.prepareStatement("select * from xconomy where UID = ifnull((select DUUID from xconomyuuid where UUID = ?), ?)")){
     s.setString(1,"link");s.setString(2,"fallback");try(ResultSet r=s.executeQuery()){assertTrue(r.next());assertEquals("a",r.getString(1));}
    }
    Connection insensitive=PostgresDialect.wrap(raw,true);
    try(PreparedStatement s=insensitive.prepareStatement("select * from xconomy where player = ?")){s.setString(1,"木兰alice");try(ResultSet r=s.executeQuery()){assertTrue(r.next());}}
    try(PreparedStatement s=c.prepareStatement("select * from xconomy where binary player = ?")){s.setString(1,"木兰alice");try(ResultSet r=s.executeQuery()){assertFalse(r.next());}}
    try(PreparedStatement s=c.prepareStatement("INSERT INTO xconomylogin(UUID,last_time) values(?,?) ON DUPLICATE KEY UPDATE last_time = ?")){
     s.setString(1,"a");s.setTimestamp(2,new Timestamp(1000));s.setTimestamp(3,new Timestamp(1000));s.executeUpdate();s.setTimestamp(3,new Timestamp(2000));s.executeUpdate();
    }
    try(PreparedStatement s=c.prepareStatement("INSERT INTO xconomyrecord(type,uid,player,balance,amount,operation,command,comment,datetime) values(?,?,?,?,?,?,?,?,?)")){
     for(int i=1;i<=3;i++)s.setString(i,"a");s.setBigDecimal(4,BigDecimal.TEN);s.setBigDecimal(5,BigDecimal.ONE);
     for(int i=6;i<=8;i++)s.setString(i,"test");s.setTimestamp(9,new Timestamp(System.currentTimeMillis()));assertEquals(1,s.executeUpdate());
    }
    try(PreparedStatement s=c.prepareStatement("INSERT IGNORE INTO xconomynon(account,balance) values(?,?)")){s.setString(1,"town");s.setBigDecimal(2,BigDecimal.TEN);assertEquals(1,s.executeUpdate());assertEquals(0,s.executeUpdate());}
   } finally {try(Statement s=raw.createStatement()){s.execute("drop schema "+schema+" cascade");}}
  }
 }
}
