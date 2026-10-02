package me.yic.xconomy;
import me.yic.xconomy.utils.OrderedWriter;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;

public class OrderedWriterTest {
 @Test public void updatesAndAbsoluteSetsKeepSubmissionOrderAndCloseDrains() {
  int[] balance={100}; List<Integer> order=new ArrayList<>();
  try(OrderedWriter writer=new OrderedWriter("test-storage",1000,error->{throw new AssertionError(error);})) {
   writer.submit(()->{balance[0]=500;order.add(balance[0]);});
   for(int i=0;i<100;i++)writer.submit(()->{balance[0]-=2;order.add(balance[0]);});
   writer.submit(()->{balance[0]+=20;order.add(balance[0]);});
  }
  assertEquals(320,balance[0]); assertEquals(102,order.size());
  assertEquals(Integer.valueOf(500),order.get(0));
 }
 @Test public void failuresAreVisibleAndFurtherWritesAreRejected() throws Exception {
  List<Throwable> failures=new CopyOnWriteArrayList<>();
  OrderedWriter writer=new OrderedWriter("failure-storage",10,failures::add);
  writer.submit(()->{throw new IllegalStateException("database");});
  try {writer.flush();fail();}catch(IllegalStateException expected){}
  assertFalse(writer.healthy()); assertEquals(1,failures.size());
  try {writer.submit(()->{});fail();}catch(RejectedExecutionException expected){}
  try {writer.close();fail();}catch(IllegalStateException expected){}
 }
 @Test public void queueIsBoundedAndNeverRunsIOOnTheCaller() throws Exception {
  CountDownLatch gate=new CountDownLatch(1),started=new CountDownLatch(1);
  OrderedWriter writer=new OrderedWriter("bounded-storage",1,error->{throw new AssertionError(error);});
  writer.submit(()->{started.countDown();try{gate.await();}catch(InterruptedException e){throw new RuntimeException(e);}});
  assertTrue(started.await(5,TimeUnit.SECONDS)); writer.submit(()->{});
  try {writer.submit(()->fail("caller executed IO"));fail();}catch(RejectedExecutionException expected){}
  gate.countDown();writer.close();
 }
}
