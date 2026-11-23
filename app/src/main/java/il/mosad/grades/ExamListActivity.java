package il.mosad.grades;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/** רשימת המבחנים של כיתה, עם הוספת מבחן או תלמיד לכיתה. בחירה במבחן פותחת את גיליון הציונים שלו. */
public class ExamListActivity extends Activity {

    private String cls;
    private List<Db.Exam> exams;
    private ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        cls = getIntent().getStringExtra("cls");
        ((TextView) findViewById(R.id.title)).setText("כיתה " + cls + " - מבחנים");
        list = (ListView) findViewById(R.id.list);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(ExamListActivity.this, ExamActivity.class);
                i.putExtra("examId", exams.get(position).id);
                startActivity(i);
            }
        });
        ((Button) findViewById(R.id.btnAddExam)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Forms.addExam(ExamListActivity.this, cls, new Forms.Done() {
                    public void run() { reload(); }
                });
            }
        });
        ((Button) findViewById(R.id.btnAddStudent)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Forms.addStudent(ExamListActivity.this, cls, new Forms.Done() {
                    public void run() { reload(); }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        exams = Db.get(this).examsFor(cls);
        list.setAdapter(new ArrayAdapter<Db.Exam>(this, android.R.layout.simple_list_item_1, exams));
    }
}
