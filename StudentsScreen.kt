package iq.school.manager.ui.students
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iq.school.manager.data.*
import iq.school.manager.domain.AuditLogger
import iq.school.manager.domain.PdfExporter
import iq.school.manager.domain.ReportData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import iq.school.manager.ui.common.ChipRow
import javax.inject.Inject

@HiltViewModel class StudentsViewModel @Inject constructor(private val dao: StudentDao, private val audit: AuditLogger) : ViewModel() {
  val query = MutableStateFlow("")
  @OptIn(ExperimentalCoroutinesApi::class)
  val students = query.flatMapLatest { dao.search(it.trim()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  fun add(name: String, father: String, guardian: String, phone: String) { viewModelScope.launch {
    val id = dao.insert(StudentEntity(fullName = name.trim(), fatherName = father.ifBlank { null }, guardianName = guardian.ifBlank { null }, phone = phone.ifBlank { null }))
    audit.log("add", "Students", id) } }
  fun delete(id: Long) { viewModelScope.launch { dao.softDelete(id, LocalDate.now().toString()); audit.log("delete", "Students", id) } }
}

@Composable fun StudentsScreen(onOpen: (Long) -> Unit, vm: StudentsViewModel = hiltViewModel()) {
  val list by vm.students.collectAsStateWithLifecycle(); val q by vm.query.collectAsStateWithLifecycle()
  var adding by remember { mutableStateOf(false) }; var toDelete by remember { mutableStateOf<StudentEntity?>(null) }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("سجل الطلاب", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(q, { vm.query.value = it }, Modifier.fillMaxWidth(), placeholder = { Text("ابحث بالاسم") }, singleLine = true)
    if (list.isEmpty()) Text(if (q.isBlank()) "لا يوجد طلاب بعد. اضغط «إضافة طالب» للبدء." else "لا توجد نتائج. جرّب اسماً آخر.")
    LazyColumn(Modifier.weight(1f)) {
      items(list, key = { it.id }) { s ->
        Row(Modifier.fillMaxWidth().clickable { onOpen(s.id) }.padding(vertical = 10.dp)) {
          Column(Modifier.weight(1f)) { Text(s.fullName); Text(s.guardianName ?: "", style = MaterialTheme.typography.bodySmall) }
          TextButton({ toDelete = s }) { Text("حذف") }
        }
        HorizontalDivider()
      }
    }
    Button({ adding = true }, Modifier.fillMaxWidth()) { Text("إضافة طالب") }
  }
  if (adding) AddStudentDialog({ adding = false }) { n, f, g, p -> vm.add(n, f, g, p); adding = false }
  toDelete?.let { s -> AlertDialog({ toDelete = null }, title = { Text("تأكيد الحذف") }, text = { Text("سيُحذف الطالب «${s.fullName}» من السجلات. هل تريد المتابعة؟") },
    confirmButton = { TextButton({ vm.delete(s.id); toDelete = null }) { Text("حذف") } }, dismissButton = { TextButton({ toDelete = null }) { Text("إلغاء") } }) }
}
@Composable private fun AddStudentDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
  var n by remember { mutableStateOf("") }; var f by remember { mutableStateOf("") }; var g by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }
  AlertDialog(onDismiss, title = { Text("إضافة طالب") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    OutlinedTextField(n, { n = it }, label = { Text("اسم الطالب") }, singleLine = true); OutlinedTextField(f, { f = it }, label = { Text("اسم الأب") }, singleLine = true)
    OutlinedTextField(g, { g = it }, label = { Text("ولي الأمر") }, singleLine = true); OutlinedTextField(p, { p = it }, label = { Text("الهاتف") }, singleLine = true) } },
    confirmButton = { TextButton({ onSave(n, f, g, p) }, enabled = n.isNotBlank()) { Text("حفظ") } }, dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class StudentFileViewModel @Inject constructor(state: SavedStateHandle, private val students: StudentDao, private val enrollment: EnrollmentDao,
  school: SchoolDao, classes: ClassDao, private val audit: AuditLogger, @ApplicationContext private val ctx: Context, private val docs: DocumentDao, studentAttendance: StudentAttendanceDao) : ViewModel() {
  private val id: Long = state.get<Long>("id") ?: 0L
  val student = students.byId(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
  val enrollments = enrollment.forStudent(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val year = school.observe().map { it?.currentYear ?: "" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
  val sections = school.observe().flatMapLatest { classes.sectionRows(it?.currentYear ?: "") }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val schoolName = school.observe().map { it?.name ?: "" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
  val documents = docs.forStudent(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val attendance = studentAttendance.counts(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  private fun enc(f: File) = EncryptedFile.Builder(ctx, f, MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
  /** الوثائق تُحفظ مشفرة داخل مجلد التطبيق الخاص (ولا تدخل في النسخة الاحتياطية للقاعدة). */
  fun addDocument(uri: Uri) { viewModelScope.launch(Dispatchers.IO) {
    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "وثيقة"
    val f = File(File(ctx.filesDir, "docs/$id").apply { mkdirs() }, "${System.currentTimeMillis()}.enc")
    ctx.contentResolver.openInputStream(uri)?.use { i -> enc(f).openFileOutput().use { o -> i.copyTo(o) } }
    docs.insert(DocumentEntity(studentId = id, title = name, path = f.path, mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream", addedAt = LocalDateTime.now().toString())); audit.log("add", "StudentDocuments", id) } }
  fun open(d: DocumentEntity, onReady: (File, String) -> Unit) { viewModelScope.launch {
    val out = withContext(Dispatchers.IO) { val dir = File(ctx.cacheDir, "viewer").apply { mkdirs() }; File(dir, "${d.id}-" + d.title.replace(Regex("[^\\p{L}\\p{N}._-]"), "_")).also { o -> enc(File(d.path)).openFileInput().use { i -> o.outputStream().use { i.copyTo(it) } } } }
    onReady(out, d.mime) } }
  fun deleteDocument(d: DocumentEntity) { viewModelScope.launch { withContext(Dispatchers.IO) { File(d.path).delete() }; docs.delete(d); audit.log("delete", "StudentDocuments", id) } }
  fun update(s: StudentEntity) { viewModelScope.launch { students.upsert(s); audit.log("update", "Students", s.id) } }
  fun enroll(sectionId: Long, prev: String) { viewModelScope.launch {
    val no = enrollment.nextNumber().toString()
    val eid = enrollment.insert(EnrollmentEntity(studentId = id, enrollmentNo = no, year = year.value, registeredYear = year.value, sectionId = sectionId, previousSchool = prev.ifBlank { null }))
    audit.log("enroll", "Enrollment", eid) } }
}
@Composable fun StudentFileScreen(vm: StudentFileViewModel = hiltViewModel()) {
  val s by vm.student.collectAsStateWithLifecycle(); val en by vm.enrollments.collectAsStateWithLifecycle()
  val sections by vm.sections.collectAsStateWithLifecycle(); val year by vm.year.collectAsStateWithLifecycle()
  var dialog by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf(false) }
  val ctx = androidx.compose.ui.platform.LocalContext.current; val docs by vm.documents.collectAsStateWithLifecycle(); val att by vm.attendance.collectAsStateWithLifecycle(); var delDoc by remember { mutableStateOf<DocumentEntity?>(null) }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.addDocument(uri) }
  val st = s ?: return
  LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item { Text("الملف المدرسي للطالب", style = MaterialTheme.typography.titleLarge); Text(st.fullName, style = MaterialTheme.typography.titleMedium) }
    item { OutlinedButton({ editing = true }, Modifier.fillMaxWidth()) { Text("تعديل البيانات") } }
    item { val ctx = androidx.compose.ui.platform.LocalContext.current; val school by vm.schoolName.collectAsStateWithLifecycle()
      OutlinedButton({ val rows = listOfNotNull("الاسم" to st.fullName, st.fatherName?.let { "اسم الأب" to it }, st.grandfatherName?.let { "اسم الجد" to it }, st.motherName?.let { "اسم الأم" to it }, st.birthDate?.let { "تاريخ التولد" to it },
          st.birthPlace?.let { "محل التولد" to it }, st.guardianName?.let { "ولي الأمر" to it }, st.phone?.let { "الهاتف" to it }, st.address?.let { "العنوان" to it }) + en.map { "قيد ${it.year}" to "رقم ${it.enrollmentNo} · ${it.status}" } + listOfNotNull(st.notes?.let { "ملاحظات" to it })
        PdfExporter.print(ctx, PdfExporter.write(ctx, ReportData("الملف المدرسي للطالب", school, listOf("البيان", "القيمة"), rows.map { listOf(it.first, it.second) })), "ملف الطالب") }, Modifier.fillMaxWidth()) { Text("طباعة ملف الطالب") } }
    item { Section("المعلومات الشخصية", listOfNotNull(st.fatherName?.let { "الأب: $it" }, st.motherName?.let { "الأم: $it" }, st.birthDate?.let { "التولد: $it" }, st.guardianName?.let { "ولي الأمر: $it" }, st.phone?.let { "الهاتف: $it" })) }
    item { Section("معلومات القيد", en.map { "قيد ${it.enrollmentNo} · ${it.year} · ${it.status}" }) }
    if (en.none { it.year == year }) item { Button({ dialog = true }, Modifier.fillMaxWidth()) { Text("قيد الطالب في صف للسنة $year") } }
    item { Section("الحضور والغياب", att.map { (mapOf("present" to "حضور", "absent" to "غياب", "late" to "تأخر", "leave" to "إجازة")[it.status] ?: it.status) + ": " + it.n }) }
    item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("الوثائق", style = MaterialTheme.typography.titleSmall)
      if (docs.isEmpty()) Text("لا توجد وثائق", style = MaterialTheme.typography.bodySmall) else docs.forEach { d -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("📄 " + d.title, Modifier.weight(1f).clickable { vm.open(d) { f, m -> PdfExporter.viewFile(ctx, f, m) } }.padding(vertical = 6.dp)); TextButton({ delDoc = d }) { Text("حذف") } } }
      OutlinedButton({ picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("إضافة وثيقة") } } } }
    item { Section("الملاحظات", listOfNotNull(st.notes)) }
  }
  delDoc?.let { d -> AlertDialog({ delDoc = null }, title = { Text("تأكيد الحذف") }, text = { Text("ستُحذف الوثيقة «${d.title}» نهائياً. هل تريد المتابعة؟") },
    confirmButton = { TextButton({ vm.deleteDocument(d); delDoc = null }) { Text("حذف") } }, dismissButton = { TextButton({ delDoc = null }) { Text("إلغاء") } }) }
  if (editing) EditStudentDialog(st, { editing = false }) { vm.update(it); editing = false }
  if (dialog) { var sec by remember { mutableStateOf<Long?>(null) }; var prev by remember { mutableStateOf("") }
    AlertDialog({ dialog = false }, title = { Text("قيد الطالب") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      ChipRow(sections.map { it.id to it.label }, sec) { sec = it }; OutlinedTextField(prev, { prev = it }, label = { Text("المدرسة السابقة") }, singleLine = true) } },
      confirmButton = { TextButton({ vm.enroll(sec!!, prev); dialog = false }, enabled = sec != null) { Text("قيد") } }, dismissButton = { TextButton({ dialog = false }) { Text("إلغاء") } }) }
}
@Composable private fun Section(title: String, lines: List<String>) = Card(Modifier.fillMaxWidth()) {
  Column(Modifier.padding(14.dp)) { Text(title, style = MaterialTheme.typography.titleSmall)
    if (lines.isEmpty()) Text("لا توجد بيانات بعد", style = MaterialTheme.typography.bodySmall) else lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } }
}

@Composable private fun EditStudentDialog(s: StudentEntity, onDismiss: () -> Unit, onSave: (StudentEntity) -> Unit) {
  var name by remember { mutableStateOf(s.fullName) }; var father by remember { mutableStateOf(s.fatherName ?: "") }; var grand by remember { mutableStateOf(s.grandfatherName ?: "") }
  var mother by remember { mutableStateOf(s.motherName ?: "") }; var birth by remember { mutableStateOf(s.birthDate ?: "") }; var place by remember { mutableStateOf(s.birthPlace ?: "") }
  var guardian by remember { mutableStateOf(s.guardianName ?: "") }; var phone by remember { mutableStateOf(s.phone ?: "") }; var addr by remember { mutableStateOf(s.address ?: "") }; var notes by remember { mutableStateOf(s.notes ?: "") }
  @Composable fun F(l: String, v: String, set: (String) -> Unit) = OutlinedTextField(v, set, Modifier.fillMaxWidth(), label = { Text(l) }, singleLine = true)
  AlertDialog(onDismiss, title = { Text("تعديل بيانات الطالب") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    F("الاسم", name) { name = it }; F("اسم الأب", father) { father = it }; F("اسم الجد", grand) { grand = it }; F("اسم الأم", mother) { mother = it }; F("تاريخ التولد", birth) { birth = it }
    F("محل التولد", place) { place = it }; F("ولي الأمر", guardian) { guardian = it }; F("الهاتف", phone) { phone = it }; F("العنوان", addr) { addr = it }; F("الملاحظات", notes) { notes = it } } },
    confirmButton = { TextButton({ onSave(s.copy(fullName = name.trim(), fatherName = father.ifBlank { null }, grandfatherName = grand.ifBlank { null }, motherName = mother.ifBlank { null }, birthDate = birth.ifBlank { null },
      birthPlace = place.ifBlank { null }, guardianName = guardian.ifBlank { null }, phone = phone.ifBlank { null }, address = addr.ifBlank { null }, notes = notes.ifBlank { null })) }, enabled = name.isNotBlank()) { Text("حفظ") } },
    dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}
