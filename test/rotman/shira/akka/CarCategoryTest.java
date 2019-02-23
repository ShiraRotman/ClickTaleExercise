package rotman.shira.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

import java.time.Duration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CarCategoryTest
{
    private static ActorSystem system;
    private static TestKit actorProbe;

    @BeforeClass public static void setup()
    {
        system=ActorSystem.create();
        actorProbe=new TestKit(system);
    }

    @AfterClass public static void teardown()
    { TestKit.shutdownActorSystem(system); system=null; }

    @Test public void testAckMessage()
    {
        ActorRef testedActor=actorProbe.childActorOf(CarCategoryActor.props("Mazda","logging-dispatcher"));
        testedActor.tell(Boolean.TRUE,actorProbe.getRef()); //Send a verification message
        actorProbe.expectMsg(Duration.ofSeconds(5),Boolean.TRUE);
    }

    @Test public void testMsgLogging()
    {
        StringBuilder destination=new StringBuilder();
        ActorRef testedActor=actorProbe.childActorOf(CarCategoryActor.props("Legacy",
                "logging-dispatcher",destination));
        testedActor.tell("Hello",actorProbe.getRef());
        testedActor.tell("Test",actorProbe.getRef());
        for (int index=0;index<=2;index++)
            testedActor.tell("Bla",actorProbe.getRef());
        actorProbe.awaitCond(Duration.ofSeconds(5),()->destination.toString().
                equals("HelloTestBlaBlaBla"));
        destination.setLength(0);
        testedActor.tell("Hello",actorProbe.getRef());
        testedActor.tell("Timer",actorProbe.getRef());
        actorProbe.awaitCond(Duration.ofSeconds(70),()->destination.toString().
                equals("HelloTimer"));
    }
}
