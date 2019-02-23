package rotman.shira.akka;

import akka.actor.*;
import akka.testkit.javadsl.TestKit;

import java.time.Duration;
import java.util.function.BiFunction;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CarWatcherTest
{
    private static ActorSystem system;

    @BeforeClass public static void setup()
    { system=ActorSystem.create(); }

    @AfterClass public static void teardown()
    { TestKit.shutdownActorSystem(system); system=null; }

    @Test public void testCarWatcher()
    {
        TestKit category1Probe=new TestKit(system);
        TestKit category2Probe=new TestKit(system);
        category1Probe.watch(category2Probe.getRef());
        String[] categoryNames=new String[] { "Mazda","Legacy" };
        BiFunction<ActorRefFactory,String,ActorRef> creator=(factory,category)->
        {
            if (category.equals(categoryNames[0])) return category1Probe.getRef();
            else if (category.equals(categoryNames[1])) return category2Probe.getRef();
            else return null;
        };

        ActorRef testedActor=system.actorOf(CarWatcherActor.props(categoryNames,creator));
        category1Probe.within(Duration.ofSeconds(12),()->
        {
            category1Probe.expectMsg(Boolean.TRUE);
            category2Probe.expectMsg(Boolean.TRUE);
            return null;
        });
        testedActor.tell(Boolean.TRUE,category1Probe.getRef());
        category1Probe.expectTerminated(Duration.ofSeconds(7),category2Probe.getRef());
    }
}
