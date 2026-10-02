-- «إدارة مدرستي» — مخطط قاعدة البيانات المحلية (SQLite / Drift / Room)
-- التواريخ نصوص ISO (YYYY-MM-DD). الحذف منطقي (deleted_at) للحفاظ على السجلات.
PRAGMA foreign_keys = ON;

CREATE TABLE Schools (
  id INTEGER PRIMARY KEY, name TEXT NOT NULL, governorate TEXT, district TEXT, subdistrict TEXT,
  stage TEXT, principal_name TEXT, logo BLOB,
  current_year TEXT NOT NULL            -- مثال: 2026/2027
);

CREATE TABLE Users (
  id INTEGER PRIMARY KEY, school_id INTEGER NOT NULL REFERENCES Schools(id),
  username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, salt TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('principal','assistant','clerk','teacher')),
  staff_id INTEGER REFERENCES Staff(id), pin_hash TEXT, biometric_enabled INTEGER DEFAULT 0,
  auto_logout_min INTEGER, is_active INTEGER DEFAULT 1, last_login TEXT
);
CREATE TABLE Permissions (            -- صلاحيات دقيقة لكل دور/مستخدم
  user_id INTEGER NOT NULL REFERENCES Users(id) ON DELETE CASCADE,
  module TEXT NOT NULL,               -- attendance, students, grades, staff ...
  can_view INTEGER DEFAULT 0, can_edit INTEGER DEFAULT 0, can_delete INTEGER DEFAULT 0,
  scope_subject_id INTEGER REFERENCES Subjects(id),   -- المدرس: مواده فقط
  PRIMARY KEY (user_id, module, scope_subject_id)
);

CREATE TABLE Staff (                  -- ملف وظيفي لكل موظف أو معلم
  id INTEGER PRIMARY KEY, school_id INTEGER NOT NULL REFERENCES Schools(id),
  full_name TEXT NOT NULL, gender TEXT CHECK (gender IN ('M','F')), birth_date TEXT,
  national_id TEXT, employee_no TEXT UNIQUE, job_title TEXT, job_grade INTEGER, job_stage INTEGER,
  certificate TEXT, specialization TEXT, hire_date TEXT, start_date TEXT,
  phone TEXT, status TEXT DEFAULT 'active', notes TEXT, deleted_at TEXT
);
CREATE TABLE Teachers (               -- امتداد للموظف الذي يدرّس
  staff_id INTEGER PRIMARY KEY REFERENCES Staff(id) ON DELETE CASCADE, weekly_periods INTEGER
);
CREATE TABLE TeacherSubjects (
  teacher_id INTEGER REFERENCES Teachers(staff_id), subject_id INTEGER REFERENCES Subjects(id),
  section_id INTEGER REFERENCES Sections(id), PRIMARY KEY (teacher_id, subject_id, section_id)
);

CREATE TABLE Classes (id INTEGER PRIMARY KEY, name TEXT NOT NULL, level INTEGER NOT NULL UNIQUE,   -- 1..6
  is_primary_system INTEGER NOT NULL DEFAULT 0);        -- 1 للصفوف الأول..الرابع
CREATE TABLE Sections (id INTEGER PRIMARY KEY, class_id INTEGER NOT NULL REFERENCES Classes(id),
  name TEXT NOT NULL, year TEXT NOT NULL, UNIQUE (class_id, name, year));
CREATE TABLE Subjects (id INTEGER PRIMARY KEY, name TEXT NOT NULL, class_id INTEGER REFERENCES Classes(id));

CREATE TABLE Students (
  id INTEGER PRIMARY KEY, school_id INTEGER NOT NULL REFERENCES Schools(id),
  full_name TEXT NOT NULL, father_name TEXT, grandfather_name TEXT, mother_name TEXT,
  birth_date TEXT, birth_place TEXT, gender TEXT CHECK (gender IN ('M','F')),
  guardian_name TEXT, phone TEXT, address TEXT, id_doc_no TEXT, notes TEXT, deleted_at TEXT
);

CREATE TABLE Enrollment (             -- سجل القيد: مصدر الحقيقة لرقم القيد والصف والشعبة
  id INTEGER PRIMARY KEY, student_id INTEGER NOT NULL REFERENCES Students(id),
  enrollment_no TEXT NOT NULL, year TEXT NOT NULL, registered_year TEXT,
  section_id INTEGER NOT NULL REFERENCES Sections(id), previous_school TEXT,
  status TEXT NOT NULL DEFAULT 'enrolled' CHECK (status IN ('enrolled','transferred','passed','failed','dropped')),
  UNIQUE (enrollment_no), UNIQUE (student_id, year)
);
-- فهرس القيد = عرض محسوب من Enrollment، فلا تتكرر البيانات
CREATE VIEW EnrollmentIndex AS
  SELECT e.enrollment_no, s.full_name, c.name AS class_name, sec.name AS section_name, e.year, e.status
  FROM Enrollment e JOIN Students s ON s.id=e.student_id
  JOIN Sections sec ON sec.id=e.section_id JOIN Classes c ON c.id=sec.class_id
  WHERE s.deleted_at IS NULL;

