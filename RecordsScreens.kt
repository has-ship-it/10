package iq.school.manager.ui.records
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iq.school.manager.data.*
import iq.school.manager.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

// ───────── فهرس القيد ─────────
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class EnrollmentIndexViewModel @Inject constructor(dao: EnrollmentDao) : ViewModel() {
  val query = MutableStateFlow(""); val byNumber = MutableStateFlow(false)
  val rows = combine(query, byNumber) { q, n -> q.trim() to n }.flatMapLatest { (q, n) -> if (n) dao.indexByNumber(q) else dao.indexByName(q) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
@Composable fun EnrollmentIndexScreen(vm: EnrollmentIndexViewModel = hiltViewModel()) {
  val rows by vm.rows.collectAsStateWithLifecycle(); val q by vm.query.collectAsStateWithLifecycle(); val byNo by vm.byNumber.collectAsStateWithLifecycle()
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("فهرس القيد", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(q, { vm.query.value = it }, Modifier.fillMaxWidth(), placeholder = { Text("اسم الطالب أو رقم القيد") }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(!byNo, { vm.byNumber.value = false }, { Text("الفرز بالاسم") }); FilterChip(byNo, { vm.byNumber.value = true }, { Text("الفرز برقم القيد") }) }
    if (rows.isEmpty()) Text("لا توجد سجلات قيد مطابقة.")
    LazyColumn { items(rows, key = { it.enrollmentNo }) { r -> Column(Modifier.padding(vertical = 8.dp)) { Text("${r.enrollmentNo} · ${r.fullName}"); Text("${r.className} - ${r.sectionName} · ${r.year} · ${r.status}", style = MaterialTheme.typography.bodySmall) }; HorizontalDivider() } }
  }
}

// ───────── سجل الترحيل ─────────
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class TransfersViewModel @Inject constructor(private val dao: TransferDao, students: StudentDao, private val enrollment: EnrollmentDao, school: SchoolDao, private val audit: AuditLogger) : ViewModel() {
  val rows = dao.rows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val query = MutableStateFlow(""); val results = query.flatMapLatest { if (it.isBlank()) flowOf(emptyList()) else students.search(it.trim()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val schoolName = school.observe().map { it?.name ?: "" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
  fun add(studentId: Long, to: String, reason: String, letter: String) { viewModelScope.launch {
    val id = dao.insert(TransferEntity(studentId = studentId, fromSchool = schoolName.value, toSchool = to.ifBlank { null }, reason = reason.ifBlank { null }, date = LocalDate.now().toString(), letterNo = letter.ifBlank { null }))
    enrollment.markTransferred(studentId); audit.log("transfer", "Transfers", id) } }
}
@Composable fun TransfersScreen(vm: TransfersViewModel = hiltViewModel()) {
  val rows by vm.rows.collectAsStateWithLifecycle(); var adding by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("سجل الترحيل", style = MaterialTheme.typography.titleLarge)
    if (rows.isEmpty()) Text("لا توجد عمليات ترحيل بعد.")
    LazyColumn(Modifier.weight(1f)) { items(rows) { r -> Column(Modifier.padding(vertical = 8.dp)) { Text(r.studentName); Text("إلى ${r.toSchool ?: "-"} · ${r.date} · كتاب ${r.letterNo ?: "-"}", style = MaterialTheme.typography.bodySmall); r.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }; HorizontalDivider() } }
    Button({ adding = true }, Modifier.fillMaxWidth()) { Text("إضافة ترحيل") }
  }
  if (adding) {
    val q by vm.query.collectAsStateWithLifecycle(); val found by vm.results.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<StudentEntity?>(null) }; var to by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }; var letter by remember { mutableStateOf("") }
    AlertDialog({ adding = false; vm.query.value = "" }, title = { Text("إضافة ترحيل") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (picked == null) { OutlinedTextField(q, { vm.query.value = it }, label = { Text("ابحث عن الطالب") }, singleLine = true); found.take(5).forEach { s -> Text(s.fullName, Modifier.fillMaxWidth().clickable { picked = s }.padding(8.dp)) } }
      else { Text(picked!!.fullName, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(to, { to = it }, label = { Text("المدرسة الجديدة") }, singleLine = true); OutlinedTextField(reason, { reason = it }, label = { Text("سبب الترحيل") }, singleLine = true); OutlinedTextField(letter, { letter = it }, label = { Text("رقم الكتاب") }, singleLine = true)
        Text("سيتغير وضع قيد الطالب إلى «مرحَّل».", style = MaterialTheme.typography.bodySmall) } } },
      confirmButton = { TextButton({ vm.add(picked!!.id, to, reason, letter); adding = false; vm.query.value = "" }, enabled = picked != null) { Text("حفظ") } },
      dismissButton = { TextButton({ adding = false; vm.query.value = "" }) { Text("إلغاء") } })
  }
}

// ───────── الإعدادات والنسخ الاحتياطي ─────────
@HiltViewModel class SettingsViewModel @Inject constructor(@ApplicationContext private val ctx: Context, private val db: AppDatabase, private val settings: SettingDao,
  private val school: SchoolDao, private val backups: BackupDao, private val users: UserDao, val session: Session, private val audit: AuditLogger) : ViewModel() {
  var weights by mutableStateOf("1,1,2,1,1"); var pass by mutableStateOf("50"); var pin by mutableStateOf(""); var autoLogout by mutableStateOf("0"); var hasPin by mutableStateOf(false); var name by mutableStateOf(""); var message by mutableStateOf<String?>(null); var bio by mutableStateOf(false)
  val list = backups.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  init { viewModelScope.launch { settings.get("grade_weights")?.let { weights = it }; settings.get("pass_mark")?.let { pass = it }; settings.get("auto_logout_min")?.let { autoLogout = it }; hasPin = settings.get("pin_user_id") != null; name = school.observe().first()?.name ?: ""; bio = settings.get("biometric_user_id") != null } }
  fun save() { viewModelScope.launch {
    val w = weights.split(",").mapNotNull { it.trim().toDoubleOrNull() }
    if (w.size != 5 || w.any { it <= 0 }) { message = "أدخل 5 أوزان موجبة مفصولة بفواصل"; return@launch }
    val p = pass.toDoubleOrNull(); if (p == null || p !in 0.0..100.0) { message = "حد النجاح يجب أن يكون بين 0 و100"; return@launch }
    val al = autoLogout.toIntOrNull(); if (al == null || al !in 0..240) { message = "مدة الخروج التلقائي بين 0 و240 دقيقة (0 = معطّل)"; return@launch }
    settings.put(SettingEntity("grade_weights", weights)); settings.put(SettingEntity("pass_mark", pass)); settings.put(SettingEntity("auto_logout_min", al.toString()))
    school.observe().first()?.let { school.upsert(it.copy(name = name)) }; audit.log("settings", "Settings"); message = "تم حفظ الإعدادات" } }
  fun setBio(on: Boolean) { viewModelScope.launch { val u = session.user ?: return@launch
    if (on) settings.put(SettingEntity("biometric_user_id", u.id.toString())) else settings.remove("biometric_user_id"); bio = on } }
  fun savePin() { viewModelScope.launch { val u = session.user ?: return@launch
    if (pin.length !in 4..6 || pin.any { !it.isDigit() }) { message = "رمز PIN من 4 إلى 6 أرقام"; return@launch }
    val salt = PasswordHasher.newSalt(); val h = withContext(Dispatchers.Default) { PasswordHasher.hash(pin, salt) }
    users.upsert(u.copy(pinHash = "$salt:$h")); settings.put(SettingEntity("pin_user_id", u.id.toString())); pin = ""; hasPin = true; message = "تم تعيين رمز PIN" } }
  fun removePin() { viewModelScope.launch { val u = session.user ?: return@launch; users.upsert(u.copy(pinHash = null)); settings.remove("pin_user_id"); hasPin = false; message = "تم حذف رمز PIN" } }
  private fun checkpoint() { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close() }
  private fun stamp() = LocalDateTime.now().toString().replace(':', '-')
  /** القاعدة مشفرة أصلاً، فالنسخة المنسوخة مشفرة بنفس المفتاح (ولا تُفتح إلا على هذا الجهاز). */
  fun backup() { viewModelScope.launch(Dispatchers.IO) {
    checkpoint(); val dir = File(ctx.filesDir, "backups").apply { mkdirs() }; val f = File(dir, "school-${stamp()}.db")
    ctx.getDatabasePath("school.db").copyTo(f, true)
    backups.insert(BackupEntity(path = f.path, location = "device", sizeBytes = f.length(), createdAt = LocalDateTime.now().toString())); audit.log("backup", "Backups")
    message = "تم إنشاء نسخة احتياطية مشفرة على الجهاز" } }
  /** يحفظ نسخة من البيانات الحالية أولاً، ثم يستبدل القاعدة ويعيد تشغيل التطبيق. */
  fun restore(b: BackupEntity) { viewModelScope.launch(Dispatchers.IO) {
    val src = File(b.path); if (!src.exists()) { message = "ملف النسخة غير موجود"; return@launch }
    checkpoint(); val dbf = ctx.getDatabasePath("school.db"); val dir = File(ctx.filesDir, "backups").apply { mkdirs() }
    dbf.copyTo(File(dir, "pre-restore-${stamp()}.db"), true)
    db.close(); File(dbf.path + "-wal").delete(); File(dbf.path + "-shm").delete(); src.copyTo(dbf, true)
    ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
    android.os.Process.killProcess(android.os.Process.myPid()) } }
}
@Composable fun SettingsScreen(onLogout: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
  val ctx = androidx.compose.ui.platform.LocalContext.current; val backups by vm.list.collectAsStateWithLifecycle(); var toRestore by remember { mutableStateOf<BackupEntity?>(null) }
  LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item { Text("الإعدادات", style = MaterialTheme.typography.titleLarge) }
    item { OutlinedTextField(vm.name, { vm.name = it }, Modifier.fillMaxWidth(), label = { Text("اسم المدرسة") }, singleLine = true) }
    item { OutlinedTextField(vm.weights, { vm.weights = it }, Modifier.fillMaxWidth(), label = { Text("أوزان الدرجات (ش1، ش2، نصف، ش3، ش4)") }, singleLine = true) }
    item { OutlinedTextField(vm.pass, { vm.pass = it }, Modifier.fillMaxWidth(), label = { Text("حد النجاح") }, singleLine = true) }
    item { OutlinedTextField(vm.autoLogout, { vm.autoLogout = it }, Modifier.fillMaxWidth(), label = { Text("الخروج التلقائي بعد (دقائق، 0 = معطّل)") }, singleLine = true) }
    item { Text("رمز PIN للدخول السريع (4 إلى 6 أرقام)", style = MaterialTheme.typography.titleSmall) }
    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      OutlinedTextField(vm.pin, { vm.pin = it.filter { c -> c.isDigit() }.take(6) }, Modifier.weight(1f), label = { Text(if (vm.hasPin) "رمز جديد" else "رمز PIN") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
      Button(vm::savePin) { Text("حفظ") }; if (vm.hasPin) TextButton(vm::removePin) { Text("حذف") } } }
    item { Button(vm::save, Modifier.fillMaxWidth()) { Text("حفظ الإعدادات") } }
    if (iq.school.manager.ui.auth.Biometric.available(ctx)) item { Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("الدخول بالبصمة", Modifier.weight(1f)); Switch(vm.bio, { vm.setBio(it) }) } }
    item { Text("النسخ الاحتياطي", style = MaterialTheme.typography.titleMedium) }
    item { OutlinedButton(vm::backup, Modifier.fillMaxWidth()) { Text("إنشاء نسخة احتياطية") } }
    items(backups, key = { it.id }) { b -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Text("${b.createdAt.take(16).replace('T', ' ')} · ${(b.sizeBytes ?: 0) / 1024} KB", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
      TextButton({ PdfExporter.shareFile(ctx, File(b.path), "application/octet-stream") }) { Text("مشاركة") }; TextButton({ toRestore = b }) { Text("استعادة") } } }
    vm.message?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.primary) } }
    item { TextButton({ vm.session.user = null; onLogout() }) { Text("تسجيل الخروج") } }
  }
  toRestore?.let { b -> AlertDialog({ toRestore = null }, title = { Text("تأكيد الاستعادة") },
    text = { Text("ستُستبدل جميع البيانات الحالية بهذه النسخة. تُحفظ نسخة من بياناتك الحالية قبل ذلك، ويُعاد تشغيل التطبيق. هل تريد المتابعة؟") },
    confirmButton = { TextButton({ vm.restore(b); toRestore = null }) { Text("استعادة") } }, dismissButton = { TextButton({ toRestore = null }) { Text("إلغاء") } }) }
}
