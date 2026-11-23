package il.mosad.grades;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * מסד נתונים מקומי (SQLite) - בלי רשת.
 * תלמיד מזוהה לפי ת.ז. (tz). מזהה המערכת (sid) נשמר כשהוא מגיע מקובץ JSON.
 */
public class Db extends SQLiteOpenHelper {

    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) instance = new Db(ctx.getApplicationContext());
        return instance;
    }

    private Db(Context ctx) {
        super(ctx, "mosad_grades.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE students (tz TEXT PRIMARY KEY, sid INTEGER, firstName TEXT, lastName TEXT, className TEXT, status TEXT, details TEXT)");
        db.execSQL("CREATE TABLE exams (id INTEGER PRIMARY KEY, className TEXT, subject TEXT, title TEXT, date TEXT)");
        db.execSQL("CREATE TABLE scores (examId INTEGER, tz TEXT, score REAL, note TEXT, PRIMARY KEY (examId, tz))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    /** תלמיד פעיל (לא "עזב") - לתצוגה ולהזנה. */
    private static final String ACTIVE = "(status IS NULL OR status NOT LIKE 'עזב%')";

    public List<String> classNames() {
        List<String> out = new ArrayList<String>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT className FROM students WHERE className != '' AND " + ACTIVE + " ORDER BY className", null);
        try {
            while (c.moveToNext()) out.add(c.getString(0));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Exam> examsFor(String cls) {
        List<Exam> out = new ArrayList<Exam>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, subject, title, date FROM exams WHERE className = ? ORDER BY date DESC, id DESC", new String[]{cls});
        try {
            while (c.moveToNext()) {
                Exam e = new Exam();
                e.id = c.getLong(0);
                e.className = cls;
                e.subject = c.getString(1);
                e.title = c.getString(2);
                e.date = c.getString(3);
                out.add(e);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public Exam exam(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT className, subject, title, date FROM exams WHERE id = ?", new String[]{String.valueOf(id)});
        try {
            if (!c.moveToFirst()) return null;
            Exam e = new Exam();
            e.id = id;
            e.className = c.getString(0);
            e.subject = c.getString(1);
            e.title = c.getString(2);
            e.date = c.getString(3);
            return e;
        } finally {
            c.close();
        }
    }

    /** תלמידי הכיתה הפעילים, עם הציון וההערה של המבחן (אם נרשמו). */
    public List<Row> rowsFor(long examId, String cls) {
        List<Row> out = new ArrayList<Row>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.tz, s.firstName, s.lastName, sc.score, sc.note FROM students s " +
                        "LEFT JOIN scores sc ON sc.tz = s.tz AND sc.examId = ? " +
                        "WHERE s.className = ? AND " + ACTIVE + " ORDER BY s.lastName, s.firstName",
                new String[]{String.valueOf(examId), cls});
        try {
            while (c.moveToNext()) {
                Row r = new Row();
                r.tz = c.getString(0);
                r.name = (nz(c.getString(1)) + " " + nz(c.getString(2))).trim();
                r.score = c.isNull(3) ? null : Double.valueOf(c.getDouble(3));
                r.note = c.isNull(4) ? "" : c.getString(4);
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /**
     * שמירת ציון והערה מהמסך. ציון ריק = בלי ציון. ציון מחוץ ל-0-100 נדחה.
     * @return הודעת שגיאה, או null אם נשמר
     */
    public String save(long examId, String tz, String scoreText, String note) {
        String t = scoreText == null ? "" : scoreText.trim().replace(',', '.');
        Double score = null;
        if (t.length() > 0) {
            try {
                score = Double.valueOf(t);
            } catch (NumberFormatException ex) {
                return "ציון לא תקין: " + scoreText;
            }
            if (score < 0 || score > 100) return "הציון חייב להיות בין 0 ל-100";
        }
        String n = note == null ? "" : note.trim();
        if (n.length() > 300) n = n.substring(0, 300);
        if (score == null && n.length() == 0) {
            getWritableDatabase().delete("scores", "examId = ? AND tz = ?", new String[]{String.valueOf(examId), tz});
        } else {
            writeScore(examId, tz, score, n);
        }
        return null;
    }

    public void writeScore(long examId, String tz, Double score, String note) {
        ContentValues v = new ContentValues();
        v.put("examId", examId);
        v.put("tz", tz);
        if (score == null) v.putNull("score"); else v.put("score", score.doubleValue());
        v.put("note", note);
        getWritableDatabase().insertWithOnConflict("scores", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Row existing(long examId, String tz) {
        Cursor c = getReadableDatabase().rawQuery("SELECT score, note FROM scores WHERE examId = ? AND tz = ?",
                new String[]{String.valueOf(examId), tz});
        try {
            if (!c.moveToFirst()) return null;
            Row r = new Row();
            r.score = c.isNull(0) ? null : Double.valueOf(c.getDouble(0));
            r.note = c.isNull(1) ? "" : c.getString(1);
            return r;
        } finally {
            c.close();
        }
    }

    /** הוספה או עדכון תלמיד לפי ת.ז. שדות ריקים לא דורסים ערכים קיימים. */
    public void upsertStudent(String tz, Long sid, String first, String last, String cls, String status) {
        ContentValues v = new ContentValues();
        v.put("tz", tz);
        if (sid != null) v.put("sid", sid.longValue());
        if (first != null && first.length() > 0) v.put("firstName", first);
        if (last != null && last.length() > 0) v.put("lastName", last);
        if (cls != null && cls.length() > 0) v.put("className", cls);
        if (status != null && status.length() > 0) v.put("status", status);
        int n = getWritableDatabase().update("students", v, "tz = ?", new String[]{tz});
        if (n == 0) getWritableDatabase().insert("students", null, v);
    }

    /** שומר את כל העמודות שיובאו מרשימת התלמידים, כמערך של זוגות [שם עמודה, ערך]. */
    public void setDetails(String tz, String json) {
        ContentValues v = new ContentValues();
        v.put("details", json);
        getWritableDatabase().update("students", v, "tz = ?", new String[]{tz});
    }

    /** כל התלמידים, כולל שעזבו, לפי כיתה ושם. */
    public List<Student> allStudents() {
        List<Student> out = new ArrayList<Student>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT tz, firstName, lastName, className, status, details FROM students " +
                        "ORDER BY className, lastName, firstName", null);
        try {
            while (c.moveToNext()) out.add(readStudent(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** תלמיד לפי ת.ז. או null. */
    public Student student(String tz) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT tz, firstName, lastName, className, status, details FROM students WHERE tz = ?",
                new String[]{nz(tz)});
        try {
            return c.moveToFirst() ? readStudent(c) : null;
        } finally {
            c.close();
        }
    }

    private static Student readStudent(Cursor c) {
        Student s = new Student();
        s.tz = nz(c.getString(0));
        s.firstName = nz(c.getString(1));
        s.lastName = nz(c.getString(2));
        s.className = nz(c.getString(3));
        s.status = nz(c.getString(4));
        s.details = c.isNull(5) ? "[]" : c.getString(5);
        return s;
    }

    /** מוסיף שם לתלמיד רק אם אין לו שם בטלפון. */
    public void setNameIfMissing(String tz, String name) {
        if (name == null || name.length() == 0) return;
        getWritableDatabase().execSQL("UPDATE students SET firstName = ? WHERE tz = ? AND " +
                "(firstName IS NULL OR firstName = '') AND (lastName IS NULL OR lastName = '')", new Object[]{name, tz});
    }

    /** תלמיד חדש שנרשם באפליקציה לפי ת.ז. @return הודעת שגיאה או null אם נשמר */
    public String addStudent(String tzText, String first, String last, String cls) {
        String digits = tzText == null ? "" : tzText.replaceAll("\\D", "");
        if (digits.length() == 0 || digits.length() > 9) return "ת.ז. חייבת להכיל עד 9 ספרות";
        String tz = Io.normTz(digits);
        if (first == null || first.trim().length() == 0 || last == null || last.trim().length() == 0)
            return "יש להזין שם פרטי ושם משפחה";
        if (cls == null || cls.trim().length() == 0) return "יש להזין כיתה";
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM students WHERE tz = ?", new String[]{tz});
        boolean exists;
        try {
            exists = c.moveToFirst();
        } finally {
            c.close();
        }
        if (exists) return "כבר קיים תלמיד עם ת.ז. זו";
        ContentValues v = new ContentValues();
        v.put("tz", tz);
        v.put("firstName", first.trim());
        v.put("lastName", last.trim());
        v.put("className", cls.trim());
        v.put("status", "פעיל");
        getWritableDatabase().insert("students", null, v);
        return null;
    }

    /** מבחן חדש שנוצר באפליקציה. מזהה שלילי = עדיין לא נשלח למערכת. */
    public long addExam(String cls, String subject, String title, String date) {
        long id = -System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("className", cls);
        v.put("subject", subject);
        v.put("title", title);
        v.put("date", date);
        getWritableDatabase().insert("exams", null, v);
        return id;
    }

    /** מבחן מקומי (שנוצר באפליקציה) שזהה לפרטים שהגיעו מהמערכת. מחזיר את המזהה המקומי או null. */
    public Long findLocalExam(String cls, String subject, String title, String date) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM exams WHERE id < 0 AND className = ? AND subject = ? AND title = ? AND date = ?",
                new String[]{nz(cls), nz(subject), nz(title), nz(date)});
        try {
            return c.moveToFirst() ? Long.valueOf(c.getLong(0)) : null;
        } finally {
            c.close();
        }
    }

    /** מעביר מבחן מקומי למזהה שהמערכת נתנה לו. הציונים וההערות עוברים איתו, כך שלא ייווצר כפילות בייצוא הבא. */
    public void adoptExam(long localId, long systemId) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM exams WHERE id = ?", new Object[]{Long.valueOf(systemId)});
        db.execSQL("UPDATE OR REPLACE scores SET examId = ? WHERE examId = ?",
                new Object[]{Long.valueOf(systemId), Long.valueOf(localId)});
        db.execSQL("UPDATE exams SET id = ? WHERE id = ?", new Object[]{Long.valueOf(systemId), Long.valueOf(localId)});
    }

    /** מיפוי מזהה מערכת -> ת.ז. (לייבוא ציונים מקובץ JSON של המערכת). */
    public Map<Long, String> systemIds() {
        Map<Long, String> out = new HashMap<Long, String>();
        Cursor c = getReadableDatabase().rawQuery("SELECT sid, tz FROM students WHERE sid IS NOT NULL", null);
        try {
            while (c.moveToNext()) out.put(Long.valueOf(c.getLong(0)), c.getString(1));
        } finally {
            c.close();
        }
        return out;
    }

    public void upsertExam(long id, String cls, String subject, String title, String date) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("className", cls);
        v.put("subject", subject);
        v.put("title", title);
        v.put("date", date);
        getWritableDatabase().insertWithOnConflict("exams", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public int count(String table) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public static class Exam {
        public long id;
        public String className, subject, title, date;

        @Override
        public String toString() {
            return (date == null ? "" : date) + "  ·  " + subject + "  ·  " + title;
        }
    }

    public static class Student {
        public String tz, firstName, lastName, className, status, details;

        public String name() {
            return (firstName + " " + lastName).trim();
        }

        @Override
        public String toString() {
            String s = name() + "  ·  כיתה " + className;
            return status.startsWith("עזב") ? s + "  ·  עזב" : s;
        }
    }

    public static class Row {
        public String tz;
        public String name;
        public Double score;
        public String note = "";
    }
}
