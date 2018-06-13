package persistence.slick

import akka.actor.ActorSystem
import akka.actor.Status.{Failure, Success}
import akka.stream.ActorMaterializer
import persistence.slick.schema.{ChessPiece, ChessPieceTable, Player, PlayerTable}
import slick.jdbc.MySQLProfile.api._
import akka.stream.alpakka.slick.scaladsl._
import com.typesafe.config.ConfigFactory
import controller.Controller
import model._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
case class SlickController(controller: Controller) {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  lazy val chessPiece = TableQuery[ChessPieceTable]
  lazy val player = TableQuery[PlayerTable]
  implicit val session = SlickSession.forConfig("chess")

  def load(): Unit ={
    val erg = Await.ready(session.db.run(chessPiece.result), Duration.Inf).value
    println("BEFORE")
    println(controller.gamefield)
    println("AFTER")
    val s: Seq[ChessPieceTable#TableElementType] = erg match {
      case Some(k) => k.get
    }

    var newField = new GameField()
    for(e <- s){
      val x = e.Position.charAt(0).asDigit
      val y = e.Position.charAt(1).asDigit
      val t: Figure = e.Designator match {
        case "K" => new König
        case "D" => new Dame
        case "L" => new Läufer
        case "T" => new Turm
        case "B" => new Bauer((x, y), "UP")
      }
      for(i <- 0 until 8){
        for(j <- 0 until 8){
          if(controller.getFigure((i,j)) == t){
            controller.gamefield.update((i,j), (x,y))
          }
        }
      }
    }
    controller.gamefield = newField
    println(controller.gamefield)

  }

  def save(): Unit ={
    val p1 = insertPlayer(Player(0, controller.playerA.toString))
    val p2 = insertPlayer(Player(0, controller.playerB.toString))

    for (i <- 0 until 8){
      for(j <- 0 until 8){
        val fig = controller.gamefield.get(i, j)
        if(controller.playerA.hasFigure(fig)){
          insertChessPiece(ChessPiece(
            0, fig.toString(), "" + i + "" + j, p1
          ))
        }

        if(controller.playerB.hasFigure(fig)){
          insertChessPiece(ChessPiece(
            0, fig.toString(), "" + i + "" + j, p2
          ))
        }
      }

    }

  def insertChessPiece(p: ChessPiece): Unit ={
    Await.ready(
      session.db.run(chessPiece += p),
      Duration.Inf)
    }
  }
  def insertPlayer(p: Player): Int ={
    Await.ready(
      session.db.run(player returning player.map(_.PlayerID) += p),
      Duration.Inf
    ).value.get.get
  }
}
