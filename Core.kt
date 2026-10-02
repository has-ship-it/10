package iq.school.manager.domain
import iq.school.manager.data.AuditDao
import iq.school.manager.data.AuditLogEntity
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

enum class DueStatus { LATER, NEAR, DUE }
/** 🟢 لاحقاً · 🟠 اقترب (خلال nearDays) · 🔴 حان الموعد. */
fun dueStatus(next: LocalDate?, today: LocalDate = LocalDate.now(), nearDays: Long = 60): DueStatus? = when {
  next == null -> null
  !next.isAfter(today) -> DueStatus.DUE
  ChronoUnit.DAYS.between(today, next) <= nearDays -> DueStatus.NEAR
  else -> DueStatus.LATER
}

object PasswordHasher {
  private const val ITER = 120_000
  fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }
  fun hash(password: String, salt: String): String {
    val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITER, 256)
    return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
  }
  fun verify(password: String, salt: String, expected: String) = java.security.MessageDigest.isEqual(hash(password, salt).toByteArray(), expected.toByteArray())
}

object Access {
  /** الأدوار الأساسية؛ تقييد المدرس بمواده يتم عبر جدول Permissions.scopeSubjectId. */
  private val rules = mapOf(
    "assistant" to setOf("attendance", "students", "enrollment", "grades", "reports"),
    "clerk" to setOf("enrollment", "students", "staff", "transfers", "reports"),
    "teacher" to setOf("grades"))
  fun canAccess(role: String, module: String) = role == "principal" || module in (rules[role] ?: emptySet())
  fun canDelete(role: String) = role == "principal"
}

class AuditLogger @Inject constructor(private val dao: AuditDao) {
  suspend fun log(action: String, table: String? = null, recordId: Long? = null, userId: Long? = null) =
    dao.insert(AuditLogEntity(userId = userId, action = action, tableName = table, recordId = recordId, at = LocalDateTime.now().toString()))
}
