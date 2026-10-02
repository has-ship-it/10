package iq.school.manager.domain
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import iq.school.manager.data.*
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class Session @Inject constructor() {
  var user by mutableStateOf<UserEntity?>(null)
  val role: String get() = user?.role ?: ""
  @Volatile var lastActive: Long = System.currentTimeMillis()
}
/** السنة الدراسية تبدأ في أيلول. */
fun academicYear(d: LocalDate = LocalDate.now()): String = if (d.monthValue >= 9) "${d.year}/${d.year + 1}" else "${d.year - 1}/${d.year}"

/** تهيئة أول تشغيل: بيانات المدرسة، الصفوف الستة، شعبتان لكل صف، والمواد الأساسية. */
class Seeder @Inject constructor(private val school: SchoolDao, private val classes: ClassDao) {
  suspend fun run(schoolName: String) {
    val year = academicYear()
    school.upsert(SchoolEntity(name = schoolName.ifBlank { "المدرسة" }, currentYear = year))
    listOf("الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس").forEachIndexed { i, n ->
      val cid = (i + 1).toLong()
      classes.upsertClass(ClassEntity(id = cid, name = n, level = i + 1, isPrimarySystem = i < 4))
      listOf("أ", "ب").forEachIndexed { j, s -> classes.upsertSection(SectionEntity(id = cid * 10 + j, classId = cid, name = s, year = year)) }
    }
    listOf("القرآن الكريم", "اللغة العربية", "الرياضيات", "العلوم", "اللغة الإنكليزية", "الاجتماعيات").forEachIndexed { i, n -> classes.upsertSubject(SubjectEntity(id = i + 1L, name = n)) }
  }
}
