package rotman.shira.akka;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;
import akka.dispatch.Futures;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import scala.concurrent.ExecutionContext;

public class CarCategoryActor extends AbstractActorWithTimers
{
    public static final String LINE_SEPARATOR=System.getProperty("line.separator");
    public static final int MAX_MESSAGES=5;

    public static final int TIMER_FREQ_SEC=60;
    private static final String TIMER_KEY="flush";
    private static final class Flush { }

    private final ExecutionContext loggingDispatcher;
    private final Flush flushObj=new Flush();
    private final String[] messages=new String[MAX_MESSAGES];
    private String categoryName; int nextIndex=0;

    public CarCategoryActor(String categoryName,String dispatcherID)
    {
        if ((categoryName==null)||(categoryName.trim().equals("")))
            throw new NullPointerException("The category name can't be null or empty!");
        if (dispatcherID==null)
            throw new NullPointerException("The logging dispatcher ID can't be null!");
        this.categoryName=categoryName;
        loggingDispatcher=getContext().getSystem().dispatchers().lookup(dispatcherID);
        if (loggingDispatcher==null)
            throw new IllegalArgumentException("Dispatcher doesn't exist for ID: " + dispatcherID);
        getTimers().startPeriodicTimer(TIMER_KEY,flushObj,Duration.ofSeconds(TIMER_FREQ_SEC));
    }

    public static Props props(String categoryName,String dispatcherID)
    { return Props.create(CarCategoryActor.class,()->new CarCategoryActor(categoryName,dispatcherID)); }

    @Override public Receive createReceive()
    {
        return receiveBuilder().match(String.class,this::handleMessage).match(Flush.class,f->performFlush()).
                match(Boolean.class,a->getSender().tell(true,getSelf())).build();
    }

    private void handleMessage(String message)
    {
        messages[nextIndex++]=message;
        if (nextIndex==messages.length) performFlush();
    }

    private void performFlush()
    {
        if (nextIndex>0)
        {
            final String[] tempMessages=Arrays.copyOf(messages,nextIndex);
            nextIndex=0;
            Futures.future(()->{ performLogging(tempMessages); return true; },loggingDispatcher);
        }
    }

    private void performLogging(String[] messages) throws IOException
    {
        //String categoryName=getSelf().path().name().intern();
        synchronized (categoryName.intern())
        {
            FileWriter destination=new FileWriter(categoryName + ".txt",true);
            for (String message : messages)
            { destination.write(message); destination.write(LINE_SEPARATOR); }
            destination.close();
        }
    }
}
