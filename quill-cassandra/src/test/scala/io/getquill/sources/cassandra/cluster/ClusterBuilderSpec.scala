package io.getquill.sources.cassandra.cluster

import com.datastax.driver.core.Cluster.Builder
import com.typesafe.config.ConfigFactory
import io.getquill.Spec

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class ClusterBuilderSpec extends Spec {

  "creates Builder" - {
    "single host" in {

      val cfgString = "contactPoint = 127.0.0.1"
      val cfg = ConfigFactory.parseString(cfgString)
      val clusterBuilder: Builder = ClusterBuilder(cfg)
      println(clusterBuilder.getContactPoints)
    }

    "single host as array" in {

      val cfgString = """contactPoints = ["127.0.0.1"] """
      val cfg = ConfigFactory.parseString(cfgString)
      val clusterBuilder: Builder = ClusterBuilder(cfg)
      println(clusterBuilder.getContactPoints)
    }

    "a string list can be extracted from a single string" in {
      val cfgString = """contactPoints = "127.0.0.1" """
      val cfg = ConfigFactory.parseString(cfgString)
      val l = cfg.getStringList("contactPoints")
      println(l)
    }
    "a string cannot be extracted from a string list" in {
      val cfgString = """contactPoints = ["127.0.0.1"] """
      val cfg = ConfigFactory.parseString(cfgString)
      val l = cfg.getString("contactPoints")
      println(l)
    }

    // since we dont know, try extracting both
    "we dont know, so extract both" in {
      val cfgString = """contactPoints = ["127.0.0.1"] """
      val cfg = ConfigFactory.parseString(cfgString)
      val key: String = "contactPoints"
      val l: Try[mutable.Buffer[String]] = Try { mutable.Buffer(cfg.getString(key)) } recover { case t: Throwable => cfg.getStringList(key).asScala}
      println(l.get)
    }

    "can parse string arrays" in {
      val cfgString = """contactPoints = ["127.0.0.1"] """
      val cfg = ConfigFactory.parseString(cfgString)
      val extracted = ClusterBuilder.param("contactPoint", classOf[List[String]], cfg)
      println(extracted)

    }

  }
}
