package com.outr.arango.core

import cats.effect.IO
import com.arangodb.async.ArangoCollectionAsync
import com.outr.arango.Id
import com.outr.arango.util.Helpers._
import fabric.Json

import scala.jdk.CollectionConverters._

trait ArangoDBDocuments[T] {
  protected def _collection: ArangoCollectionAsync

  def toT(value: Json): T
  def fromT(t: T): Json

  def count: IO[Int] = _collection.count().toIO.map(_.getCount.toInt)

  def id(key: String): Id[T] = Id[T](key, _collection.name())

  def apply(id: Id[T],
            default: Id[T] => T = id => throw NotFoundException(id._id)): IO[T] = get(id).map(_.getOrElse(default(id)))

  def get(id: Id[T]): IO[Option[T]] = _collection
    .getDocument(id._key, classOf[Json])
    .toIO
    .map(json => Option(json).map(toT))

  def insert(doc: T, options: CreateOptions = CreateOptions.Insert, transaction: StreamTransaction = None.orNull): IO[CreateResult[T]] = _collection
    .insertDocument(fromT(doc), options.copy(streamTransaction = options.streamTransaction.orElse(Option(transaction))))
    .toIO
    .map(createDocumentEntityConversion(_, toT))

  def upsert(doc: T, options: CreateOptions = CreateOptions.Upsert, transaction: StreamTransaction = None.orNull): IO[CreateResult[T]] =
    insert(doc, options.copy(streamTransaction = options.streamTransaction.orElse(Option(transaction))))

  def update(id: Id[T], doc: T, options: UpdateOptions = UpdateOptions.Default, transaction: StreamTransaction = None.orNull): IO[UpdateResult[T]] = _collection
    .updateDocument(id._key, fromT(doc), options.copy(streamTransaction = options.streamTransaction.orElse(Option(transaction))))
    .toIO
    .map(updateDocumentEntityConversion(_, toT))

  def delete(id: Id[T], options: DeleteOptions = DeleteOptions.Default, transaction: StreamTransaction = None.orNull): IO[DeleteResult[T]] = _collection
    .deleteDocument(id._key, classOf[Json], options.copy(streamTransaction = options.streamTransaction.orElse(Option(transaction))))
    .toIO
    .map(deleteDocumentEntityConversion(_, toT))

  object batch {
    def insert(docs: List[T], options: CreateOptions = CreateOptions.Insert, transaction: StreamTransaction = None.orNull): IO[CreateResults[T]] = _collection
      .insertDocuments(docs.map(fromT).asJava, options.copy(streamTransaction = options.streamTransaction.orElse(Option(transaction))))
      .toIO
      .map(multiDocumentCreateConversion(_, toT))

    def upsert(docs: List[T], options: CreateOptions = CreateOptions.Upsert, transaction: StreamTransaction = None.orNull): IO[CreateResults[T]] =
      insert(docs, options.copy(streamTransaction = options.streamTransaction.orElse(Option(transaction))))

    def delete(ids: List[Id[T]], options: DeleteOptions = DeleteOptions.Default, transaction: StreamTransaction = None.orNull): IO[DeleteResults[T]] = _collection
      .deleteDocuments(ids.map(_._key).asJava, classOf[Json], options.copy(streamTransaction = options.streamTransaction.orElse(Option(transaction))))
      .toIO
      .map(multiDocumentDeleteConversion(_, toT))
  }

  object stream {
    def apply[V](docs: fs2.Stream[IO, V], chunkSize: Int, process: fs2.Chunk[V] => IO[Int]): IO[Int] = docs
      .chunkN(chunkSize)
      .evalMap(process)
      .compile
      .foldMonoid

    def insert(docs: fs2.Stream[IO, T],
               chunkSize: Int = 1000,
               options: CreateOptions = CreateOptions.Insert,
               transaction: StreamTransaction = None.orNull): IO[Int] =
      apply[T](docs, chunkSize, chunk => batch.insert(chunk.toList, options, transaction).as(chunk.size))

    def upsert(docs: fs2.Stream[IO, T],
               chunkSize: Int = 1000,
               options: CreateOptions = CreateOptions.Upsert,
               transaction: StreamTransaction = None.orNull): IO[Int] =
      apply[T](docs, chunkSize, chunk => batch.upsert(chunk.toList, options, transaction).as(chunk.size))

    def delete(docs: fs2.Stream[IO, Id[T]],
               chunkSize: Int = 1000,
               options: DeleteOptions = DeleteOptions.Default,
               transaction: StreamTransaction = None.orNull): IO[Int] =
      apply[Id[T]](docs, chunkSize, chunk => batch.delete(chunk.toList, options, transaction).as(chunk.size))
  }
}