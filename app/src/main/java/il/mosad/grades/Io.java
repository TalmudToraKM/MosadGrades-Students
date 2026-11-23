package il.mosad.grades;

import android.database.Cursor;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * ייבוא וייצוא דרך תיקייה קבועה בטלפון: /sdcard/Mosad/
 *
 * ייבוא מהמערכת:
 *   - קובץ Excel "תלמידים" (xlsx): שם פרטי, שם משפחה, ת.ז., כיתה, סטטוס.
 *   - קובץ JSON "ייצוא JSON" מהמודול ציונים: מבחנים קיימים, תלמידים וציונים.
 * ייצוא לקובץ: JSON עם הציונים וההערות שנרשמו בטלפון. המערכת מייבאת אותו ישירות.
 *
 * מיזוג ציונים בייבוא: ערך בקובץ מעדכן את הטלפון. ערך ריק בקובץ לא מוחק ציון שנרשם בטלפון.
 */
public class Io {

    public static final String FORMAT = "mosad-grades";
    public static final String FOLDER = "Mosad";

    public static File folder() {
        File f = new File(Environment.getExternalStorageDirectory(), FOLDER);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    /** קבצי xlsx ו-json בתיקייה, החדשים ראשונים. */
    public static File[] importableFiles() {
        File[] files = folder().listFiles();
        List<File> out = new ArrayList<File>();
        if (files != null) {
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.US);
                if (f.isFile() && (n.endsWith(".json") || n.endsWith(".xlsx"))) out.add(f);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            public int compare(File a, File b) {
                return a.lastModified() < b.lastModified() ? 1 : (a.lastModified() > b.lastModified() ? -1 : 0);
            }
        });
        return out.toArray(new File[out.size()]);
    }

    public static String[] names(File[] files) {
        String[] out = new String[files.length];
        for (int i = 0; i < files.length; i++) out[i] = files[i].getName();
        return out;
    }

    /** מחזיר סיכום בעברית. זורק IOException עם הודעה מובנת לשגיאה. */
    public static String importFile(Db db, File file) throws IOException {
        String n = file.getName().toLowerCase(Locale.US);
        if (n.endsWith(".xlsx")) return importStudents(db, file);
        return importSystemJson(db, file);
    }

    /** ייבוא רשימת תלמידים מקובץ Excel של המערכת. */
    public static String importStudents(Db db, File file) throws IOException {
        List<String[]> rows = XlsxReader.readFirstSheet(file);
        if (rows.size() < 2) throw new IOException("לא נמצאו שורות בקובץ. השורה הראשונה צריכה להכיל כותרות.");
        String[] head = rows.get(0);
        int cFirst = find(head, "שם פרטי"), cLast = find(head, "שם משפחה"), cTz = find(head, "ת.ז.");
        int cCls = find(head, "כיתה"), cStatus = find(head, "סטטוס");
        if (cTz < 0 || cFirst < 0 || cLast < 0 || cCls < 0) {
            throw new IOException("חסרות עמודות: שם פרטי, שם משפחה, ת.ז., כיתה. יש לייצא מהמערכת את רשימת התלמידים.");
        }
        int n = 0, skipped = 0;
        db.getWritableDatabase().beginTransaction();
        try {
            for (int i = 1; i < rows.size(); i++) {
                String[] r = rows.get(i);
                String tz = normTz(cell(r, cTz));
                if (tz.length() == 0) { skipped++; continue; }
                db.upsertStudent(tz, null, cell(r, cFirst), cell(r, cLast), cell(r, cCls), cStatus < 0 ? "" : cell(r, cStatus));
                JSONArray det = new JSONArray();
                for (int k = 0; k < head.length; k++) {
                    String h = head[k].trim();
                    String v = cellValue(h, cell(r, k));
                    if (h.length() == 0 || v.length() == 0) continue;
                    JSONArray pair = new JSONArray();
                    pair.put(h);
                    pair.put(v);
                    det.put(pair);
                }
                db.setDetails(tz, det.toString());
                n++;
            }
            db.getWritableDatabase().setTransactionSuccessful();
        } finally {
            db.getWritableDatabase().endTransaction();
        }
        return "נקראו " + n + " תלמידים" + (skipped > 0 ? " (" + skipped + " שורות בלי ת.ז. דולגו)" : "") + ".";
    }

    /** ייבוא קובץ JSON מהמערכת: מבחנים, תלמידים וציונים. */
    public static String importSystemJson(Db db, File file) throws IOException {
        JSONObject j;
        try {
            j = new JSONObject(readUtf8(file));
        } catch (JSONException e) {
            throw new IOException("הקובץ פגום ואינו JSON תקין");
        }
        if (!FORMAT.equals(j.optString("format"))) {
            throw new IOException("הקובץ אינו ייצוא ציונים מהמערכת (חסר format = mosad-grades)");
        }
        JSONArray students = j.optJSONArray("students");
        JSONArray exams = j.optJSONArray("exams");
        JSONArray scores = j.optJSONArray("scores");
        int nStudents = 0, nExams = 0, nScores = 0, nSkipped = 0;
        Map<Long, String> sidToTz = db.systemIds();

        db.getWritableDatabase().beginTransaction();
        try {
            if (students != null) for (int i = 0; i < students.length(); i++) {
                JSONObject s = students.getJSONObject(i);
                String tz = normTz(s.optString("tz", ""));
                if (tz.length() == 0) continue;
                long sid = s.getLong("id");
                db.upsertStudent(tz, Long.valueOf(sid), "", "", s.optString("className", ""), "");
                // שם מלא מהמערכת משמש רק אם אין עדיין שם בטלפון (רשימת התלמידים מה-Excel גוברת)
                db.setNameIfMissing(tz, s.optString("name", ""));
                sidToTz.put(Long.valueOf(sid), tz);
                nStudents++;
            }
            if (exams != null) for (int i = 0; i < exams.length(); i++) {
                JSONObject e = exams.getJSONObject(i);
                String cls = e.optString("className", ""), subj = e.optString("subject", "");
                String ttl = e.optString("title", ""), dt = e.optString("date", "");
                Long local = db.findLocalExam(cls, subj, ttl, dt);
                if (local != null) db.adoptExam(local.longValue(), e.getLong("id"));
                db.upsertExam(e.getLong("id"), cls, subj, ttl, dt);
                nExams++;
            }
            if (scores != null) for (int i = 0; i < scores.length(); i++) {
                JSONObject r = scores.getJSONObject(i);
                String tz = null;
                if (!r.isNull("studentId")) tz = sidToTz.get(Long.valueOf(r.getLong("studentId")));
                if (tz == null && r.has("tz")) tz = normTz(r.optString("tz", ""));
                if (tz == null || tz.length() == 0) { nSkipped++; continue; }
                long examId = r.getLong("examId");
                Db.Row old = db.existing(examId, tz);
                Double score = r.isNull("score") ? null : Double.valueOf(r.getDouble("score"));
                String note = r.optString("note", "");
                if (score == null && old != null) score = old.score;
                if (note.length() == 0 && old != null) note = old.note;
                if (score == null && note.length() == 0) { nSkipped++; continue; }
                db.writeScore(examId, tz, score, note);
                nScores++;
            }
            db.getWritableDatabase().setTransactionSuccessful();
        } finally {
            db.getWritableDatabase().endTransaction();
        }
        return "נקראו " + nStudents + " תלמידים, " + nExams + " מבחנים, " + nScores + " ציונים" +
                (nSkipped > 0 ? " (" + nSkipped + " שורות ריקות או לא מזוהות דולגו)" : "") + ".";
    }

    /** כותב את כל הציונים וההערות שנרשמו בטלפון לקובץ JSON. */
    public static File exportScores(Db db) throws IOException {
        try {
            JSONObject out = new JSONObject();
            out.put("format", FORMAT);
            out.put("version", 1);
            out.put("source", "app");
            out.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
            JSONArray arr = new JSONArray();
            Cursor c = db.getReadableDatabase().rawQuery(
                    "SELECT sc.examId, sc.tz, sc.score, sc.note, s.sid FROM scores sc " +
                            "LEFT JOIN students s ON s.tz = sc.tz " +
                            "WHERE sc.score IS NOT NULL OR (sc.note IS NOT NULL AND sc.note != '')", null);
            try {
                while (c.moveToNext()) {
                    JSONObject r = new JSONObject();
                    r.put("examId", c.getLong(0));
                    r.put("tz", c.getString(1));
                    r.put("studentId", c.isNull(4) ? JSONObject.NULL : Long.valueOf(c.getLong(4)));
                    r.put("score", c.isNull(2) ? JSONObject.NULL : Double.valueOf(c.getDouble(2)));
                    r.put("note", c.isNull(3) ? "" : c.getString(3));
                    arr.put(r);
                }
            } finally {
                c.close();
            }
            out.put("scores", arr);
            JSONArray newEx = new JSONArray();
            Cursor ce = db.getReadableDatabase().rawQuery(
                    "SELECT id, className, subject, title, date FROM exams WHERE id < 0", null);
            try {
                while (ce.moveToNext()) {
                    JSONObject e = new JSONObject();
                    e.put("localId", ce.getLong(0));
                    e.put("className", ce.getString(1));
                    e.put("subject", ce.getString(2));
                    e.put("title", ce.isNull(3) ? "" : ce.getString(3));
                    e.put("date", ce.isNull(4) ? "" : ce.getString(4));
                    newEx.put(e);
                }
            } finally {
                ce.close();
            }
            out.put("newExams", newEx);
            JSONArray newSt = new JSONArray();
            Cursor cs = db.getReadableDatabase().rawQuery(
                    "SELECT tz, firstName, lastName, className, status FROM students WHERE sid IS NULL", null);
            try {
                while (cs.moveToNext()) {
                    JSONObject s = new JSONObject();
                    s.put("tz", cs.getString(0));
                    s.put("firstName", cs.isNull(1) ? "" : cs.getString(1));
                    s.put("lastName", cs.isNull(2) ? "" : cs.getString(2));
                    s.put("className", cs.isNull(3) ? "" : cs.getString(3));
                    String st = cs.isNull(4) ? "" : cs.getString(4);
                    s.put("status", st.startsWith("עזב") ? "left" : "active");
                    newSt.put(s);
                }
            } finally {
                cs.close();
            }
            out.put("newStudents", newSt);
            File f = new File(folder(), "grades-app-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date()) + ".json");
            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                w.write(out.toString(2));
            } finally {
                w.close();
            }
            return f;
        } catch (JSONException e) {
            throw new IOException("הייצוא נכשל: " + e.getMessage());
        }
    }

    /** ת.ז. בתשעה ספרות (כמו במערכת). */
    public static String normTz(String s) {
        String d = s == null ? "" : s.replaceAll("\\D", "");
        if (d.length() == 0) return "";
        if (d.length() > 9) d = d.substring(d.length() - 9);
        while (d.length() < 9) d = "0" + d;
        return d;
    }

    /** ערך תא לתצוגה. תאריך שמגיע כמספר סידורי של Excel מומר ל-dd/MM/yyyy. */
    static String cellValue(String header, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.length() == 0) return "";
        if (header.contains("תאריך") && v.matches("\\d+(\\.\\d+)?")) {
            long ms = (long) ((Double.parseDouble(v) - 25569) * 86400000L);
            SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.format(new Date(ms));
        }
        return v;
    }

    private static int find(String[] head, String name) {
        for (int i = 0; i < head.length; i++) if (name.equals(head[i].trim())) return i;
        return -1;
    }

    private static String cell(String[] r, int i) {
        return i >= 0 && i < r.length ? r[i].trim() : "";
    }

    static String readUtf8(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) bo.write(b, 0, n);
            String s = new String(bo.toByteArray(), "UTF-8");
            if (s.length() > 0 && s.charAt(0) == '﻿') s = s.substring(1);
            return s;
        } finally {
            in.close();
        }
    }
}
