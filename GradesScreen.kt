package iq.school.manager.ui.grades
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iq.school.manager.data.*
import iq.school.manager.domain.*
import iq.school.manager.ui.common.ChipRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class GradesViewModel @Inject constructor(private val perms: PermissionDao, private val session: Session, school: SchoolDao, classes: ClassDao, enrollment: EnrollmentDao, private val grades: GradeDao,
  private val pgrades: PrimaryGradeDao, private val settings: SettingDao, private val audit: AuditLogger) : ViewModel() {
  private fun <T> Flow<T>.keep(init: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)
  val sections = school.observe().flatMapLatest { classes.sectionRows(it?.currentYear ?: "") }.keep(emptyList())
  /** المدرس يرى المواد المسندة إليه فقط (جدول Permissions). */
  val subjects = classes.subjects().map { all -> if (session.role != "teacher") all else { val ids = perms.forUser(session.user?.id ?: 0).filter { it.module == "grades" && it.canView }.map { it.scopeSubjectId }.toSet(); all.filter { it.id in ids } } }.keep(emptyList())
  val section = MutableStateFlow<Long?>(null); val subject = MutableStateFlow<Long?>(null); val term = MutableStateFlow("الفصل الأول")
  val roster = section.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else enrollment.roster(s) }.keep(emptyList())
  val saved = combine(section, subject) { a, b -> a to b }.flatMapLatest { (a, b) -> if (a == null || b == null) flowOf(emptyList()) else grades.forSectionSubject(a, b) }.keep(emptyList())
  val savedPrimary = combine(section, subject, term) { a, b, c -> Triple(a, b, c) }.flatMapLatest { (a, b, c) -> if (a == null || b == null) flowOf(emptyList()) else pgrades.forSectionSubjectTerm(a, b, c) }.keep(emptyList())
  var cfg by mutableStateOf(GradeConfig())
  init { viewModelScope.launch {
    val w = settings.get("grade_weights")?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }; val p = settings.get("pass_mark")?.toDoubleOrNull()
    if (w != null && w.size == 5) cfg = cfg.copy(weights = w); if (p != null) cfg = cfg.copy(passMark = p) } }
  fun saveGrades(edits: Map<Long, List<String>>, existing: Map<Long, GradeEntity>) { viewModelScope.launch {
    val sub = subject.value ?: return@launch
    edits.forEach { (eid, v) -> val n = v.map { it.toDoubleOrNull()?.takeIf { x -> x in 0.0..100.0 } }
      grades.upsert((existing[eid] ?: GradeEntity(enrollmentId = eid, subjectId = sub)).copy(month1 = n[0], month2 = n[1], midyear = n[2], month3 = n[3], month4 = n[4])) }
    audit.log("grades", "Grades") } }
  fun saveScores(edits: Map<Long, String>, existing: Map<Long, PrimaryGradeEntity>) { viewModelScope.launch { val sub = subject.value ?: return@launch
    edits.forEach { (eid, v) -> pgrades.upsert((existing[eid] ?: PrimaryGradeEntity(enrollmentId = eid, subjectId = sub, term = term.value)).copy(score = v.toDoubleOrNull()?.takeIf { it in 0.0..100.0 })) }; audit.log("grades", "PrimaryGrades") } }
  fun saveEval(eid: Long, ev: String, existing: PrimaryGradeEntity?) { viewModelScope.launch {
    val sub = subject.value ?: return@launch
    pgrades.upsert((existing ?: PrimaryGradeEntity(enrollmentId = eid, subjectId = sub, term = term.value)).copy(evaluation = ev)) } }
}
private fun fmt(x: Double) = if (x == x.toLong().toDouble()) x.toLong().toString() else x.toString()

