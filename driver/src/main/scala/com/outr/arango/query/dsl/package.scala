package com.outr.arango.query

import cats.effect.IO
import com.outr.arango.collection.DocumentCollection
import com.outr.arango.{Document, DocumentModel, DocumentRef, Field, FieldAndValue, NamedRef, Ref, WrappedRef}
import fabric._
import fabric.rw._

import scala.language.implicitConversions

package object dsl {
  private val forced = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  implicit def int2Value(i: Int): Json = num(i)
  implicit def string2Json(s: String): Json = str(s)

  implicit def ref2ReturnPart(ref: Ref): ReturnPart = {
    ReturnPart.RefReturn(ref)
  }

  implicit def string2ReturnPart(json: String): ReturnPart = this.json(json)

  implicit class DocumentCollectionExtras[D <: Document[D]](val collection: DocumentCollection[D]) extends AnyVal {
    def modify(filter: => Filter, fieldAndValues: FieldAndValue[_]*): IO[Int] = {
      val v = DocumentRef[D, DocumentModel[D]](collection.model, None)
      val count = ref("count")

      val query = aql {
        FOR(v) IN collection
        FILTER(withReference(v)(filter))
        UPDATE(v, fieldAndValues: _*)
        COLLECT WITH COUNT INTO count
        RETURN(count)
      }
      collection.graph.query[Int](query).one
    }
  }

  implicit class FieldExtras[T: RW](field: => Field[T]) {
    def thisField: Field[T] = field

//    def apply(value: T): FieldAndValue[T] = macro AQLMacros.fieldAndValue
    private def cond(value: T, condition: String): Filter = {
      val context = QueryBuilderContext()
      val (refOption, f) = withReference(field)
      val ref = refOption.getOrElse(throw new RuntimeException("No reference for field!"))
      val leftName = context.name(ref)
      val left = Query(s"$leftName.${f.fieldName}")
      val right = Query.variable(value.json)

      new Filter(left, condition, right)
    }
    private def cond(that: => Field[T], condition: String): Filter = {
      val context = QueryBuilderContext()
      val (leftRefOption, leftField) = withReference(field)
      val leftRef = leftRefOption.getOrElse(throw new RuntimeException("No reference for field!"))
      val leftName = context.name(leftRef)
      val left = Query(s"$leftName.${leftField.fieldName}")
      val (rightRefOption, rightField) = withReference(that)
      val rightRef = rightRefOption.getOrElse(throw new RuntimeException("No reference for field!"))
      val rightName = context.name(rightRef)
      val right = Query(s"$rightName.${rightField.fieldName}")

      new Filter(left, condition, right)
    }
    private def cond(values: Seq[T], condition: String): Filter = {
      val context = QueryBuilderContext()
      val (refOption, f) = withReference(field)
      val ref = refOption.getOrElse(throw new RuntimeException("No reference for field!"))
      val leftName = context.name(ref)
      val left = Query(s"$leftName.${f.fieldName}")
      val right = Query.variable(arr(values.map(_.json): _*))

      new Filter(left, condition, right)
    }
    private def stringCond(value: String, condition: String): Filter = {
      val context = QueryBuilderContext()
      val (refOption, f) = withReference(field)
      val ref = refOption.getOrElse(throw new RuntimeException("No reference for field!"))
      val leftName = context.name(ref)
      val left = Query(s"$leftName.${f.fieldName}")
      val right = Query.variable(str(value))

      new Filter(left, condition, right)
    }
    def is(value: T): Filter = ===(value)
    def ===(value: T): Filter = {
      cond(value, "==")
    }
    def ===(field: => Field[T]): Filter = {
      cond(field, "==")
    }
    def isNot(value: T): Filter = !==(value)
    def !=(value: T): Filter = !==(value)
    def !==(value: T): Filter = {
      cond(value, "!=")
    }
    def >(value: T): Filter = {
      cond(value, ">")
    }
    def >=(value: T): Filter = {
      cond(value, ">=")
    }
    def <(value: T): Filter = {
      cond(value, "<")
    }
    def <=(value: T): Filter = {
      cond(value, "<=")
    }
    def IN(values: Seq[T]): Filter = {
      cond(values, "IN")
    }
    def NOT_IN(values: Seq[T]): Filter = {
      cond(values, "NOT IN")
    }
    def LIKE(value: String): Filter = {
      stringCond(value, "LIKE")
    }
    def NOT_LIKE(value: String): Filter = {
      stringCond(value, "NOT LIKE")
    }
    def =~(value: String): Filter = {
      stringCond(value, "=~")
    }
    def !~(value: String): Filter = {
      stringCond(value, "!~")
    }

    def asc: (Field[T], SortDirection) = (field, SortDirection.ASC)
    def ASC: (Field[T], SortDirection) = (field, SortDirection.ASC)
    def desc: (Field[T], SortDirection) = (field, SortDirection.DESC)
    def DESC: (Field[T], SortDirection) = (field, SortDirection.DESC)
  }

  def withReference[Return](ref: Ref)(f: => Return): Return = {
    val context = QueryBuilderContext()
    context.ref = Some(ref)
    val previous = forced.get()
    forced.set(true)
    try {
      val r: Return = f
      r
    } finally {
      context.ref = None
      forced.set(previous)
    }
  }

  def withReference[Return](f: => Return): (Option[Ref], Return) = {
    val context = QueryBuilderContext()
    if (!forced.get()) context.ref = None
    val r: Return = f
    (context.ref, r)
  }

  implicit def ref2Wrapped[T](ref: WrappedRef[T]): T = {
    val context = QueryBuilderContext()
    context.ref = Some(ref)
    ref.wrapped
  }

  def FOR[D <: Document[D], Model <: DocumentModel[D]](ref: DocumentRef[D, Model]): ForPartial[D, Model] = ForPartial(ref)

  def REMOVE[D <: Document[D], Model <: DocumentModel[D]](ref: DocumentRef[D, Model]): RemovePartial[D, Model] = RemovePartial(ref)

  def SORT[T](f: => (Field[T], SortDirection)): Unit = {
    val context = QueryBuilderContext()
    val (refOption, (field, sort)) = withReference(f)
    val ref = refOption.getOrElse(throw new RuntimeException("No ref option found for SORT!"))
    val name = context.name(ref)
    val sortValue = sort match {
      case SortDirection.ASC => "ASC"
      case SortDirection.DESC => "DESC"
    }
    context.addQuery(Query(s"SORT $name.${field.fieldName} $sortValue"))
  }

  def FILTER(filter: Filter): Unit = {
    val query = filter.build()
    addQuery(Query.merge(List(Query("FILTER"), query), " "))
  }

  def COLLECT: CollectStart.type = CollectStart

  def COUNT: CollectWith.Count.type = CollectWith.Count

  def UPDATE[D <: Document[D], Model <: DocumentModel[D]](ref: DocumentRef[D, Model], values: FieldAndValue[_]*): Unit = {
    addQuery(UpdatePart(ref, values.toList).build())
  }

  def NEW: ReturnPart = ReturnPart.New

  def mapped(mappings: (String, Field[_])*): ReturnPart = json(mappings.map {
    case (name, field) => s"$name: ${field.fieldName}"
  }.mkString("{", ", ", "}"))

  def json(json: String): ReturnPart = ReturnPart.Json(json)

  def LIMIT(offset: Int, limit: Int): Unit = addQuery(Query(s"LIMIT $offset, $limit"))

  def LIMIT(limit: Int): Unit = LIMIT(0, limit)

  def RETURN(part: ReturnPart): Unit = addQuery(part.build())

  def RETURN[T](field: => Field[T]): Unit = {
    val context = QueryBuilderContext()
    val (refOption, f) = withReference(field)
    val ref = refOption.getOrElse(throw new RuntimeException("No reference for field!"))
    val leftName = context.name(ref)
    addQuery(Query(s"RETURN $leftName.${f.fieldName}"))
  }

  def addQuery(query: Query): Unit = {
    val context = QueryBuilderContext()
    context.addQuery(query)
  }

  def ref: Ref = new Ref {
    override def refName: Option[String] = None
  }
  def ref(name: String): Ref = NamedRef(name)
  def ref[T](wrapped: T, name: Option[String]): WrappedRef[T] = new WrappedRef[T](wrapped, name)

  def aql(f: => Unit): Query = QueryBuilderContext.contextualize(f)
}