package iq.school.manager.domain
/** الأوزان وحد النجاح تأتي من الإعدادات (جدول Settings) وليست ثابتة في الكود. */
data class GradeConfig(val weights: List<Double> = listOf(1.0, 1.0, 2.0, 1.0, 1.0), val passMark: Double = 50.0)
enum class Result { PASS, FOLLOW_UP, INCOMPLETE }
object GradeCalculator {
  /** scores بالترتيب: شهر1، شهر2، نصف السنة، شهر3، شهر4. الخانات الفارغة تُتجاهل. */
  fun average(scores: List<Double?>, cfg: GradeConfig): Double? {
    var sum = 0.0; var w = 0.0
    scores.forEachIndexed { i, s -> if (s != null) { val wi = cfg.weights.getOrElse(i) { 1.0 }; sum += s * wi; w += wi } }
    return if (w == 0.0) null else Math.round(sum / w * 10) / 10.0
  }
  fun result(avg: Double?, cfg: GradeConfig) = when { avg == null -> Result.INCOMPLETE; avg >= cfg.passMark -> Result.PASS; else -> Result.FOLLOW_UP }
}
