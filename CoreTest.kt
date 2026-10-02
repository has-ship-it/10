package iq.school.manager.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class CoreTest {
  private val today = LocalDate.of(2026, 10, 2)
  @Test fun due() = assertEquals(DueStatus.DUE, dueStatus(LocalDate.of(2026, 9, 20), today))
  @Test fun near() = assertEquals(DueStatus.NEAR, dueStatus(LocalDate.of(2026, 11, 15), today))
  @Test fun later() = assertEquals(DueStatus.LATER, dueStatus(LocalDate.of(2027, 3, 1), today))
  @Test fun noDate() = assertNull(dueStatus(null, today))
  @Test fun hashing() { val s = PasswordHasher.newSalt(); val h = PasswordHasher.hash("abc123", s); assertTrue(PasswordHasher.verify("abc123", s, h)); assertFalse(PasswordHasher.verify("x", s, h)) }
  @Test fun roles() { assertTrue(Access.canAccess("principal", "staff")); assertFalse(Access.canAccess("teacher", "staff")); assertTrue(Access.canAccess("teacher", "grades")) }
}
