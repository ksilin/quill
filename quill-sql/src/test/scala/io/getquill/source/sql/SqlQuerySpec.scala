
package io.getquill.source.sql

import io.getquill._
import io.getquill.naming.Literal
import io.getquill.norm.QueryGenerator
import io.getquill.norm.Normalize
import io.getquill.source.sql.idiom.SqlIdiom
import io.getquill.util.Show._

class SqlQuerySpec extends Spec {

  val idiom = new SqlIdiom {
    def prepare(sql: String) = sql
  }

  import idiom._

  implicit val naming = new Literal {}

  "transforms the ast into a flatten sql-like structure" - {

    "generated query" - {
      val gen = new QueryGenerator(1)
      for (i <- (3 to 15)) {
        for (j <- (0 until 30)) {
          val query = Normalize(gen(i))
          s"$i levels ($j) - $query" in {
            VerifySqlQuery(SqlQuery(query)) match {
              case None        =>
              case Some(error) => println(error)
            }
          }
        }
      }
    }

    "join query" in {
      val q = quote {
        for {
          a <- qr1
          b <- qr2 if (a.s != null && b.i > a.i)
        } yield {
          (a.i, b.i)
        }
      }
      SqlQuery(q.ast).show mustEqual
        "SELECT a.i, b.i FROM TestEntity a, TestEntity2 b WHERE (a.s IS NOT NULL) AND (b.i > a.i)"
    }
    "nested infix query" - {
      "as source" in {
        val q = quote {
          infix"SELECT * FROM TestEntity".as[Query[TestEntity]].filter(t => t.i == 1)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM (SELECT * FROM TestEntity) t WHERE t.i = 1"
      }
      "fails if used as the flatMap body" in {
        val q = quote {
          qr1.flatMap(a => infix"SELECT * FROM TestEntity2 t where t.s = ${a.s}".as[Query[TestEntity2]])
        }
        val e = intercept[IllegalStateException] {
          SqlQuery(q.ast)
        }
      }
    }
    "sorted query" - {
      "with map" in {
        val q = quote {
          qr1.sortBy(t => t.s).map(t => t.s)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.s FROM TestEntity t ORDER BY t.s"
      }
      "with filter" in {
        val q = quote {
          qr1.filter(t => t.s == "s").sortBy(t => t.s).map(t => (t.i))
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.i FROM TestEntity t WHERE t.s = 's' ORDER BY t.s"
      }
      "with reverse" in {
        val q = quote {
          qr1.sortBy(t => t.s).reverse.map(t => t.s)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.s FROM TestEntity t ORDER BY t.s DESC"
      }
      "with outer filter" in {
        val q = quote {
          qr1.sortBy(t => t.s).filter(t => t.s == "s").map(t => t.s)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.s FROM TestEntity t WHERE t.s = 's' ORDER BY t.s"
      }
      "with flatMap" in {
        val q = quote {
          qr1.sortBy(t => t.s).flatMap(t => qr2.map(t => t.s))
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.s FROM (SELECT * FROM TestEntity t ORDER BY t.s) t, TestEntity2 t"
      }
      "tuple criteria" in {
        val q = quote {
          qr1.sortBy(t => (t.s, t.i)).map(t => t.s)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.s FROM TestEntity t ORDER BY t.s, t.i"
      }
      "multiple sortBy" in {
        val q = quote {
          qr1.sortBy(t => (t.s, t.i)).reverse.sortBy(t => t.l).map(t => t.s)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.s FROM TestEntity t ORDER BY t.s DESC, t.i DESC, t.l"
      }
      "fails if the sortBy criteria is malformed" in {
        val q = quote {
          qr1.sortBy(t => t)(null)
        }
        val e = intercept[IllegalStateException] {
          SqlQuery(q.ast)
        }
      }
    }
    "grouped query" - {
      "simple" in {
        val q = quote {
          qr1.groupBy(t => t.i).map(t => t._1)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.i _1 FROM TestEntity t GROUP BY t.i"
      }
      "nested" in {
        val q = quote {
          qr1.groupBy(t => t.i).map(t => t._1).flatMap(t => qr2)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM (SELECT t.i _1 FROM TestEntity t GROUP BY t.i) t, TestEntity2 x"
      }
      "without map" in {
        val q = quote {
          qr1.groupBy(t => t.i)
        }
        val e = intercept[IllegalStateException] {
          SqlQuery(q.ast)
        }
      }
      "tuple" in {
        val q = quote {
          qr1.groupBy(t => (t.i, t.l)).map(t => t._1)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.i _1, t.l _2 FROM TestEntity t GROUP BY t.i, t.l"
      }
      "invalid groupby criteria" - {
        val q = quote {
          qr1.groupBy(t => t).map(t => t)
        }
        val e = intercept[IllegalStateException] {
          SqlQuery(q.ast)
        }
      }
    }
    "aggregated query" in {
      val q = quote {
        qr1.map(t => t.i).max
      }
      SqlQuery(q.ast).show mustEqual
        "SELECT MAX(t.i) FROM TestEntity t"
    }
    "limited query" - {
      "simple" in {
        val q = quote {
          qr1.take(10)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM TestEntity x LIMIT 10"
      }
      "nested" in {
        val q = quote {
          qr1.take(10).flatMap(a => qr2)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM (SELECT * FROM TestEntity x LIMIT 10) a, TestEntity2 x"
      }
      "with map" in {
        val q = quote {
          qr1.take(10).map(t => t.s)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.s FROM TestEntity t LIMIT 10"
      }
      "multiple limits" in {
        val q = quote {
          qr1.take(1).take(10)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM (SELECT * FROM TestEntity x LIMIT 1) x LIMIT 10"
      }
    }
    "offset query" - {
      "simple" in {
        val q = quote {
          qr1.drop(10)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM TestEntity x OFFSET 10"
      }
      "nested" in {
        val q = quote {
          qr1.drop(10).flatMap(a => qr2)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM (SELECT * FROM TestEntity x OFFSET 10) a, TestEntity2 x"
      }
      "with map" in {
        val q = quote {
          qr1.drop(10).map(t => t.s)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT t.s FROM TestEntity t OFFSET 10"
      }
      "multiple offsets" in {
        val q = quote {
          qr1.drop(1).drop(10)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM (SELECT * FROM TestEntity x OFFSET 1) x OFFSET 10"
      }
    }
    "limited and offset query" - {
      "simple" in {
        val q = quote {
          qr1.drop(10).take(11)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM TestEntity x LIMIT 11 OFFSET 10"
      }
      "nested" in {
        val q = quote {
          qr1.drop(10).take(11).flatMap(a => qr2)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM (SELECT * FROM TestEntity x LIMIT 11 OFFSET 10) a, TestEntity2 x"
      }
      "multiple" in {
        val q = quote {
          qr1.drop(1).take(2).drop(3).take(4)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM (SELECT * FROM TestEntity x LIMIT 2 OFFSET 1) x LIMIT 4 OFFSET 3"
      }
      "take.drop" in {
        val q = quote {
          qr1.take(1).drop(2)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM (SELECT * FROM TestEntity x LIMIT 1) x OFFSET 2"
      }
    }
    "set operation query" - {
      "union" in {
        val q = quote {
          qr1.union(qr1)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM TestEntity x UNION SELECT * FROM TestEntity x"
      }
      "unionAll" in {
        val q = quote {
          qr1.unionAll(qr1)
        }
        SqlQuery(q.ast).show mustEqual
          "SELECT * FROM TestEntity x UNION ALL SELECT * FROM TestEntity x"
      }
    }
  }

  "fails if the query is not normalized" in {
    val q = quote {
      ((s: String) => qr1.filter(_.s == s))("s")
    }
    val e = intercept[IllegalStateException] {
      val a = SqlQuery(q.ast)
    }
  }
}
