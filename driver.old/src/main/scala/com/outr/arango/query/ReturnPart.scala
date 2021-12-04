package com.outr.arango.query

import com.outr.arango.{Field, Query, Ref}

sealed trait ReturnPart {
  def value: String

  def build(): Query = {
    Query(s"RETURN ${value}", Map.empty)
  }
}

object ReturnPart {
  case class RefReturn(ref: Ref) extends ReturnPart {
    override def value: String = {
      val context = QueryBuilderContext()
      val name = context.name(ref)
      name
    }
  }

  case class Json(override val value: String) extends ReturnPart

  object New extends ReturnPart {
    override val value: String = "NEW"
  }
}