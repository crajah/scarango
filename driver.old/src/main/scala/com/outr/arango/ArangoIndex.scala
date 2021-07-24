package com.outr.arango

import com.outr.arango.api.{APIIndex, APIIndexIndexHandle, APIIndexfulltext, APIIndexgeo, APIIndexpersistent, APIIndexttl}
import com.outr.arango.api.model.{PostAPIIndexFulltext, PostAPIIndexGeo, PostAPIIndexPersistent, PostAPIIndexTtl}
import fabric.rw.Asable
import io.youi.client.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class ArangoIndex(client: HttpClient, dbName: String, collectionName: String) {
  def create(index: Index)(implicit ec: ExecutionContext): Future[IndexInfo] = {
    val future = index.`type` match {
      case IndexType.Persistent => APIIndexpersistent.post(client, collectionName, PostAPIIndexPersistent("persistent", Some(index.fields), Some(index.sparse), Some(index.unique)))
      case IndexType.Geo => APIIndexgeo.post(client, collectionName, PostAPIIndexGeo("geo", Some(index.fields), Some(index.geoJson.toString)))
      case IndexType.FullText => APIIndexfulltext.post(client, collectionName, PostAPIIndexFulltext("fulltext", Some(index.fields), Some(index.minLength)))
      case IndexType.TTL => APIIndexttl.post(client, collectionName, PostAPIIndexTtl("ttl", Some(index.expireAfterSeconds.toDouble), Some(index.fields)))
    }
    future.map(_.as[IndexInfo])
  }

  def list()(implicit ec: ExecutionContext): Future[IndexList] = {
    APIIndex.get(client, collectionName).map(_.as[IndexList])
  }

  def delete(id: Id[Index])(implicit ec: ExecutionContext): Future[IndexDelete] = {
    APIIndexIndexHandle.delete(client, collectionName, id.value).map(_.as[IndexDelete])
  }
}