CREATE TABLE Attendance (             -- حضور المعلمين اليومي
  id INTEGER PRIMARY KEY, staff_id INTEGER NOT NULL REFERENCES Staff(id), date TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('present','absent','late','leave')),
  check_in TEXT, check_out TEXT, absence_reason TEXT, notes TEXT, UNIQUE (staff_id, date)
);
CREATE TABLE StudentAttendance (      -- اختياري، يُفعَّل من الإعدادات
  id INTEGER PRIMARY KEY, enrollment_id INTEGER NOT NULL REFERENCES Enrollment(id), date TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('present','absent','late','leave')), notes TEXT,
  UNIQUE (enrollment_id, date)
);

CREATE TABLE Allowances (             -- العلاوات
  id INTEGER PRIMARY KEY, staff_id INTEGER NOT NULL REFERENCES Staff(id),
  last_date TEXT, next_date TEXT, order_no TEXT, notes TEXT
);
CREATE TABLE Promotions (             -- الترفيعات
  id INTEGER PRIMARY KEY, staff_id INTEGER NOT NULL REFERENCES Staff(id),
  from_grade INTEGER, to_grade INTEGER, last_date TEXT, next_date TEXT, order_no TEXT, notes TEXT
);
CREATE TABLE Transfers (              -- سجل ترحيل الطلاب
  id INTEGER PRIMARY KEY, student_id INTEGER NOT NULL REFERENCES Students(id),
  enrollment_id INTEGER REFERENCES Enrollment(id), from_school TEXT, to_school TEXT,
  reason TEXT, date TEXT NOT NULL, letter_no TEXT, notes TEXT
);

CREATE TABLE Grades (                 -- الخامس والسادس
  id INTEGER PRIMARY KEY, enrollment_id INTEGER NOT NULL REFERENCES Enrollment(id),
  subject_id INTEGER NOT NULL REFERENCES Subjects(id),
  month1 REAL, month2 REAL, midyear REAL, month3 REAL, month4 REAL, final_exam REAL, notes TEXT,
  UNIQUE (enrollment_id, subject_id)  -- المجموع والمعدل والحالة تُحسب من إعدادات الاحتساب
);
CREATE TABLE PrimaryGrades (          -- الأول إلى الرابع
  id INTEGER PRIMARY KEY, enrollment_id INTEGER NOT NULL REFERENCES Enrollment(id),
  subject_id INTEGER NOT NULL REFERENCES Subjects(id),
  term TEXT NOT NULL, score REAL, evaluation TEXT, notes TEXT,
  UNIQUE (enrollment_id, subject_id, term)
);

CREATE TABLE Reports (id INTEGER PRIMARY KEY, type TEXT NOT NULL, params_json TEXT,
  created_by INTEGER REFERENCES Users(id), created_at TEXT NOT NULL, file_path TEXT);
CREATE TABLE Notifications (id INTEGER PRIMARY KEY, type TEXT NOT NULL, message TEXT NOT NULL,
  ref_table TEXT, ref_id INTEGER, is_read INTEGER DEFAULT 0, created_at TEXT NOT NULL);
CREATE TABLE AuditLogs (id INTEGER PRIMARY KEY, user_id INTEGER REFERENCES Users(id),
  action TEXT NOT NULL, table_name TEXT, record_id INTEGER, details_json TEXT, at TEXT NOT NULL);
CREATE TABLE Settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
  -- أمثلة: grade_weights=[1,1,2,1,1]، pass_mark=50، dark_mode، student_attendance_enabled
CREATE TABLE Backups (id INTEGER PRIMARY KEY, path TEXT NOT NULL, location TEXT CHECK (location IN ('device','drive')),
  encrypted INTEGER DEFAULT 1, size_bytes INTEGER, created_at TEXT NOT NULL);

CREATE INDEX idx_students_name ON Students(full_name);
CREATE INDEX idx_enroll_section ON Enrollment(section_id, year);
CREATE INDEX idx_att_date ON Attendance(date);
CREATE INDEX idx_allow_next ON Allowances(next_date);
CREATE INDEX idx_promo_next ON Promotions(next_date);
CREATE INDEX idx_grades_enroll ON Grades(enrollment_id);
CREATE INDEX idx_audit_at ON AuditLogs(at);
