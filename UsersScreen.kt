package iq.school.manager.ui.users
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iq.school.manager.data.*
import iq.school.manager.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

val roleAr = mapOf("principal" to "المدير", "assistant" to "المعاون", "clerk" to "الموظف الإداري", "teacher" to "المدرس")

@HiltViewModel class UsersViewModel @Inject constructor(private val users: UserDao, private val perms: PermissionDao, classes: ClassDao, private val audit: AuditLogger, val session: Session) : ViewModel() {
  val list = users.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val subjects = classes.subjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  var message by mutableStateOf<String?>(null)
  private suspend fun hashed(pw: String): Pair<String, String> { val salt = PasswordHasher.newSalt(); return salt to withContext(Dispatchers.Default) { PasswordHasher.hash(pw, salt) } }
  fun add(username: String, password: String, role: String, subjectIds: Set<Long>) { viewModelScope.launch {
    if (username.isBlank() || password.length < 6) { message = "اسم المستخدم مطلوب وكلمة المرور 6 أحرف على الأقل"; return@launch }
    val (salt, hash) = hashed(password)
    val id = runCatching { users.insert(UserEntity(username = username.trim(), passwordHash = hash, salt = salt, role = role)) }.getOrNull()
    if (id == null) { message = "اسم المستخدم مستخدم مسبقاً"; return@launch }
    if (role == "teacher") subjectIds.forEach { perms.upsert(PermissionEntity(userId = id, module = "grades", scopeSubjectId = it, canView = true, canEdit = true)) }
    audit.log("add", "Users", id, session.user?.id); message = "تمت إضافة المستخدم" } }
  fun setActive(u: UserEntity, on: Boolean) { viewModelScope.launch { users.upsert(u.copy(isActive = on)); audit.log("update", "Users", u.id, session.user?.id) } }
  fun resetPassword(u: UserEntity, pw: String) { viewModelScope.launch {
    if (pw.length < 6) { message = "كلمة المرور 6 أحرف على الأقل"; return@launch }
    val (salt, hash) = hashed(pw); users.upsert(u.copy(passwordHash = hash, salt = salt)); audit.log("update", "Users", u.id, session.user?.id); message = "تم تغيير كلمة المرور" } }
}
@Composable fun UsersScreen(vm: UsersViewModel = hiltViewModel()) {
  val list by vm.list.collectAsStateWithLifecycle(); val subjects by vm.subjects.collectAsStateWithLifecycle()
  var adding by remember { mutableStateOf(false) }; var resetFor by remember { mutableStateOf<UserEntity?>(null) }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("المستخدمون والصلاحيات", style = MaterialTheme.typography.titleLarge)
    Text("المعاون: الحضور والطلاب والدرجات · الموظف الإداري: القيد والملاك والترحيل · المدرس: درجات مواده فقط", style = MaterialTheme.typography.bodySmall)
    vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    LazyColumn(Modifier.weight(1f)) { items(list, key = { it.id }) { u ->
      Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(u.username); Text(roleAr[u.role] ?: u.role, style = MaterialTheme.typography.bodySmall) }
        TextButton({ resetFor = u }) { Text("كلمة المرور") }
        if (u.role != "principal") Switch(u.isActive, { vm.setActive(u, it) }) }
      HorizontalDivider() } }
    Button({ adding = true }, Modifier.fillMaxWidth()) { Text("إضافة مستخدم") }
  }
  if (adding) { var name by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }; var role by remember { mutableStateOf("assistant") }; var picked by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog({ adding = false }, title = { Text("إضافة مستخدم") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      OutlinedTextField(name, { name = it }, label = { Text("اسم المستخدم") }, singleLine = true); OutlinedTextField(pw, { pw = it }, label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("assistant", "clerk", "teacher")) { r -> FilterChip(role == r, { role = r }, { Text(roleAr[r] ?: r) }) } }
      if (role == "teacher") { Text("المواد المسندة:", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(subjects, key = { it.id }) { s -> FilterChip(s.id in picked, { picked = if (s.id in picked) picked - s.id else picked + s.id }, { Text(s.name) }) } } } } },
      confirmButton = { TextButton({ vm.add(name, pw, role, picked); adding = false }, enabled = name.isNotBlank() && pw.length >= 6) { Text("إضافة") } }, dismissButton = { TextButton({ adding = false }) { Text("إلغاء") } }) }
  resetFor?.let { u -> var pw by remember { mutableStateOf("") }
    AlertDialog({ resetFor = null }, title = { Text("كلمة مرور جديدة: ${u.username}") }, text = { OutlinedTextField(pw, { pw = it }, label = { Text("كلمة المرور (6 أحرف فأكثر)") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) },
      confirmButton = { TextButton({ vm.resetPassword(u, pw); resetFor = null }, enabled = pw.length >= 6) { Text("حفظ") } }, dismissButton = { TextButton({ resetFor = null }) { Text("إلغاء") } }) }
}
