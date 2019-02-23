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

    @BeforeClass public static void setup()
    { system=ActorSystem.create(); }

    @AfterClass public static void teardown()
    { TestKit.shutdownActorSystem(system); system=null; }

    @Test public void testCarCategory()
    {
        TestKit actorProbe=new TestKit(system);
        ActorRef testedActor=actorProbe.childActorOf(CarCategoryActor.props("Mazda","logging-dispatcher"));
        testedActor.tell(Boolean.TRUE,actorProbe.getRef()); //Send a verification message
        actorProbe.expectMsg(Duration.ofSeconds(5),Boolean.TRUE);
    }
}
