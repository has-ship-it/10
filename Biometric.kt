package iq.school.manager.ui.auth
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometric {
  private const val STRONG = BiometricManager.Authenticators.BIOMETRIC_STRONG
  fun available(ctx: Context) = BiometricManager.from(ctx).canAuthenticate(STRONG) == BiometricManager.BIOMETRIC_SUCCESS
  fun prompt(a: FragmentActivity, title: String, onOk: () -> Unit) {
    val cb = object : BiometricPrompt.AuthenticationCallback() { override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() } }
    BiometricPrompt(a, ContextCompat.getMainExecutor(a), cb).authenticate(
      BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText("إلغاء").setAllowedAuthenticators(STRONG).build())
  }
}
