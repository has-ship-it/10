package iq.school.manager.ui.reports
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iq.school.manager.data.*
import iq.school.manager.domain.*
import iq.school.manager.ui.common.ChipRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

enum class ReportType(val label: String) { TEACHER_ATT("تقرير حضور المعلمين"), STAFF("تقرير الملاك"), ALLOW("تقرير العلاوات"), PROMO("تقرير الترفيعات"), TRANSFER("تقرير الترحيل"),
  REGISTER("سجل القيد"), INDEX("فهرس القيد"), GRADES("كشف درجات"), RESULTS("نتائج الطلاب"), STATS("إحصائيات المدرسة") }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class ReportsViewModel @Inject constructor(@ApplicationContext private val ctx: Context, private val school: SchoolDao, private val classes: ClassDao, private val staff: StaffDao,
  private val students: StudentDao, private val attendance: AttendanceDao, private val allowances: AllowanceDao, private val promotions: PromotionDao, private val transfers: TransferDao,
  private val enrollment: EnrollmentDao, private val grades: GradeDao, private val settings: SettingDao, private val reports: ReportDao, private val audit: AuditLogger, private val session: Session) : ViewModel() {
  private fun <T> Flow<T>.keep(i: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), i)
  val sections = school.observe().flatMapLatest { classes.sectionRows(it?.currentYear ?: "") }.keep(emptyList()); val subjects = classes.subjects().keep(emptyList())
  val section = MutableStateFlow<Long?>(null); val subject = MutableStateFlow<Long?>(null)
  var period by mutableStateOf("daily"); var preview by mutableStateOf<ReportData?>(null); var file by mutableStateOf<File?>(null); var busy by mutableStateOf(false); var note by mutableStateOf<String?>(null)

  private suspend fun cfg(): GradeConfig { val w = settings.get("grade_weights")?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }; val p = settings.get("pass_mark")?.toDoubleOrNull()
    return GradeConfig(weights = if (w != null && w.size == 5) w else GradeConfig().weights, passMark = p ?: 50.0) }
  private fun f(x: Double?) = x?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "-"
  private fun statusAr(r: Result) = when (r) { Result.PASS -> "ناجح"; Result.FOLLOW_UP -> "يحتاج متابعة"; Result.INCOMPLETE -> "غير مكتمل" }
  private fun dueAr(d: String?) = when (dueStatus(d?.let { runCatching { LocalDate.parse(it) }.getOrNull() })) { DueStatus.DUE -> "حان الموعد"; DueStatus.NEAR -> "اقترب"; DueStatus.LATER -> "لاحقاً"; null -> "-" }

  private suspend fun build(t: ReportType, name: String): ReportData? {
    val today = LocalDate.now()
    return when (t) {
      ReportType.TEACHER_ATT -> { val from = when (period) { "weekly" -> today.minusDays(6); "monthly" -> today.withDayOfMonth(1); else -> today }
        val rows = attendance.range(from.toString(), today.toString()); val label = mapOf("daily" to "يومي", "weekly" to "أسبوعي", "monthly" to "شهري")[period]
        ReportData("تقرير حضور المعلمين ($label)", name, listOf("المعلم", "حاضر", "غائب", "متأخر", "إجازة"), staff.observeAll().first().map { s -> val r = rows.filter { it.staffId == s.id }
          listOf(s.fullName, "${r.count { it.status == "present" }}", "${r.count { it.status == "absent" }}", "${r.count { it.status == "late" }}", "${r.count { it.status == "leave" }}") }) }
      ReportType.STAFF -> ReportData("تقرير الملاك", name, listOf("الاسم", "العنوان الوظيفي", "الرقم الوظيفي", "الهاتف"), staff.observeAll().first().map { listOf(it.fullName, it.jobTitle ?: "-", it.employeeNo ?: "-", it.phone ?: "-") })
      ReportType.ALLOW -> ReportData("تقرير العلاوات", name, listOf("الاسم", "آخر علاوة", "العلاوة القادمة", "الحالة"), allowances.rows().first().map { listOf(it.staffName, it.lastDate ?: "-", it.nextDate ?: "-", dueAr(it.nextDate)) })
      ReportType.PROMO -> ReportData("تقرير الترفيعات", name, listOf("الاسم", "من درجة", "إلى درجة", "آخر ترفيع", "الترفيع القادم"), promotions.rows().first().map { listOf(it.staffName, it.fromGrade?.toString() ?: "-", it.toGrade?.toString() ?: "-", it.lastDate ?: "-", it.nextDate ?: "-") })
      ReportType.TRANSFER -> ReportData("تقرير الترحيل", name, listOf("الطالب", "إلى مدرسة", "التاريخ", "رقم الكتاب", "السبب"), transfers.rows().first().map { listOf(it.studentName, it.toSchool ?: "-", it.date, it.letterNo ?: "-", it.reason ?: "-") })
      ReportType.REGISTER -> ReportData("سجل القيد", name, listOf("رقم القيد", "الطالب", "الصف", "الشعبة", "السنة", "الحالة"), enrollment.indexByName("").first().map { listOf(it.enrollmentNo, it.fullName, it.className, it.sectionName, it.year, it.status) })
      ReportType.INDEX -> ReportData("فهرس القيد", name, listOf("رقم القيد", "الطالب", "الصف", "الشعبة", "السنة", "الحالة"), enrollment.indexByNumber("").first().map { listOf(it.enrollmentNo, it.fullName, it.className, it.sectionName, it.year, it.status) })
      ReportType.GRADES -> { val sec = section.value; val sub = subject.value; if (sec == null || sub == null) { note = "اختر الشعبة والمادة أولاً"; null } else { val c = cfg(); val g = grades.forSectionSubject(sec, sub).first().associateBy { it.enrollmentId }
        ReportData("كشف درجات — " + sections.value.firstOrNull { it.id == sec }?.label + " — " + subjects.value.firstOrNull { it.id == sub }?.name, name, listOf("الطالب", "ش1", "ش2", "نصف", "ش3", "ش4", "المعدل", "الحالة"),
          enrollment.roster(sec).first().map { r -> val x = g[r.enrollmentId]; val v = listOf(x?.month1, x?.month2, x?.midyear, x?.month3, x?.month4); val avg = GradeCalculator.average(v, c)
            listOf(r.fullName) + v.map { f(it) } + listOf(f(avg), statusAr(GradeCalculator.result(avg, c))) }) } }
      ReportType.RESULTS -> { val sec = section.value; if (sec == null) { note = "اختر الشعبة أولاً"; null } else { val c = cfg(); val all = grades.forSection(sec).first().groupBy { it.enrollmentId }
        ReportData("نتائج الطلاب — " + sections.value.firstOrNull { it.id == sec }?.label, name, listOf("الطالب", "عدد المواد", "المعدل العام", "الحالة"), enrollment.roster(sec).first().map { r ->
          val avgs = all[r.enrollmentId].orEmpty().mapNotNull { GradeCalculator.average(listOf(it.month1, it.month2, it.midyear, it.month3, it.month4), c) }; val overall = if (avgs.isEmpty()) null else Math.round(avgs.average() * 10) / 10.0
          listOf(r.fullName, "${avgs.size}", f(overall), statusAr(GradeCalculator.result(overall, c))) }) } }
      ReportType.STATS -> { val total = staff.count().first(); val rec = attendance.forDate(today.toString()).first().count { it.status == "present" || it.status == "late" }
        ReportData("إحصائيات المدرسة", name, listOf("المؤشر", "القيمة"), listOf(listOf("عدد الطلاب", "${students.count().first()}"), listOf("عدد الكادر", "$total"), listOf("عدد الشعب", "${sections.value.size}"), listOf("حضور المعلمين اليوم", "$rec من $total"))) }
    } }
  fun generate(t: ReportType) { viewModelScope.launch { busy = true; note = null; preview = null
    val d = build(t, school.observe().first()?.name ?: "المدرسة")
    if (d != null) { val out = withContext(Dispatchers.IO) { PdfExporter.write(ctx, d) }; file = out; preview = d
      reports.insert(ReportEntity(type = t.name, createdBy = session.user?.id, createdAt = LocalDateTime.now().toString(), filePath = out.path)); audit.log("report", "Reports", userId = session.user?.id) }
    busy = false } }
}
@Composable fun ReportsScreen(vm: ReportsViewModel = hiltViewModel()) {
  val ctx = LocalContext.current; val sections by vm.sections.collectAsStateWithLifecycle(); val subjects by vm.subjects.collectAsStateWithLifecycle()
  val sec by vm.section.collectAsStateWithLifecycle(); val sub by vm.subject.collectAsStateWithLifecycle()
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("مركز التقارير", style = MaterialTheme.typography.titleLarge)
    Text("للحضور:", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("daily" to "يومي", "weekly" to "أسبوعي", "monthly" to "شهري").forEach { (k, l) -> FilterChip(vm.period == k, { vm.period = k }, { Text(l) }) } }
    Text("لكشف الدرجات والنتائج:", style = MaterialTheme.typography.labelMedium)
    ChipRow(sections.map { it.id to it.label }, sec) { vm.section.value = it }; ChipRow(subjects.map { it.id to it.name }, sub) { vm.subject.value = it }
    vm.note?.let { Text(it, color = MaterialTheme.colorScheme.error) }; if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    LazyColumn(Modifier.weight(1f)) { items(ReportType.values().toList()) { t -> Text("📄 " + t.label, Modifier.fillMaxWidth().clickable(enabled = !vm.busy) { vm.generate(t) }.padding(vertical = 12.dp)); HorizontalDivider() } }
  }
  val d = vm.preview; val file = vm.file
  if (d != null && file != null) AlertDialog({ vm.preview = null }, title = { Text(d.title) }, text = {
    LazyColumn(Modifier.heightIn(max = 360.dp)) { item { Text(d.columns.joinToString(" | "), style = MaterialTheme.typography.titleSmall) }
      items(d.rows.take(40)) { Text(it.joinToString(" | "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp)) }
      if (d.rows.size > 40) item { Text("… و${d.rows.size - 40} صفاً آخر في ملف PDF") } } },
    confirmButton = { Row { TextButton({ PdfExporter.print(ctx, file, d.title) }) { Text("طباعة") }; TextButton({ PdfExporter.share(ctx, file) }) { Text("حفظ / مشاركة PDF") } } },
    dismissButton = { TextButton({ vm.preview = null }) { Text("إغلاق") } })
}
