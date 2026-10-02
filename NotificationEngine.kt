package iq.school.manager.domain
import iq.school.manager.data.*
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** يولّد تنبيهات تلقائية من بيانات الجداول ويعيد كتابتها عند كل تحديث. */
class NotificationEngine @Inject constructor(private val dao: NotificationDao, private val allowances: AllowanceDao, private val staff: StaffDao,
  private val attendance: AttendanceDao, private val enrollment: EnrollmentDao, private val students: StudentDao) {
  suspend fun refresh() {
    val today = LocalDate.now(); val now = LocalDateTime.now().toString(); dao.clearAuto()
    suspend fun add(type: String, msg: String) = dao.insert(NotificationEntity(type = "auto_$type", message = msg, createdAt = now))
    val due = allowances.dueCount(today.toString()); if (due > 0) add("allow_due", "حان موعد استحقاق العلاوة لـ $due من الموظفين")
    val near = allowances.nearCount(today.plusDays(1).toString(), today.plusDays(60).toString()); if (near > 0) add("allow_near", "اقترب موعد استحقاق العلاوة لـ $near من الموظفين")
    val total = staff.count().first(); val recorded = attendance.forDate(today.toString()).first().size
    if (total > 0 && recorded < total && today.dayOfWeek !in listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) add("att", "لم يُسجَّل حضور ${total - recorded} من المعلمين اليوم")
    val noGrades = enrollment.withoutGradesCount(); if (noGrades > 0) add("grades", "لم تُدخَل درجات $noGrades من الطلاب المقيدين")
    val incomplete = students.incompleteCount(); if (incomplete > 0) add("student", "يوجد نقص في بيانات $incomplete من الطلاب (ولي الأمر أو الهاتف)")
  }
}
