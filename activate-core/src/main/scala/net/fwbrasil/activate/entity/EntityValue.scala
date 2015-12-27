package net.fwbrasil.activate.entity

import java.util.{ Date, Calendar }
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import org.joda.time.base.AbstractInstant
import net.fwbrasil.activate.util.Reflection.getObject
import net.fwbrasil.activate.serialization.SerializationContext
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.serialization.Serializer
import net.fwbrasil.activate.serialization.javaSerializer
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.entity.id.EntityId
import org.joda.time.Instant
// import org.joda.time.DateMidnight
import org.joda.time.DateTime

abstract class EntityValue[V](val value: Option[V]) extends Serializable {
    def emptyValue: V
}

case class IntEntityValue(override val value: Option[Int])
    extends EntityValue(value) {
    def emptyValue = 0
}

case class LongEntityValue(override val value: Option[Long])
    extends EntityValue(value) {
    def emptyValue = 0l
}

case class BooleanEntityValue(override val value: Option[Boolean])
    extends EntityValue(value) {
    def emptyValue = false
}

case class CharEntityValue(override val value: Option[Char])
    extends EntityValue(value) {
    def emptyValue = ' '
}

case class StringEntityValue(override val value: Option[String])
    extends EntityValue(value) {
    def emptyValue = ""
}