@Composable fun GradesScreen(vm: GradesViewModel = hiltViewModel()) {
  val sections by vm.sections.collectAsStateWithLifecycle(); val subjects by vm.subjects.collectAsStateWithLifecycle(); val roster by vm.roster.collectAsStateWithLifecycle()
  val saved by vm.saved.collectAsStateWithLifecycle(); val sec by vm.section.collectAsStateWithLifecycle(); val sub by vm.subject.collectAsStateWithLifecycle()
  val byEnroll = saved.associateBy { it.enrollmentId }; val edits = remember(sec, sub) { mutableStateMapOf<Long, List<String>>() }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("درجات الخامس والسادس", style = MaterialTheme.typography.titleLarge)
    ChipRow(sections.filter { it.level >= 5 }.map { it.id to it.label }, sec) { vm.section.value = it }
    ChipRow(subjects.map { it.id to it.name }, sub) { vm.subject.value = it }
    if (sec == null || sub == null) Text("اختر الشعبة والمادة لعرض الطلاب.") else if (roster.isEmpty()) Text("لا يوجد طلاب مقيدون في هذه الشعبة. قيّد الطلاب من ملف الطالب.")
    LazyColumn(Modifier.weight(1f)) { items(roster, key = { it.enrollmentId }) { r ->
      val g = byEnroll[r.enrollmentId]
      val v = edits[r.enrollmentId] ?: listOf(g?.month1, g?.month2, g?.midyear, g?.month3, g?.month4).map { it?.let(::fmt) ?: "" }
      val avg = GradeCalculator.average(v.map { it.toDoubleOrNull() }, vm.cfg); val res = GradeCalculator.result(avg, vm.cfg)
      Column(Modifier.padding(vertical = 6.dp)) {
        Row { Text(r.fullName, Modifier.weight(1f)); Text("${avg ?: "-"} · " + when (res) { Result.PASS -> "ناجح"; Result.FOLLOW_UP -> "يحتاج متابعة"; Result.INCOMPLETE -> "غير مكتمل" }) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { v.forEachIndexed { i, t ->
          OutlinedTextField(t, { nt -> edits[r.enrollmentId] = v.toMutableList().also { l -> l[i] = nt } }, Modifier.weight(1f), singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp), placeholder = { Text(listOf("ش1", "ش2", "نصف", "ش3", "ش4")[i], fontSize = 11.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) } }
      }; HorizontalDivider() } }
    Button({ vm.saveGrades(edits.toMap(), byEnroll) }, Modifier.fillMaxWidth(), enabled = edits.isNotEmpty()) { Text("حفظ الدرجات") }
  }
}

@Composable fun PrimaryGradesScreen(vm: GradesViewModel = hiltViewModel()) {
  val sections by vm.sections.collectAsStateWithLifecycle(); val subjects by vm.subjects.collectAsStateWithLifecycle(); val roster by vm.roster.collectAsStateWithLifecycle()
  val saved by vm.savedPrimary.collectAsStateWithLifecycle(); val sec by vm.section.collectAsStateWithLifecycle(); val sub by vm.subject.collectAsStateWithLifecycle(); val term by vm.term.collectAsStateWithLifecycle()
  val byEnroll = saved.associateBy { it.enrollmentId }; val evals = listOf("ممتاز", "جيد", "متوسط", "يحتاج دعم"); val scores = remember(sec, sub, term) { mutableStateMapOf<Long, String>() }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("درجات الصفوف الأولية", style = MaterialTheme.typography.titleLarge)
    ChipRow(sections.filter { it.level <= 4 }.map { it.id to it.label }, sec) { vm.section.value = it }
    ChipRow(subjects.map { it.id to it.name }, sub) { vm.subject.value = it }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("الفصل الأول", "الفصل الثاني").forEach { t -> FilterChip(term == t, { vm.term.value = t }, { Text(t) }) } }
    if (sec == null || sub == null) Text("اختر الصف والمادة لعرض الطلاب.") else if (roster.isEmpty()) Text("لا يوجد طلاب مقيدون في هذه الشعبة.")
    LazyColumn(Modifier.weight(1f)) { items(roster, key = { it.enrollmentId }) { r -> val g = byEnroll[r.enrollmentId]
      Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(r.fullName, Modifier.weight(1f))
          OutlinedTextField(scores[r.enrollmentId] ?: g?.score?.let(::fmt) ?: "", { scores[r.enrollmentId] = it }, Modifier.width(96.dp), singleLine = true, placeholder = { Text("الدرجة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { evals.forEach { e -> FilterChip(g?.evaluation == e, { vm.saveEval(r.enrollmentId, e, g) }, { Text(e) }) } } }
      HorizontalDivider() } }
    Button({ vm.saveScores(scores.toMap(), byEnroll) }, Modifier.fillMaxWidth(), enabled = scores.isNotEmpty()) { Text("حفظ الدرجات") }
  }
}
