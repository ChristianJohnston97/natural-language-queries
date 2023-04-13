package cdp.domain

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import doobie.implicits._
import doobie.util.stream.repeatEvalChunks
import doobie.util.transactor.Transactor.Aux
import doobie.{PreparedStatementIO, Transactor, _}
import fs2.Stream
import fs2.Stream.{bracket, eval}
import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.settings.CreateCompletionSettings
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import java.sql.{PreparedStatement, ResultSet}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

/**
 * Scala client => https://github.com/cequence-io/openai-scala-client
 * Dataset => https://www.kaggle.com/datasets/vainero/restaurants-customers-orders-dataset?resource=download&select=cities.csv
 */
object Main extends IOApp {


  val query = "which cities and dates do the members make the most orders. Show top 10"

  /*
    Other Questions

    You can use this data to discover which City and period of the day the members do the most orders?
    What is the ratio of meal types in restaurants in each city?
    What is the ratio of the orders in cities with the most Italian restaurants?
    Which cities have the most vegan meals?
    What is the difference in the range price of the hot or cold meal?
    What is the correlation between the sex of members and serve_type?
   */


  //----------------------------------------------------------------------------

  type Row = Map[String, Any]

  @SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.While", "org.wartremover.warts.NonUnitStatements"))
  def getNextChunkGeneric(chunkSize: Int): ResultSetIO[Seq[Row]] =
    FRS.raw { rs =>
      val md = rs.getMetaData
      val ks = (1 to md.getColumnCount).map(md.getColumnLabel).toList
      var n = chunkSize
      val b = Vector.newBuilder[Row]
      while (n > 0 && rs.next) {
        val mb = Map.newBuilder[String, Any]
        ks.foreach(k => mb += (k -> rs.getObject(k)))
        b += mb.result()
        n -= 1
      }
      b.result()
    }


  def liftProcessGeneric(chunkSize: Int, create: ConnectionIO[PreparedStatement],
                          prep:   PreparedStatementIO[Unit],
                          exec:   PreparedStatementIO[ResultSet]): Stream[ConnectionIO, Row] = {

    def prepared(ps: PreparedStatement): Stream[ConnectionIO, PreparedStatement] =
      eval[ConnectionIO, PreparedStatement] {
        val fs = FPS.setFetchSize(chunkSize)
        FC.embed(ps, fs *> prep).map(_ => ps)
      }

    def unrolled(rs: ResultSet): Stream[ConnectionIO, Row] =
      repeatEvalChunks[ConnectionIO, Row](FC.embed(rs, getNextChunkGeneric(chunkSize)))

    val preparedStatement: Stream[ConnectionIO, PreparedStatement] =
      bracket(create)(FC.embed(_, FPS.close)).flatMap(prepared)

    def results(ps: PreparedStatement): Stream[ConnectionIO, Row] =
      bracket[ConnectionIO, ResultSet](FC.embed(ps, exec))(FC.embed(_, FRS.close)).flatMap(unrolled)

    preparedStatement.flatMap(results)

  }

  def processGeneric(sql: String, prep: PreparedStatementIO[Unit], chunkSize: Int): Stream[ConnectionIO, Row] =
    liftProcessGeneric(chunkSize, FC.prepareStatement(sql), prep, FPS.executeQuery)

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val materializer: Materializer = Materializer(ActorSystem())
  val service: OpenAIService = OpenAIServiceFactory(apiKey = "<API_KEY>")
  val text: String = s"""### Postgres SQL tables, with their properties:
               |#
               |#  "meal_type" (id INTEGER  NOT NULL PRIMARY KEY,meal_type VARCHAR(7) NOT NULL);
               |#  "meal" (id INTEGER  NOT NULL PRIMARY KEY, restaurant_id INTEGER  NOT NULL, meal_type_id  INTEGER  NOT NULL REFERENCES meal_type(id), # price NUMERIC NOT NULL);
               |#  "city" (id INTEGER  NOT NULL PRIMARY KEY, city_name VARCHAR(28) NOT NULL);
               |#  "member" (id INTEGER  NOT NULL PRIMARY KEY, first_name VARCHAR(10) NOT NULL, surname VARCHAR(11) NOT NULL, sex VARCHAR(1) NOT NULL, email VARCHAR(28) NOT NULL, city_id INTEGER NOT NULL references city(id), monthly_budget NUMERIC(6,1) NOT NULL);
               |#  "monthly_member_totals" (member_id INTEGER NOT NULL references member(id), year INTEGER  NOT NULL, month INTEGER  NOT NULL, order_count INTEGER  NOT NULL, meals_count INTEGER  NOT NULL, monthly_budget VARCHAR(18) NOT NULL, total_expense NUMERIC(6,1) NOT NULL, balance VARCHAR(19) NOT NULL, commission VARCHAR(18) NOT NULL, PRIMARY KEY (member_id, year, month));
               |#  "restaurant_type" (id INTEGER  NOT NULL PRIMARY KEY, restaurant_type VARCHAR(9) NOT NULL);
               |#  "restaurant" (id INTEGER  NOT NULL PRIMARY KEY, restaurant_name VARCHAR(13) NOT NULL, restaurant_type_id INTEGER  NOT NULL references restaurant_type(id), income_percentage  NUMERIC(5,3) NOT NULL, city_id INTEGER  NOT NULL references city(id));
               |#  "order" (id INTEGER  NOT NULL PRIMARY KEY, order_date DATE  NOT NULL, order_hour VARCHAR(16) NOT NULL, member_id INTEGER  NOT NULL references member(id), restaurant_id INTEGER  NOT NULL references restaurant(id), total_order VARCHAR(18) NOT NULL);
               |#  "order_meals" (id INTEGER  NOT NULL PRIMARY KEY, order_id INTEGER  NOT NULL references "order"(id), meal_id  INTEGER  NOT NULL references meal(id));
               |#
               |### A query to show $query
             """.stripMargin
  val settings: CreateCompletionSettings = CreateCompletionSettings(model = ModelId.text_davinci_003, temperature = Some(0), max_tokens = Some(150), top_p = Some(1.0), frequency_penalty = Some(0.0), presence_penalty = Some(0.0), stop= Seq("#", ";"))
  val xa: Aux[IO, Unit] = Transactor.fromDriverManager[IO]("org.postgresql.Driver", "jdbc:postgresql:database", "postgres", "")

  override def run(args: List[String]): IO[ExitCode] = {
    (for {
      completion <- IO.fromFuture(IO(service.createCompletion(text, settings = settings)))
      query      = completion.choices.head.text
      //_          <- IO.delay(println("Query" + query + "\n"))
      rows       <- processGeneric(query, ().pure[PreparedStatementIO], 100).transact(xa).compile.toList
      _          <- IO(Console.println(prettyPrint(rows)))
    } yield ()).as(ExitCode.Success)
  }

  def prettyPrint(results: List[Map[String, Any]]): String = {
    val headers = results.head.keys.toList
    val inFormat = headers +: results.map(_.values.toList.map(_.toString))

    def formatTable(table: List[List[String]]): String = {
      if (table.isEmpty) ""
      else {
        val colWidths = table.transpose.map(_.map(cell => if (cell == null) 0 else cell.length).max + 2)
        val rows = table.map(_.zip(colWidths).map { case (item, size) => (" %-" + (size - 1) + "s").format(item) }.mkString("|", "|", "|"))
        val separator = colWidths.map("-" * _).mkString("+", "+", "+")
        (separator +: rows.head +: separator +: rows.tail :+ separator).mkString("\n")
      }
    }
    formatTable(inFormat)
  }
}

