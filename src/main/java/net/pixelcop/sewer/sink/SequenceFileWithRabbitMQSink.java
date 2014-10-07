package net.pixelcop.sewer.sink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import net.pixelcop.sewer.DrainSink;
import net.pixelcop.sewer.Event;
import net.pixelcop.sewer.SendRabbitMQTopic.RabbitMessageBatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.pixelcop.sewer.SendRabbitMQTopic;
import net.pixelcop.sewer.sink.durable.TransactionManager;

import com.evidon.nerf.AccessLogWritable;

/**
 * @author richard craparotta
 */
@DrainSink
public class SequenceFileWithRabbitMQSink extends SequenceFileSink {

  private static final Logger LOG = LoggerFactory.getLogger(SequenceFileWithRabbitMQSink.class);
  private ArrayList<RabbitMessageBatch> batches = new ArrayList<RabbitMessageBatch>(); 
  
  public SequenceFileWithRabbitMQSink(String[] args) {
	super(args);
  }

  @Override
  public void close() throws IOException {
	  //sends each batch (different hosts) to SendRabbit
	  if( !TransactionManager.sendRabbit.isAlive() )
		  TransactionManager.restartRabbit();
	  for(RabbitMessageBatch batch : batches ) {
		  LOG.info("RABBITMQ: ::::::::::::::::::::::::::Putting batch of host: "+batch.getHost() + " , SIZE: "+batch.getCount());
		  TransactionManager.sendRabbit.putBatch(batch);
	  }
	  super.close();
  }
  
  @Override
  public void open() throws IOException {
	  super.open();
  }
  
  private AtomicInteger atomicCount = new AtomicInteger();
  @Override
  public void append(Event event) throws IOException {
    super.append(event);
    //adding to Rabbit message,adds to the RabbitMessageBatch object that has the same host, if no matches creates one with that host and adds.
    boolean done = false;
    LOG.info("RABBITMQ: Starting append for, Count: "+atomicCount.addAndGet(1));
    for(RabbitMessageBatch rmb : batches) {
//    	done = rmb.checkHostAndAddMessage(event.toString(), ((AccessLogWritable)event).getHost());
    	done = rmb.checkHostAndAddMessage(""+atomicCount.get()+" , "+((AccessLogWritable)event).getHost(), ((AccessLogWritable)event).getHost());
    	if(done)
    		break;
    }
    if(!done) {
    	RabbitMessageBatch newBatch = TransactionManager.sendRabbit.new RabbitMessageBatch(((AccessLogWritable)event).getHost());
//    	done = newBatch.checkHostAndAddMessage(event.toString(), ((AccessLogWritable)event).getHost());
    	done = newBatch.checkHostAndAddMessage(""+atomicCount.get()+" , "+((AccessLogWritable)event).getHost(), ((AccessLogWritable)event).getHost());
    	batches.add(newBatch);
    }
    if( !done )
    	LOG.error("RABBITMQ: ERROR: Message not added to batch!\t"+atomicCount.get());
  }

}
