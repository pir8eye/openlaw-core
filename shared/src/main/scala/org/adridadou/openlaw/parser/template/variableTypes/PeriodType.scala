package org.adridadou.openlaw.parser.template.variableTypes

import org.parboiled2._
import VariableType._
import cats.implicits._
import org.adridadou.openlaw.{OpenlawBigDecimal, OpenlawDateTime, OpenlawNativeValue, OpenlawValue}
import org.adridadou.openlaw.parser.template._
import org.adridadou.openlaw.parser.template.expressions.ValueExpression

import scala.language.implicitConversions
import scala.math.BigDecimal

case object PeriodType extends VariableType("Period") {

  override def plus(optLeft: Option[OpenlawValue], optRight: Option[OpenlawValue], executionResult:TemplateExecutionResult): Option[OpenlawValue] = for {
    left <- optLeft
    right <-optRight
  } yield {
    right match {
      case period:Period => plus(convert[Period](left), period)
      case date:OpenlawDateTime => DateTimeType.plus(date, convert[Period](left))
    }
  }

  private def plus(left:Period, right:Period):Period = left.plus(right)

  override def minus(optLeft: Option[OpenlawValue], optRight: Option[OpenlawValue], executionResult:TemplateExecutionResult): Option[Period] = for(
    left <- optLeft;
    right <-optRight
  ) yield minus(convert[Period](left), convert[Period](right))

  private def minus(left:Period, right:Period):Period = left.minus(right)

  override def divide(optLeft: Option[OpenlawValue], optRight: Option[OpenlawValue], executionResult: TemplateExecutionResult): Option[Period] = for {
    left <- optLeft
    right <-optRight if convert[OpenlawBigDecimal](right) =!= BigDecimal(0)
  } yield divide(convert[Period](left), convert[OpenlawBigDecimal](right))

  private def divide(left:Period, right:OpenlawBigDecimal):Period = left.divide(right)

  override def cast(value: String, executionResult:TemplateExecutionResult): Period =
    cast(value)

  def cast(value: String): Period = {
    val parser = new PeriodTypeParser(value)
    parser.root.run().toEither match {
      case Right(res) => res
      case Left(ex:ParseError) =>
        throw new RuntimeException(parser.formatError(ex))
      case Left(ex) =>
        throw ex
    }
  }

  override def isCompatibleType(otherType: VariableType, operation: ValueOperation): Boolean = operation match {
    case Plus => otherType === PeriodType || otherType === DateType || otherType === DateTimeType
    case Minus => otherType === PeriodType || otherType === DateType || otherType === DateTimeType
    case Divide => otherType === NumberType
    case _ => false
  }

  override def internalFormat(value: OpenlawValue): String = {
    val period = convert[Period](value)
    val result = ( if( period.years > 0 ) s"${period.years}" + " years " else "") +
      ( if( period.months > 0 ) s"${period.months}" + " months " else "") +
      ( if( period.weeks > 0 ) s"${period.weeks}" + " weeks " else "") +
      ( if( period.days > 0 ) s"${period.days}" + " days " else "") +
      ( if( period.hours > 0 ) s"${period.hours}" + " hours " else "") +
      ( if( period.minutes > 0 ) s"${period.minutes}" + " minutes " else "") +
      ( if( period.seconds > 0 ) s"${period.seconds}" + " seconds " else "")
    result
  }

  override def getTypeClass: Class[_ <: Period] = classOf[Period]

  def thisType: VariableType = PeriodType

  override def operationWith(rightType: VariableType, operation: ValueOperation): VariableType = (rightType, operation) match {
    case (NumberType, Divide) => PeriodType
    case _ => rightType
  }

  override def validateOperation(expr: ValueExpression, executionResult: TemplateExecutionResult): Option[String] = {
    expr.operation match {
      case Divide =>
        (expr.left.evaluate(executionResult), expr.right.evaluate(executionResult)) match {
          case (_, Some(value:OpenlawBigDecimal)) if value.underlying === BigDecimal(0) => Some(s"error while evaluating the expression '$expr': division by zero!")
          case (Some(period: Period), _) if period.months > 0 => Some(s"error while evaluating the expression '$expr': cannot divide months")
          case _ => None
        }
      case _ =>
        None
    }
  }
}