case class EnumerationEntityValue[E <: Enumeration#Value: Manifest](override val value: Option[E])
    extends EntityValue[E](value) {
    def enumerationManifest = manifest[E]
    def enumerationClass = erasureOf[E]
    def enumerationObjectClass = ActivateContext.loadClass(enumerationClass.getName + "$")
    def enumerationObject =
        getObject[Enumeration](enumerationObjectClass)
    def emptyValue = enumerationObject.values.head.asInstanceOf[E]
}

case class FloatEntityValue(override val value: Option[Float])
    extends EntityValue(value) {
    def emptyValue = 0f
}

case class DoubleEntityValue(override val value: Option[Double])
    extends EntityValue(value) {
    def emptyValue = 0d
}

case class BigDecimalEntityValue(override val value: Option[BigDecimal])
    extends EntityValue(value) {
    def emptyValue = null
}

case class DateEntityValue(override val value: Option[java.util.Date])
    extends EntityValue(value) {
    def emptyValue = null
}

case class CalendarEntityValue(override val value: Option[java.util.Calendar])
    extends EntityValue(value) {
    def emptyValue = null
}

case class JodaInstantEntityValue[I <: AbstractInstant: Manifest](override val value: Option[I])
    extends EntityValue[I](value) {
    def instantClass = erasureOf[I]
    def emptyValue = null.asInstanceOf[I]
}

case class ByteArrayEntityValue(override val value: Option[Array[Byte]])
    extends EntityValue(value) {
    def emptyValue = null
}

case class EntityInstanceEntityValue[E <: BaseEntity: Manifest](override val value: Option[E])
    extends EntityValue[E](value) {
    def entityManifest = manifest[E]
    def entityClass = erasureOf[E]
    def emptyValue = null.asInstanceOf[E]
}

case class EntityInstanceReferenceValue[E <: BaseEntity: Manifest](override val value: Option[BaseEntity#ID])
    extends EntityValue[BaseEntity#ID](value) {
    def entityManifest = manifest[E]
    def entityClass = erasureOf[E]
    def emptyValue = null.asInstanceOf[BaseEntity#ID]
}

case class ListEntityValue[V](override val value: Option[List[V]])(implicit val m: Manifest[V], val tval: Option[V] => EntityValue[V])
    extends EntityValue[List[V]](value) {
    def valueManifest = manifest[V]
    def emptyValueEntityValue = tval(None)
    def valueEntityValue(value: V) = tval(Option(value))
    def emptyValue = List[V]()
}

case class LazyListEntityValue[V <: BaseEntity](override val value: Option[LazyList[V]])(implicit val m: Manifest[V], val tval: Option[V] => EntityValue[V])
    extends EntityValue[LazyList[V]](value) {
    def entityClass = erasureOf[V]
    def emptyValueEntityValue = tval(None)
    def valueManifest = manifest[V]
    def emptyValue = LazyList[V]()
}

case class SetEntityValue[V](override val value: Option[Set[V]])(implicit val m: Manifest[V], val tval: Option[V] => EntityValue[V])
    extends EntityValue[Set[V]](value) {
    def valueManifest = manifest[V]
    def emptyValueEntityValue = tval(None)
    def valueEntityValue(value: V) = tval(Option(value))
    def emptyValue = Set[V]()
}

case class ReferenceListEntityValue[V](override val value: Option[List[Option[BaseEntity#ID]]])(implicit val m: Manifest[V], val tval: Option[V] => EntityValue[V])
    extends EntityValue[List[Option[BaseEntity#ID]]](value) {
    def emptyValue = List()
}

case class SerializableEntityValue[S: Manifest](override val value: Option[S], val serializerOption: () => Option[Serializer] = () => None)
    extends EntityValue[S](value) {
    def typeManifest = manifest[S]
    def emptyValue = null.asInstanceOf[S]
    def forSerializer(s: => Serializer) = this.copy(serializerOption = () => Some(s))
    def serializer =
        serializerOption().get
}

object EntityValue extends ValueContext {

    private val encoders =
        MutableMap[Class[_], Encoder[Any, Any]]()

    def registerEncodersFor(classpathHints: List[Any]) = {
        val encoderNames = Reflection.getAllImplementorsNames(classpathHints, classOf[Encoder[_, _]]).
          map(name => name -> ActivateContext.loadClass(name))
        val encoderObjects = encoderNames.filter(_._1.endsWith("$")) map (_._2)
        val encoderClasses = encoderNames.filterNot(_._1.endsWith("$")) map(_._2)

        val encoders = encoderObjects.map(obj => obj.getField("MODULE$").get(obj).asInstanceOf[Encoder[Any, Any]]) ++
          encoderClasses.map(_.newInstance.asInstanceOf[Encoder[Any, Any]])

        for (encoder <- encoders)
            this.encoders(encoder.clazz) = encoder
    }

    def tvalFunctionOption[T](clazz: Class[_], genericParameter: Class[_]): Option[Option[T] => EntityValue[T]] =
        Option((
            if (encoders.contains(clazz))
                (value: Option[Any]) => encoders(clazz).entityValue(value)
            else if (clazz == classOf[String])
                (value: Option[String]) => toStringEntityValueOption(value)
            else if (clazz == classOf[Int] || clazz == classOf[java.lang.Integer])
                (value: Option[Int]) => toIntEntityValueOption(value)
            else if (clazz == classOf[Long] || clazz == classOf[java.lang.Long])
                (value: Option[Long]) => toLongEntityValueOption(value)
            else if (clazz == classOf[Boolean] || clazz == classOf[java.lang.Boolean])
                (value: Option[Boolean]) => toBooleanEntityValueOption(value)
            else if (clazz == classOf[Char] || clazz == classOf[java.lang.Character])
                (value: Option[Char]) => toCharEntityValueOption(value)
            else if (classOf[Enumeration#Value].isAssignableFrom(clazz))
                (value: Option[Enumeration#Value]) => toEnumerationEntityValueOption(value)(manifestClass(clazz))
            else if (clazz == classOf[Float] || clazz == classOf[java.lang.Float])
                (value: Option[Float]) => toFloatEntityValueOption(value)
            else if (clazz == classOf[Double] || clazz == classOf[java.lang.Double])
                (value: Option[Double]) => toDoubleEntityValueOption(value)
            else if (clazz == classOf[BigDecimal])
                (value: Option[BigDecimal]) => toBigDecimalEntityValueOption(value)
            else if (clazz == classOf[java.util.Date])
                (value: Option[Date]) => toDateEntityValueOption(value)
            else if (classOf[AbstractInstant].isAssignableFrom(clazz))
                (value: Option[AbstractInstant]) => JodaInstantEntityValue(value)(manifestClass(clazz))
            else if (clazz == classOf[java.util.Calendar])
                (value: Option[Calendar]) => toCalendarEntityValueOption(value)
            else if (clazz == classOf[Array[Byte]])
                (value: Option[Array[Byte]]) => toByteArrayEntityValueOption(value)
            else if (classOf[BaseEntity].isAssignableFrom(clazz))
                ((value: Option[BaseEntity]) => toEntityInstanceEntityValueOption(value)(manifestClass(clazz)))
            else if (classOf[List[_]].isAssignableFrom(clazz))
                (value: Option[List[Any]]) => toListEntityValueOption(value)(manifestClass(genericParameter), tvalFunction(genericParameter, classOf[Object]))
            else if (classOf[LazyList[_]] == clazz)
                (value: Option[LazyList[BaseEntity]]) => toLazyListEntityValueOption(value)(manifestClass(genericParameter), tvalFunction(genericParameter, classOf[Object]))
            else if (classOf[java.io.Serializable].isAssignableFrom(clazz) || clazz.isArray)
                (value: Option[java.io.Serializable]) => toSerializableEntityValueOption(value)(manifestClass(clazz))
            else
                null).asInstanceOf[(Option[T]) => EntityValue[T]])

    private[activate] def tvalFunction[T](clazz: Class[_], genericParameter: Class[_]): Option[T] => EntityValue[T] =
        tvalFunctionOption[T](clazz, genericParameter).getOrElse(throw new IllegalStateException("Invalid entity property type. " + clazz))

}

trait ValueContext {

    import language.implicitConversions

    implicit def toIntEntityValue(value: Int) =
        toIntEntityValueOption(Option(value))
    implicit def toLongEntityValue(value: Long) =
        toLongEntityValueOption(Option(value))
    implicit def toBooleanEntityValue(value: Boolean) =
        toBooleanEntityValueOption(Option(value))
    implicit def toCharEntityValue(value: Char) =
        toCharEntityValueOption(Option(value))
    implicit def toStringEntityValue(value: String) =
        toStringEntityValueOption(Option(value))
    implicit def toEnumerationEntityValue[E <: Enumeration#Value: Manifest](value: E): EnumerationEntityValue[E] =
        toEnumerationEntityValueOption[E](Option(value))
    implicit def toFloatEntityValue(value: Float) =
        toFloatEntityValueOption(Option(value))
    implicit def toDoubleEntityValue(value: Double) =
        toDoubleEntityValueOption(Option(value))
    implicit def toBigDecimalEntityValue(value: BigDecimal) =
        toBigDecimalEntityValueOption(Option(value))
    implicit def toDateEntityValue(value: java.util.Date) =
        toDateEntityValueOption(Option(value))
    implicit def toJodaInstantEntityValue(value: Instant) =
        toJodaInstantEntityValueOption(Option(value))
    // implicit def toJodaDateMidnightEntityValue(value: DateMidnight) =
    //     toJodaDateMidnightEntityValueOption(Option(value))
    implicit def toJodaDateTimeEntityValue(value: DateTime) =
        toJodaDateTimeEntityValueOption(Option(value))
    implicit def toCalendarEntityValue(value: java.util.Calendar) =
        toCalendarEntityValueOption(Option(value))
    implicit def toByteArrayEntityValue(value: Array[Byte]) =
        toByteArrayEntityValueOption(Option(value))
    implicit def toEntityInstanceEntityValue[E <: BaseEntity: Manifest](value: E) =
        toEntityInstanceEntityValueOption(Option(value))
    def toListEntityValue[V](value: List[V])(implicit m: Manifest[V], tval: Option[V] => EntityValue[V]): ListEntityValue[V] =
        toListEntityValueOption(Option(value))
    def toLazyListEntityValue[V <: BaseEntity](value: LazyList[V])(implicit m: Manifest[V], tval: Option[V] => EntityValue[V]): LazyListEntityValue[V] =
        toLazyListEntityValueOption(Option(value))
    implicit def toSerializableEntityValue[S <: java.io.Serializable: Manifest](value: S): SerializableEntityValue[S] =
        toSerializableEntityValueOption(Option(value))

    implicit def toIntEntityValueOption(value: Option[Int]) =
        IntEntityValue(value)
    implicit def toLongEntityValueOption(value: Option[Long]) =
        LongEntityValue(value)
    implicit def toBooleanEntityValueOption(value: Option[Boolean]) =
        BooleanEntityValue(value)
    implicit def toCharEntityValueOption(value: Option[Char]) =
        CharEntityValue(value)
    implicit def toStringEntityValueOption(value: Option[String]) =
        StringEntityValue(value)
    implicit def toEnumerationEntityValueOption[E <: Enumeration#Value: Manifest](value: Option[E]): EnumerationEntityValue[E] =
        EnumerationEntityValue[E](value)
    implicit def toFloatEntityValueOption(value: Option[Float]) =
        FloatEntityValue(value)
    implicit def toDoubleEntityValueOption(value: Option[Double]) =
        DoubleEntityValue(value)
    implicit def toBigDecimalEntityValueOption(value: Option[BigDecimal]) =
        BigDecimalEntityValue(value)
    implicit def toDateEntityValueOption(value: Option[java.util.Date]) =
        DateEntityValue(value)
    implicit def toJodaInstantEntityValueOption(value: Option[Instant]): JodaInstantEntityValue[Instant] =
        JodaInstantEntityValue(value)
    // implicit def toJodaDateMidnightEntityValueOption(value: Option[DateMidnight]): JodaInstantEntityValue[DateMidnight] =
    //     JodaInstantEntityValue(value)
    implicit def toJodaDateTimeEntityValueOption(value: Option[DateTime]): JodaInstantEntityValue[DateTime] =
        JodaInstantEntityValue(value)
    implicit def toCalendarEntityValueOption(value: Option[java.util.Calendar]) =
        CalendarEntityValue(value)
    implicit def toByteArrayEntityValueOption(value: Option[Array[Byte]]) =
        ByteArrayEntityValue(value)
    implicit def toEntityInstanceEntityValueOption[E <: BaseEntity: Manifest](value: Option[E]): EntityInstanceEntityValue[E] =
        EntityInstanceEntityValue(value)
    def toListEntityValueOption[V](value: Option[List[V]])(implicit m: Manifest[V], tval: Option[V] => EntityValue[V]): ListEntityValue[V] =
        ListEntityValue[V](value)
    def toLazyListEntityValueOption[V <: BaseEntity](value: Option[LazyList[V]])(implicit m: Manifest[V], tval: Option[V] => EntityValue[V]): LazyListEntityValue[V] =
        LazyListEntityValue[V](value)
    implicit def toSerializableEntityValueOption[S <: java.io.Serializable: Manifest](value: Option[S]): SerializableEntityValue[S] =
        SerializableEntityValue[S](value)

}
