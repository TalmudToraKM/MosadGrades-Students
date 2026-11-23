package il.mosad.grades;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** חלונות להוספת תלמיד חדש ומבחן חדש מתוך האפליקציה. */
public class Forms {

    public interface Done {
        void run();
    }

    private static EditText field(Activity a, String hint, int inputType) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setGravity(Gravity.RIGHT);
        if (inputType != 0) e.setInputType(inputType);
        return e;
    }

    private static LinearLayout column(Activity a, EditText... fields) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * a.getResources().getDisplayMetrics().density);
        l.setPadding(p, p / 2, p, 0);
        for (EditText f : fields) l.addView(f);
        return l;
    }

    /** תלמיד חדש לפי ת.ז. הכיתה מוצעת מראש אם נפתח מתוך כיתה. */
    public static void addStudent(final Activity a, String cls, final Done done) {
        final EditText first = field(a, "שם פרטי", 0);
        final EditText last = field(a, "שם משפחה", 0);
        final EditText tz = field(a, "ת.ז. (עד 9 ספרות)", InputType.TYPE_CLASS_NUMBER);
        final EditText klass = field(a, "כיתה", 0);
        if (cls != null) klass.setText(cls);
        final AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("תלמיד חדש")
                .setView(column(a, first, last, tz, klass))
                .setPositiveButton("שמור", null)
                .setNegativeButton("ביטול", null)
                .create();
        d.show();
        // הכפתור נשאר פתוח אם יש שגיאה, כדי שלא יאבדו הנתונים שהוזנו
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String err = Db.get(a).addStudent(tz.getText().toString(), first.getText().toString(),
                        last.getText().toString(), klass.getText().toString());
                if (err != null) {
                    Toast.makeText(a, err, Toast.LENGTH_LONG).show();
                    return;
                }
                d.dismiss();
                if (done != null) done.run();
            }
        });
    }

    /** מבחן חדש לכיתה: מקצוע, שם (אופציונלי) ותאריך. */
    public static void addExam(final Activity a, final String cls, final Done done) {
        final EditText subject = field(a, "מקצוע (למשל: חומש)", 0);
        final EditText title = field(a, "שם המבחן (לא חובה)", 0);
        final EditText date = field(a, "תאריך yyyy-mm-dd", 0);
        date.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
        final AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("מבחן חדש - כיתה " + cls)
                .setView(column(a, subject, title, date))
                .setPositiveButton("שמור", null)
                .setNegativeButton("ביטול", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String s = subject.getText().toString().trim();
                String dt = date.getText().toString().trim();
                String t = title.getText().toString().trim();
                if (s.length() == 0) {
                    Toast.makeText(a, "יש להזין מקצוע", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!dt.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    Toast.makeText(a, "התאריך צריך להיות בפורמט yyyy-mm-dd", Toast.LENGTH_LONG).show();
                    return;
                }
                // בלי שם, השם הוא "מבחן <מקצוע>" - כמו במערכת, כדי שההתאמה בייבוא תצליח
                if (t.length() == 0) t = "מבחן " + s;
                Db.get(a).addExam(cls, s, t, dt);
                d.dismiss();
                if (done != null) done.run();
            }
        });
    }
}
