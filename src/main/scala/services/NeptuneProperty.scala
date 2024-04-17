package services

import scala.annotation.implicitNotFound
import scala.language.implicitConversions
import scala.jdk.CollectionConverters._

sealed trait NeptuneProperty {
  def getUntyped:Object
}

sealed trait NeptuneSimpleProperty extends NeptuneProperty

case class StringProperty(value:String) extends NeptuneSimpleProperty {
  override def getUntyped: String = value
}
case class IntProperty(value:Int) extends NeptuneSimpleProperty {
  override def getUntyped: Integer = value
}
case class SetProperty(value:Map[String, NeptuneSimpleProperty]) extends NeptuneProperty {
  override def getUntyped: Seq[Object] = value.toSeq.flatMap(kv=>Seq(kv._1, kv._2))
}
case class ListProperty(value:Seq[NeptuneSimpleProperty]) extends NeptuneProperty {
  override def getUntyped: Seq[Object] = value.map(_.getUntyped)
}
case class DoubleProperty(value:Double) extends NeptuneSimpleProperty {
  override def getUntyped: java.lang.Double = value
}
case class BoolProperty(value:Boolean) extends NeptuneSimpleProperty {
  override def getUntyped: java.lang.Boolean = value
}

object NeptuneProperty {
  object Implicits {
    implicit def fromString(str:String): NeptuneSimpleProperty = StringProperty(str)
    implicit def fromInt(i:Int): NeptuneSimpleProperty = IntProperty(i)
    implicit def fromDouble(d:Double):NeptuneSimpleProperty = DoubleProperty(d)
    implicit def fromFloat(f:Float):NeptuneSimpleProperty = fromDouble(f.toDouble)
    implicit def fromList(l:Seq[NeptuneSimpleProperty]): NeptuneProperty = ListProperty(l)
    implicit def fromBool(b:Boolean):NeptuneSimpleProperty = BoolProperty(b)

    @implicitNotFound("Could not find an implicit conversion for NeptuneSimpleProperty[${T}]. Try implementing one in NeptuneProperty.scala?")
    implicit def fromOptional[T](opt:Option[T])(implicit conv:T=>NeptuneSimpleProperty):Option[NeptuneSimpleProperty] = opt.map(conv)
    implicit def fromSeq[T](s: Seq[T])(implicit conv: T => NeptuneSimpleProperty): NeptuneProperty = fromList(s.map(conv))

  }
}