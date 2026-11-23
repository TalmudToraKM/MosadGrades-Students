package il.mosad.grades;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

/** כרטיס תלמיד: כל הפרטים מרשימת התלמידים של המערכת (לקריאה בלבד). */
public class StudentActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Db.Student s = Db.get(this).student(getIntent().getStringExtra("tz"));
        if (s == null) {
            Toast.makeText(this, "התלמיד לא נמצא", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (12 * getResources().getDisplayMetrics().density);
        col.setPadding(p, p, p, p);
        add(col, "שם", s.name());
        add(col, "ת.ז.", s.tz);
        add(col, "כיתה", s.className);
        add(col, "סטטוס", s.status);
        try {
            JSONArray det = new JSONArray(s.details);
            for (int i = 0; i < det.length(); i++) {
                JSONArray pair = det.getJSONArray(i);
                add(col, pair.getString(0), pair.getString(1));
            }
        } catch (JSONException e) {
            Toast.makeText(this, "לא ניתן לקרוא את פרטי התלמיד", Toast.LENGTH_LONG).show();
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }

    private void add(LinearLayout col, String label, String value) {
        if (value == null || value.length() == 0) return;
        TextView t = new TextView(this);
        t.setText(label + ": " + value);
        t.setGravity(Gravity.RIGHT);
        t.setTextSize(16);
        t.setPadding(0, 6, 0, 6);
        col.addView(t);
    }
}
