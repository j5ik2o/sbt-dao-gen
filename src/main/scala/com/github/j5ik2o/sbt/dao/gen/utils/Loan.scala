package com.github.j5ik2o.sbt.dao.gen.utils

import scala.language.reflectiveCalls
import scala.util._
import scala.util.control.NonFatal

object Loan {

  def using[A <: { def close() }, B](resource: A)(func: A => Try[B]): Try[B] =
    func(resource)
      .recoverWith { case NonFatal(e) =>
        Failure(e)
      }
      .map { r =>
        resource.close()
        r
      }

  def using[A <: { def close() }, B](resource: Try[A])(func: A => Try[B]): Try[B] =
    resource.flatMap { r =>
      func(r)
        .recoverWith { case NonFatal(e) =>
          Failure(e)
        }
        .map { v =>
          r.close()
          v
        }
    }

}
