import akka.actor._
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

// Сообщение для пинга
case object PingMessage

// Конфигурация узла
case class NodeConfig(host: String, port: Int, nodeId: Int)

class PingActor(isFirstNode: Boolean,
                 totalMessages: Int,
                 nextConfig: NodeConfig,
                 allConfigs: scala.collection.mutable.ListBuffer[NodeConfig])
    extends Actor {
  private var remaining = totalMessages
  private var lastPercent = -1
  private var startTime = 0L
  private val systemName = "DistributedSystem"

  override def postStop(): Unit = {
    // На не‑первом узле после остановки акторсистемы
    if (!isFirstNode) context.system.terminate()
  }

  def receive: Receive = {
    case PingMessage =>
      if (isFirstNode && remaining == totalMessages) {
        println("Starting pinging...")
        startTime = System.nanoTime()
      }

      // Если первый узел и осталось 0 сообщений — ничего не делаем
      if (!(isFirstNode && remaining == 0)) {
        // Отправляем следующему в кольце
        val remotePath =
          s"akka://$systemName@${nextConfig.host}:${nextConfig.port}/user/pingActor"
        context.actorSelection(remotePath) ! PingMessage

        if (isFirstNode) {
          remaining -= 1
          printProgress()
          if (remaining == 0) {
            val secs = (System.nanoTime() - startTime) / 1e9
            println(f"\nPing throughput: ${totalMessages / secs}%.2f msg/s")
            // Завершаем всех узлов
            allConfigs.foreach { cfg =>
              val p = s"akka://$systemName@${cfg.host}:${cfg.port}/user/pingActor"
              context.actorSelection(p) ! PoisonPill
            }
            // Закрываем локальную систему
            context.system.terminate()
          }
        }
      }
  }

  private def printProgress(): Unit = {
    val done = totalMessages - remaining
    val pct = (done * 100) / totalMessages
    if (pct != lastPercent) {
      lastPercent = pct
      val barW = 50
      val pos  = (pct * barW) / 100
      val bar = (0 until barW).map { i =>
        if (i < pos) "="
        else if (i == pos) ">"
        else " "
      }.mkString
      Console.err.print(s"\r[$bar] $pct%")
      Console.err.flush()
    }
  }
}

object DistributedMain extends App {
  var myNodeId    = 1
  var delayMs     = 5000
  val totalMessages = 100000
  val nodeConfigs = ListBuffer.empty[NodeConfig]

  // Парсинг аргументов
  args.sliding(2, 2).toList.foreach {
    case Array("--node", hp) =>
      hp.split(":") match {
        case Array(h, p, id) => nodeConfigs += NodeConfig(h, p.toInt, id.toInt)
        case _ =>
          System.err.println("Invalid node format. Use host:port:nodeId")
          System.exit(1)
      }
    case Array("--node-id", id) => myNodeId = id.toInt
    case Array("--delay", d)   => delayMs = d.toInt
    case _ =>
  }

  if (!nodeConfigs.exists(_.nodeId == myNodeId)) {
    System.err.println(s"Node with id: $myNodeId is not registered.")
    System.exit(1)
  }

  val sorted = nodeConfigs.sortBy(_.nodeId)
  val idx    = sorted.indexWhere(_.nodeId == myNodeId)
  val next   = sorted((idx + 1) % sorted.size)
  val isFirst = sorted.head.nodeId == myNodeId
  val myCfg  = sorted(idx)

  // Конфиг для Akka-remote
  val configStr = s"""
akka {
  actor {
    provider = "remote"
    warn-about-java-serializer-usage = on
    allow-java-serialization = off
    serializers {
      kryo = "io.altoo.akka.serialization.kryo.KryoSerializer"
    }
    serialization-bindings {
      "PingMessage$$" = kryo
    }
  }
  remote {
    artery {
      transport = tcp
      canonical.hostname = "${myCfg.host}"
      canonical.port = ${myCfg.port}
      bind.hostname = "${myCfg.host}"
      bind.port = ${myCfg.port}
      advanced.stream-idle-timeout = infinite
    }
  }
}
""".stripMargin
  val config = ConfigFactory.parseString(configStr).withFallback(ConfigFactory.load())

  val system     = ActorSystem("DistributedSystem", config)
  val pingActor  = system.actorOf(
    Props(new PingActor(isFirst, totalMessages, next, sorted)),
    "pingActor"
  )

  import system.dispatcher
  if (isFirst) {
    system.scheduler.scheduleOnce(delayMs.milliseconds) {
      val path = s"akka://DistributedSystem@${myCfg.host}:${myCfg.port}/user/pingActor"
      System.err.println(s"Sending first ping to $path")
      system.actorSelection(path) ! PingMessage
    }
  }

  Await.ready(system.whenTerminated, Duration.Inf)
}

