package com.outr.arango

import com.outr.arango.api.model.GetAPIDatabaseNew
import com.outr.arango.api.{APIDatabase, APIDatabaseDatabaseName}
import com.outr.arango.model.ArangoResponse
import io.youi.client.HttpClient
import io.youi.net.Path
import profig.JsonUtil

import scala.concurrent.{ExecutionContext, Future}

class ArangoDatabase(client: HttpClient, name: String) {
  def create()(implicit ec: ExecutionContext): Future[ArangoResponse[Boolean]] = {
    APIDatabase.post(client, GetAPIDatabaseNew(
      name = name
    )).map(JsonUtil.fromJson[ArangoResponse[Boolean]](_))
    // TODO: Support setting user
  }

  def drop()(implicit ec: ExecutionContext): Future[ArangoResponse[Boolean]] = {
    APIDatabaseDatabaseName.delete(client, name).map(JsonUtil.fromJson[ArangoResponse[Boolean]](_))
  }

  def collection(name: String): ArangoCollection = {
    new ArangoCollection(client.path(Path.parse(s"/_db/${this.name}/")), this.name, name)
  }

  lazy val query: ArangoQuery = new ArangoQuery(client.path(Path.parse(s"/_db/${this.name}/")))
}