class PeriodTypeParser(val input: ParserInput) extends Parser {

  implicit def wspStr(s: String): Rule0 = rule {
    str(s) ~ zeroOrMore(' ')
  }

  def digits:Rule1[Int] = rule {
    capture(oneOrMore(CharPredicate.Digit)) ~> ((str:String) => str.toInt)
  }

  def periodEntry:Rule1[(Int, String)] = rule {
    digits ~ zeroOrMore(' ') ~ capture(oneOrMore(CharPredicate.Alpha)) ~> ((num:Int, str:String) => (num, str))
  }
  def root:Rule1[Period] = rule {
    oneOrMore(zeroOrMore(' ') ~ periodEntry ~ zeroOrMore(' ')) ~ EOI ~> ((values:Seq[(Int, String)]) => values.map({
        case (digit, periodType) => periodType.trim match {
          case "year" => Period(years = digit)
          case "years" => Period(years = digit)

          case "month" => Period(months = digit)
          case "months" => Period(months = digit)

          case "week" => Period(weeks = digit)
          case "weeks" => Period(weeks = digit)

          case "day" => Period(days = digit)
          case "days" => Period(days = digit)

          case "hour" => Period(hours = digit)
          case "hours" => Period(hours = digit)

          case "minute" => Period(minutes = digit)
          case "minutes" => Period(minutes = digit)

          case "second" => Period(seconds = digit)
          case "seconds" => Period(seconds = digit)

          case other => throw new RuntimeException(other + " is not a valid period type")
        }
      }).reduce((left, right) => left.plus(right)))
  }

  def singularPlural(name:String):Rule0 = rule {
    name | (name + "s")
  }
}

case class ParameterNotFound(value:String) extends RuntimeException
case class ParsingError(msg:String) extends RuntimeException

case class Period(seconds:Int = 0, minutes:Int = 0, hours:Int = 0, days:Int = 0, weeks:Int = 0, months:Int = 0, years:Int = 0) extends OpenlawNativeValue {
  def minus(right:Period):Period = Period(seconds - right.seconds, minutes - right.minutes, hours - right.hours, days - right.days, weeks - right.weeks, months - right.months, years - right.years)
  def plus(right:Period):Period = Period(seconds + right.seconds, minutes + right.minutes, hours + right.hours, days + right.days, weeks + right.weeks, months + right.months, years + right.years)
  def divide(right: OpenlawBigDecimal): Period = {
    var totalSeconds = toSeconds / right.underlying.toLongExact

    val divYears = totalSeconds / (365 * 24 * 60 * 60)
    totalSeconds -= divYears * (365 * 24 * 60 * 60)
    val divWeeks = totalSeconds / (7 * 24 * 60 * 60)
    totalSeconds -= divWeeks * (7 * 24 * 60 * 60)
    val divDays = totalSeconds / (24 * 60 * 60)
    totalSeconds -= divDays * (24 * 60 * 60)
    val divHours = totalSeconds / (60 * 60)
    totalSeconds -= divHours * (60 * 60)
    val divMinutes = totalSeconds / 60
    totalSeconds -= divMinutes * 60

    Period(
      years = divYears.toInt,
      weeks = divWeeks.toInt,
      days = divDays.toInt,
      hours = divHours.toInt,
      minutes = divMinutes.toInt,
      seconds = totalSeconds.toInt
    )
  }
  override def toString:String = (if( years > 0 ) s"$years years " else "") +
      ( if( months > 0 ) s"$months months " else "") +
      ( if( weeks > 0 ) s"$weeks weeks " else "") +
      ( if( days > 0 ) s"$days days " else "") +
      ( if( hours > 0 ) s"$hours hours " else "") +
      ( if( minutes > 0 ) s"$minutes minutes " else "") +
      ( if( seconds > 0 ) s"$seconds seconds" else "")

  private def toSeconds: Long = {
    seconds.toLong + 60 * (minutes.toLong + 60 * (hours.toLong + 24 * (days.toLong + 7 * weeks.toLong + 365 * years.toLong)))
  }
}
