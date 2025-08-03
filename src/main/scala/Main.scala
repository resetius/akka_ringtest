import akka.actor._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration

// Message to pass around the ring
case object Next

class RingActor(idx: Int, N: Int, M: Int, ring: ListBuffer[ActorRef]) extends Actor {
  private var remain = M
  private var lastPercent = -1
  private var startTime = 0L
  private var timerStarted = false

  def receive: Receive = {
    case Next =>
      // Record start time on first message by actor 0
      if (idx == 0 && !timerStarted) {
        startTime = System.nanoTime()
        timerStarted = true
      }
      // If actor 0 has completed all rounds, stop handling further
      if (!(idx == 0 && remain == 0)) {
        // Forward the message to the next actor
        ring((idx + 1) % N) ! Next

        if (idx == 0) {
          if (sender() != context.system.deadLetters) {
            remain -= 1
          }
          printProgress()
          if (remain == 0) {
            shutdownRing()
          }
        }
      }
  }

  private def printProgress(): Unit = {
    val processed = M - remain
    val percent = (processed * 100) / M
    if (percent != lastPercent) {
      lastPercent = percent
      val barWidth = 50
      val pos = (percent * barWidth) / 100
      val bar = (0 until barWidth).map { i =>
        if (i < pos) "="
        else if (i == pos) ">"
        else " "
      }.mkString
      Console.err.print(s"\r[$bar] $percent%")
      Console.err.flush()
    }
  }

  private def shutdownRing(): Unit = {
    val now = System.nanoTime()
    val secs = (now - startTime) / 1e9
    println(f"\nRing throughput: ${(N.toLong * M) / secs}%.2f msg/s")
    // Poison all actors to stop them
    ring.foreach(_ ! PoisonPill)
    // Terminate the actor system
    context.system.terminate()
  }
}

object Main extends App {
  var N = 2        // Default number of actors
  var M = 100      // Default number of messages
  var mode = "throughput"
  var inflight = 1

  // Parse command-line arguments
  args.sliding(2, 2).toList.collect {
    case Array("--actors", actors)   => N = actors.toInt
    case Array("--messages", msgs)   => M = msgs.toInt
    case Array("--mode", m)          => mode = m
    case Array("--batch", batch)     => inflight = batch.toInt
  }

  if (mode == "throughput") {
    // Initialize the actor system
    val system = ActorSystem("RingSystem")
    val ringRefs = ListBuffer.empty[ActorRef]

    // Create actors and build the ring
    for (i <- 0 until N) {
      val actor = system.actorOf(Props(new RingActor(i, N, M, ringRefs)), s"actor$i")
      ringRefs += actor
    }

    // Kick off the ring
    for (i <- 0 until inflight) {
      ringRefs(0) ! Next
    }

    // Wait for termination after shutdown
    Await.ready(system.whenTerminated, Duration.Inf)
  } else {
    System.err.println(s"Unsupported mode: $mode")
    System.exit(1)
  }
}

