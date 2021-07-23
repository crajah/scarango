package com.outr.arango.api.model

import fabric._
import fabric.rw._

case class VersionDetailsStruct(architecture: Option[String] = None,
                                arm: Option[String] = None,
                                asan: Option[String] = None,
                                asmCrc32: Option[String] = None,
                                assertions: Option[String] = None,
                                boostVersion: Option[String] = None,
                                buildDate: Option[String] = None,
                                buildRepository: Option[String] = None,
                                compiler: Option[String] = None,
                                cplusplus: Option[String] = None,
                                debug: Option[String] = None,
                                endianness: Option[String] = None,
                                failureTests: Option[String] = None,
                                fdClientEventHandler: Option[String] = None,
                                fdSetsize: Option[String] = None,
                                fullVersionString: Option[String] = None,
                                host: Option[String] = None,
                                icuVersion: Option[String] = None,
                                jemalloc: Option[String] = None,
                                maintainerMode: Option[String] = None,
                                mode: Option[String] = None,
                                opensslVersion: Option[String] = None,
                                platform: Option[String] = None,
                                reactorType: Option[String] = None,
                                rocksdbVersion: Option[String] = None,
                                serverVersion: Option[String] = None,
                                sizeofInt: Option[String] = None,
                                sizeofVoid: Option[String] = None,
                                sse42: Option[String] = None,
                                unalignedAccess: Option[String] = None,
                                v8Version: Option[String] = None,
                                vpackVersion: Option[String] = None,
                                zlibVersion: Option[String] = None)

object VersionDetailsStruct {
  implicit val rw: ReaderWriter[VersionDetailsStruct] = ccRW
}