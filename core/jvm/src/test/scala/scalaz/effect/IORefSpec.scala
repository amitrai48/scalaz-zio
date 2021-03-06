package scalaz.effect

import org.specs2.Specification

class IORefSpec extends Specification with RTS {

  def is = "IORefSpec".title ^ s2"""
   Create a new IORef with a specified value and check if:
      `read` returns the current value.                                                        $e1
      `write` puts the new value correctly.                                                    $e2
      `modify` changes the value and returns the updated value.                                $e3
      `modifyFold` changes the value and returns another value computed from the modification. $e4
      `writeLater` puts a new value.                                                           $e5
      `tryWrite` returns
         true and puts a new value to IORef.                                                   $e6
         false and abort if there is a concurrent modification of the value by other fibers.   $e7
      `compareAndSet` returns
        true if the previous value and the current value have the same reference.              $e8
        false if the previous value and the current value have a different reference.          $e9
    """

  val (current, update) = ("value", "new value")

  def e1 =
    unsafePerformIO(
      for {
        ref   <- IORef[Nothing, String](current)
        value <- ref.read
      } yield value must beTheSameAs(current)
    )

  def e2 =
    unsafePerformIO(
      for {
        ref   <- IORef[Nothing, String](current)
        _     <- ref.write[Nothing](update)
        value <- ref.read
      } yield value must beTheSameAs(update)
    )

  def e3 =
    unsafePerformIO(
      for {
        ref   <- IORef[Nothing, String](current)
        value <- ref.modify(_ => update)
      } yield value must beTheSameAs(update)
    )

  def e4 =
    unsafePerformIO(
      for {
        ref   <- IORef[Nothing, String](current)
        r     <- ref.modifyFold[Nothing, String](_ => ("hello", update))
        value <- ref.read
      } yield (r must beTheSameAs("hello")) and (value must beTheSameAs(update))
    )

  def e5 =
    unsafePerformIO(
      for {
        ref   <- IORef[Nothing, String](current)
        _     <- ref.writeLater[Nothing](update)
        value <- ref.read
      } yield value must beTheSameAs(update)
    )

  def e6 =
    unsafePerformIO(
      for {
        ref     <- IORef[Nothing, String](current)
        success <- ref.tryWrite[Nothing](update)
        value   <- ref.read
      } yield (success must beTrue) and (value must beTheSameAs(update))
    )

  def e7 = {

    def tryWriteUntilFalse(ref: IORef[Int], update: Int): IO[Nothing, Boolean] =
      ref
        .tryWrite[Nothing](update)
        .flatMap(success => if (!success) IO.point[Nothing, Boolean](success) else tryWriteUntilFalse(ref, update))

    unsafePerformIO(
      for {
        ref     <- IORef[Nothing, Int](0)
        f1      <- ref.write[Nothing](1).forever[Unit].fork[Nothing]
        f2      <- ref.write[Nothing](2).forever[Unit].fork[Nothing]
        success <- tryWriteUntilFalse(ref, 3)
        value   <- ref.read[Nothing]
        _       <- f1.zipWith(f2)((_, _) => ()).interrupt[Nothing](new Error("Terminated fiber"))
      } yield (success must beFalse) and (value must be_!=(3))
    )

  }

  def e8 =
    unsafePerformIO(
      for {
        ref     <- IORef[Nothing, String](current)
        success <- ref.compareAndSet[Nothing](current, update)
        value   <- ref.read
      } yield (success must beTrue) and (value must beTheSameAs(update))
    )

  def e9 =
    unsafePerformIO(
      for {
        ref     <- IORef[Nothing, String](current)
        success <- ref.compareAndSet[Nothing](update, current)
        value   <- ref.read
      } yield (success must beFalse) and (value must beTheSameAs(current))
    )

}
