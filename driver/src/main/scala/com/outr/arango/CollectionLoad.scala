package com.outr.arango

case class CollectionLoad(id: String,
                          name: String,
                          count: Option[Int],
                          status: Int,
                          `type`: Int,
                          isSystem: Boolean)
