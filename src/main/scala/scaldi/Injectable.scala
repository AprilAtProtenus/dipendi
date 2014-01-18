package scaldi

import language.{postfixOps, implicitConversions}

import scaldi.util.Util._
import scala.reflect.runtime.universe.{TypeTag, typeTag}
import TypeTagIdentifier._
import util.ReflectionHelper

trait Injectable {
  protected def inject[T](implicit injector: Injector, tt: TypeTag[T]): T =
    List[Identifier](typeId[T]) |>
        (ids => injectWithDefault[T](injector, noBindingFound(ids))(ids))

  protected def inject[T](identifiers: Identifier*)(implicit injector: Injector, tt: TypeTag[T]): T =
    List[Identifier](typeId[T]) ++ identifiers |>
        (ids => injectWithDefault[T](injector, noBindingFound(ids))(ids))

  protected def inject[T](constraints: => InjectConstraints[T])(implicit injector: Injector, tt: TypeTag[T]): T =
    List(typeId[T]) ++ constraints.identifiers |>
      (ids => injectWithDefault[T](injector, constraints.default map(_()) getOrElse noBindingFound(ids))(ids))

  protected def injectWithDefault[T](default: => T)(implicit injector: Injector, tt: TypeTag[T]): T =
    List(typeId[T]) |> injectWithDefault[T](injector, default)

  protected def injectWithDefault[T](identifiers: Identifier*)(default: => T)(implicit injector: Injector, tt: TypeTag[T]): T =
    List(typeId[T]) ++ identifiers |> injectWithDefault[T](injector, default)

  protected def injectAllOfType[T](implicit injector: Injector, tt: TypeTag[T]): List[T] =
    injectAllOfType[T]()(injector, tt)

  protected def injectAllOfType[T](identifiers: Identifier*)(implicit injector: Injector, tt: TypeTag[T]): List[T] =
    List[Identifier](typeId[T]) ++ identifiers |>
        (ids => injector getBindings ids flatMap (_.get) map (_.asInstanceOf[T]))

  protected def injectAll(identifiers: Identifier*)(implicit injector: Injector): List[Any] =
    identifiers |> (ids => injector getBindings ids.toList flatMap (_.get))

  /**
   * This check is requires because Scala can't infer return type, so you can't write something like this:
   *     val host: String = inject
   *
   * In this example `Nothing` would be inferred. And it's not what most users expect!
   *
   * For more info, please refer to https://issues.scala-lang.org/browse/SI-2609
   */
  private def check[T](tt: TypeTag[T]) =
    if (tt == typeTag[Nothing])
      throw new InjectException("Unfortunately inject can't infer required binding type. " +
          "Please provide expected injection type explicitly: inject [MyType]")
    else tt

  private def injectWithDefault[T](injector: Injector, default: => T)(ids: List[Identifier]) =
    injector getBinding ids flatMap (_.get) map (_.asInstanceOf[T]) getOrElse default

  private def noBindingFound(ids: List[Identifier]) =
    throw new InjectException(ids map ("  * " +) mkString ("No biding found with following identifiers:\n", "\n", ""))

  // in case is identifier goes at first

  protected implicit def canBeIdentifiedToConstraints[T : CanBeIdentifier](target: T) =
    new InjectConstraints[Nothing](initialIdentifiers = List(implicitly[CanBeIdentifier[T]].toIdentifier(target)))

  // initial words

  protected val identified = new IdentifiedWord
  protected val by = new ByWord
}

trait OpenInjectable extends Injectable {
  override val identified = new IdentifiedWord
  override val by = new ByWord

  override implicit def canBeIdentifiedToConstraints[T: CanBeIdentifier](target: T) =
    super.canBeIdentifiedToConstraints[T](target)

  override def inject[T](implicit injector: Injector, tt: TypeTag[T]) =
    super.inject[T](injector, tt)

  override def inject[T](identifiers: Identifier*)(implicit injector: Injector, tt: TypeTag[T]): T =
    super.inject[T](identifiers: _*)(injector, tt)

  override def inject[T](constraints: => InjectConstraints[T])(implicit injector: Injector, tt: TypeTag[T]) =
    super.inject[T](constraints)(injector, tt)

  override def injectWithDefault[T](default: => T)(implicit injector: Injector, tt: TypeTag[T]) =
    super.injectWithDefault[T](default)(injector, tt)

  override def injectWithDefault[T](identifiers: Identifier*)(default: => T)(implicit injector: Injector, tt: TypeTag[T]): T =
    super.injectWithDefault[T](identifiers: _*)(default)(injector, tt)

  override def injectAllOfType[T](implicit injector: Injector, tt: TypeTag[T]): List[T] =
    super.injectAllOfType[T](injector, tt)

  override def injectAllOfType[T](identifiers: Identifier*)(implicit injector: Injector, tt: TypeTag[T]): List[T] =
    super.injectAllOfType[T](identifiers: _*)(injector, tt)

  override def injectAll(identifiers: Identifier*)(implicit injector: Injector): List[Any] =
    super.injectAll(identifiers: _*)(injector)
}

object Injectable extends OpenInjectable

class IdentifiedWord[+T](default: Option[() => T] = None, initialIdentifiers: List[Identifier] = Nil) {
  def by[T: CanBeIdentifier](target: T*) = new InjectConstraints(default, initialIdentifiers ++ (target map implicitly[CanBeIdentifier[T]].toIdentifier))
}

class ByWord(initialIdentifiers: List[Identifier] = Nil) {
  def default[T](fn: => T) = new InjectConstraints[T](Some(() => fn), initialIdentifiers)
}

case class InjectConstraints[+T](default: Option[() => T] = None, initialIdentifiers: List[Identifier] = Nil) {
  var identifiers : List[Identifier] = initialIdentifiers

  def and(ids: Identifier*) = {
    identifiers = identifiers ++ ids
    this
  }

  def and(by: ByWord) = new ByWord(identifiers)
  def which(by: ByWord) = and(by)
  def that(by: ByWord) = and(by)
  def is(by: ByWord) = and(by)

  def and(by: IdentifiedWord[_]) = new IdentifiedWord[T](default, identifiers)
}