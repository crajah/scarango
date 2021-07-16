package com.outr.arango.api

import com.outr.arango.api.model._
import io.youi.client.HttpClient
import io.youi.http.{HttpMethod, HttpRequest, HttpResponse}
import io.youi.net._
import fabric.Value
import io.youi.client.intercept.Interceptor

import scala.concurrent.{ExecutionContext, Future}
      
object APIViewViewNamePropertiesArangoSearch {

  def patch(client: HttpClient, viewName: String, body: PatchAPIViewPropertiesIresearch)(implicit ec: ExecutionContext): Future[Value] = client
    .method(HttpMethod.Patch)
    .path(path"/_api/view/{view-name}/properties".withArguments(Map("view-name" -> viewName)), append = true)
    .restful[PatchAPIViewPropertiesIresearch, Value](body)


  def put(client: HttpClient, viewName: String, body: PostAPIViewProps)(implicit ec: ExecutionContext): Future[Value] = {
    client
      .method(HttpMethod.Put)
      .path(path"/_api/view/{view-name}/properties".withArguments(Map("view-name" -> viewName)), append = true)
      .restful[PostAPIViewProps, Value](body)
  }
}