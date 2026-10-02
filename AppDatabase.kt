package iq.school.manager.data
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(version = 1, exportSchema = true, views = [EnrollmentIndexRow::class], entities = [
  SchoolEntity::class, UserEntity::class, PermissionEntity::class, StaffEntity::class, TeacherEntity::class, TeacherSubjectEntity::class,
  ClassEntity::class, SectionEntity::class, SubjectEntity::class, StudentEntity::class, EnrollmentEntity::class, AttendanceEntity::class,
  StudentAttendanceEntity::class, AllowanceEntity::class, PromotionEntity::class, TransferEntity::class, GradeEntity::class,
  PrimaryGradeEntity::class, ReportEntity::class, NotificationEntity::class, AuditLogEntity::class, SettingEntity::class, BackupEntity::class, DocumentEntity::class])
abstract class AppDatabase : RoomDatabase() {
  abstract fun school(): SchoolDao; abstract fun users(): UserDao; abstract fun permissions(): PermissionDao
  abstract fun staff(): StaffDao; abstract fun students(): StudentDao; abstract fun classes(): ClassDao; abstract fun enrollment(): EnrollmentDao
  abstract fun attendance(): AttendanceDao; abstract fun studentAttendance(): StudentAttendanceDao
  abstract fun allowances(): AllowanceDao; abstract fun promotions(): PromotionDao; abstract fun transfers(): TransferDao
  abstract fun grades(): GradeDao; abstract fun primaryGrades(): PrimaryGradeDao; abstract fun reports(): ReportDao
  abstract fun notifications(): NotificationDao; abstract fun audit(): AuditDao; abstract fun settings(): SettingDao; abstract fun backups(): BackupDao; abstract fun documents(): DocumentDao
}
