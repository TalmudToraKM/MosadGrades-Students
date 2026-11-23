package il.mosad.grades;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** גיליון ציונים למבחן: לכל תלמיד ציון והערה. השמירה כשיוצאים משדה. */
public class ExamActivity extends Activity {

    private Db db;
    private Db.Exam exam;
    private List<Db.Row> rows;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam);
        db = Db.get(this);
        exam = db.exam(getIntent().getLongExtra("examId", -1));
        if (exam == null) {
            Toast.makeText(this, "המבחן לא נמצא", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ((TextView) findViewById(R.id.title)).setText(exam.subject + " · " + exam.title + " · כיתה " + exam.className);
        rows = db.rowsFor(exam.id, exam.className);
        adapter = new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int pos) { return rows.get(pos); }
            public long getItemId(int pos) { return pos; }

            public View getView(int pos, View convertView, ViewGroup parent) {
                Holder h;
                if (convertView == null) {
                    convertView = LayoutInflater.from(ExamActivity.this).inflate(R.layout.row_student, parent, false);
                    h = new Holder();
                    h.name = (TextView) convertView.findViewById(R.id.name);
                    h.score = (EditText) convertView.findViewById(R.id.score);
                    h.note = (EditText) convertView.findViewById(R.id.note);
                    h.score.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                        public void onFocusChange(View v, boolean hasFocus) { if (!hasFocus) saveRow(v); }
                    });
                    h.note.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                        public void onFocusChange(View v, boolean hasFocus) { if (!hasFocus) saveRow(v); }
                    });
                    convertView.setTag(h);
                } else {
                    h = (Holder) convertView.getTag();
                }
                Db.Row r = rows.get(pos);
                h.name.setText(r.name);
                h.score.setTag(r);
                h.note.setTag(r);
                h.score.setText(r.score == null ? "" : formatScore(r.score));
                h.note.setText(r.note);
                return convertView;
            }
        };
        ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // תלמידים שנוספו בינתיים (מתוך רשימת הכיתה) מופיעים כאן
        if (exam != null) {
            rows = db.rowsFor(exam.id, exam.className);
            adapter.notifyDataSetChanged();
        }
    }

    /** שמירת השורה של השדה שאיבד פוקוס. השורה נשלפת מהתג של השדה. */
    private void saveRow(View field) {
        View row = (View) field.getParent();
        Db.Row r = (Db.Row) ((EditText) row.findViewById(R.id.score)).getTag();
        String scoreText = ((EditText) row.findViewById(R.id.score)).getText().toString();
        String note = ((EditText) row.findViewById(R.id.note)).getText().toString();
        String err = db.save(exam.id, r.tz, scoreText, note);
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
            ((EditText) row.findViewById(R.id.score)).setText(r.score == null ? "" : formatScore(r.score));
            return;
        }
        // עדכון הזיכרון של השורה כדי שהתצוגה תישאר נכונה אחרי גלילה
        String t = scoreText.trim().replace(',', '.');
        r.score = t.length() == 0 ? null : Double.valueOf(t);
        r.note = note.trim();
    }

    private static String formatScore(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    static class Holder {
        TextView name;
        EditText score;
        EditText note;
    }
}
