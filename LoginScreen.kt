package iq.school.manager.ui.auth
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iq.school.manager.data.*
import iq.school.manager.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel class AuthViewModel @Inject constructor(private val settings: SettingDao, private val users: UserDao, private val session: Session, private val seeder: Seeder, private val audit: AuditLogger) : ViewModel() {
  var firstRun by mutableStateOf<Boolean?>(null); var error by mutableStateOf<String?>(null)
  var biometricUser by mutableStateOf<Long?>(null); var pinUser by mutableStateOf<Long?>(null); var pinFails by mutableStateOf(0)
  init { viewModelScope.launch { firstRun = users.count() == 0; biometricUser = settings.get("biometric_user_id")?.toLongOrNull(); pinUser = settings.get("pin_user_id")?.toLongOrNull() } }
  /** بعد 5 محاولات خاطئة يُعطَّل الـ PIN حتى الدخول بكلمة المرور. */
  fun pinLogin(pin: String, onOk: () -> Unit) { viewModelScope.launch {
    val u = pinUser?.let { users.byId(it) }; val parts = u?.pinHash?.split(":")
    val ok = parts != null && parts.size == 2 && withContext(Dispatchers.Default) { PasswordHasher.verify(pin, parts[0], parts[1]) }
    if (u != null && ok) { session.user = u; audit.log("login", "Users", u.id, u.id); onOk() } else { pinFails++; error = if (pinFails >= 5) "تجاوزت عدد المحاولات، استخدم كلمة المرور" else "رمز PIN غير صحيح" } } }
  fun biometricLogin(onOk: () -> Unit) { viewModelScope.launch { val u = biometricUser?.let { users.byId(it) } ?: return@launch; session.user = u; audit.log("login", "Users", u.id, u.id); onOk() } }
  fun submit(username: String, password: String, schoolName: String, onOk: () -> Unit) { viewModelScope.launch {
    error = null; val name = username.trim()
    if (firstRun == true) {
      if (password.length < 6) { error = "كلمة المرور 6 أحرف على الأقل"; return@launch }
      val salt = PasswordHasher.newSalt(); val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(password, salt) }
      users.upsert(UserEntity(username = name, passwordHash = hash, salt = salt, role = "principal")); seeder.run(schoolName)
    }
    val u = users.byUsername(name)
    val ok = u != null && withContext(Dispatchers.Default) { PasswordHasher.verify(password, u.salt, u.passwordHash) }
    if (u != null && ok) { users.upsert(u.copy(lastLogin = LocalDateTime.now().toString())); session.user = u; audit.log("login", "Users", u.id, u.id); onOk() }
    else error = "اسم المستخدم أو كلمة المرور غير صحيحة" } }
}
@Composable fun LoginScreen(onDone: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
  var u by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }; var s by remember { mutableStateOf("") }
  val first = vm.firstRun == true; val ctx = LocalContext.current
  Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.CenterVertically)) {
    Text("مرحباً بك في إدارة مدرستي", style = MaterialTheme.typography.headlineSmall)
    if (first) { Text("أنشئ حساب المدير لأول مرة"); OutlinedTextField(s, { s = it }, Modifier.fillMaxWidth(), label = { Text("اسم المدرسة") }, singleLine = true) }
    OutlinedTextField(u, { u = it }, Modifier.fillMaxWidth(), label = { Text("اسم المستخدم / رقم المدير") }, singleLine = true)
    OutlinedTextField(p, { p = it }, Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button({ vm.submit(u, p, s, onDone) }, Modifier.fillMaxWidth(), enabled = vm.firstRun != null && u.isNotBlank() && p.isNotBlank()) { Text(if (first) "إنشاء الحساب والدخول" else "تسجيل الدخول") }
    if (!first && vm.pinUser != null && vm.pinFails < 5) { var pin by remember { mutableStateOf("") }
      OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(6) }, Modifier.fillMaxWidth(), label = { Text("رمز PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
      OutlinedButton({ vm.pinLogin(pin, onDone) }, Modifier.fillMaxWidth(), enabled = pin.length >= 4) { Text("الدخول برمز PIN") } }
    if (!first && vm.biometricUser != null && Biometric.available(ctx)) OutlinedButton({ Biometric.prompt(ctx as FragmentActivity, "الدخول بالبصمة") { vm.biometricLogin(onDone) } }, Modifier.fillMaxWidth()) { Text("الدخول بالبصمة") }
  }
}
