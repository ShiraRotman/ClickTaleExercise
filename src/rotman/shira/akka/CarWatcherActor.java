package rotman.shira.akka;

import akka.actor.AbstractFSM;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.SupervisorStrategy;
import akka.japi.pf.DeciderBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;

enum WatcherState { Idle,WaitAck }
final class WatcherData
{
    private HashSet<ActorRef> childActors;

    public WatcherData(ActorRef[] actors)
    { for (ActorRef actor : actors) childActors.add(actor); }

    public WatcherData receivedAck(ActorRef actor)
    { childActors.remove(actor); return this; }

    public boolean isEmpty() { return childActors.isEmpty(); }
    public HashSet<ActorRef> getActors() { return childActors; }
}

public class CarWatcherActor extends AbstractFSM<WatcherState,WatcherData>
{
    public static final int VERIFY_FREQ_SEC=10;
    public static final int MAX_WAIT_SEC=5;
    private static final String WAIT_TIMER_KEY="wait_ack";

    private final SupervisorStrategy strategy;
    private final ActorRef[] categoryActors;
    private final String[] categoryNames;

    public CarWatcherActor(String... categoryNames)
    {
        if ((categoryNames==null)||(categoryNames.length==0))
            throw new NullPointerException("The category names can't be null");
        for (String categoryName : categoryNames)
        {
            if ((categoryName==null)||(categoryName.trim().equals("")))
                throw new IllegalArgumentException("Invalid category name: " + categoryName);
        }

        strategy=new OneForOneStrategy(DeciderBuilder.match(IOException.class,e->SupervisorStrategy.restart()).
                matchAny(e->SupervisorStrategy.escalate()).build());
        ActorContext context=getContext();
        categoryActors=new ActorRef[categoryNames.length];
        for (int index=0;index<categoryNames.length;index++)
        {
            categoryActors[index]=context.actorOf(CarCategoryActor.props(categoryNames[index],
                    "logging-dispatcher").withDispatcher("category-dispatcher"));
        }
        this.categoryNames=categoryNames;
        initializeStateMachine();
    }

    public static Props props(String... categoryNames)
    { return Props.create(CarWatcherActor.class,()->new CarWatcherActor(categoryNames)); }
    @Override public SupervisorStrategy supervisorStrategy() { return strategy; }

    private void initializeStateMachine()
    {
        startWith(WatcherState.Idle,null);
        when(WatcherState.Idle,Duration.ofSeconds(VERIFY_FREQ_SEC),matchEvent(StateTimeout$.class,
                (e,d)->goTo(WatcherState.WaitAck).using(new WatcherData(categoryActors))));
        onTransition(matchState(WatcherState.Idle,WatcherState.WaitAck,this::verifyChildren));
        when(WatcherState.WaitAck,matchEvent(Boolean.class,(e,d)->handleAck(d)));
        when(WatcherState.WaitAck,matchEvent(ReceiveTimeout.class,(e,d)->handleTimeout(d)));
    }

    private void verifyChildren()
    {
        for (ActorRef childActor : categoryActors)
            childActor.tell(Boolean.TRUE,getSelf());
        setTimer(WAIT_TIMER_KEY,ReceiveTimeout.class,Duration.ofSeconds(MAX_WAIT_SEC));
    }

    private State<WatcherState,WatcherData> handleAck(WatcherData watcherData)
    {
        watcherData.receivedAck(getSender());
        if (watcherData.isEmpty())
        {
            cancelTimer(WAIT_TIMER_KEY);
            return goTo(WatcherState.Idle).using(null);
        }
        else return stay();
    }

    private State<WatcherState,WatcherData> handleTimeout(WatcherData watcherData)
    {
        HashSet<ActorRef> unresponsiveActors=watcherData.getActors();
        for (int index=0;index<categoryActors.length;index++)
        {
            if (unresponsiveActors.contains(categoryActors[index]))
            {
                categoryActors[index].tell(PoisonPill.getInstance(),getSelf());
                categoryActors[index]=getContext().actorOf(CarCategoryActor.props(categoryNames[index],
                        "logging-dispatcher").withDispatcher("category-dispatcher"));
            }
        }
        return goTo(WatcherState.Idle).using(null);
    }
}
