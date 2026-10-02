package iq.school.manager.ui.notifications
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
import iq.school.manager.data.NotificationDao
import iq.school.manager.domain.NotificationEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class NotificationsViewModel @Inject constructor(dao: NotificationDao, private val engine: NotificationEngine) : ViewModel() {
  val items = dao.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  init { viewModelScope.launch { engine.refresh() } }
}
@Composable fun NotificationsScreen(vm: NotificationsViewModel = hiltViewModel()) {
  val list by vm.items.collectAsStateWithLifecycle()
  Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("مركز الإشعارات", style = MaterialTheme.typography.titleLarge)
    if (list.isEmpty()) Text("لا توجد تنبيهات. كل شيء على ما يرام.", Modifier.padding(top = 16.dp))
    LazyColumn { items(list, key = { it.id }) { Text("🔔 " + it.message, Modifier.padding(vertical = 10.dp)); HorizontalDivider() } }
  }
}
