package iq.school.manager.domain
import org.junit.Assert.*
import org.junit.Test
class GradeCalculatorTest {
  private val cfg = GradeConfig()
  @Test fun weightedAverage() = assertEquals(80.0, GradeCalculator.average(listOf(80.0, 80.0, 80.0, 80.0, 80.0), cfg)!!, 0.01)
  @Test fun skipsEmptyScores() = assertEquals(60.0, GradeCalculator.average(listOf(60.0, null, null, null, null), cfg)!!, 0.01)
  @Test fun passAndFollowUp() { assertEquals(Result.PASS, GradeCalculator.result(50.0, cfg)); assertEquals(Result.FOLLOW_UP, GradeCalculator.result(49.9, cfg)) }
  @Test fun noScoresIsIncomplete() = assertEquals(Result.INCOMPLETE, GradeCalculator.result(GradeCalculator.average(listOf(null, null), cfg), cfg))
}
