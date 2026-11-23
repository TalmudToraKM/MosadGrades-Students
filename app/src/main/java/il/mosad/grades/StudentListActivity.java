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

/** כל התלמידים של המערכת. בחירה בתלמיד פותחת את כרטיס הפרטים שלו. */
public class StudentListActivity extends Activity {

    private List<Db.Student> students;
    private ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        ((TextView) findViewById(R.id.title)).setText("כל התלמידים");
        findViewById(R.id.btnAddExam).setVisibility(View.GONE);
        ((Button) findViewById(R.id.btnAddStudent)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Forms.addStudent(StudentListActivity.this, null, new Forms.Done() {
                    public void run() { reload(); }
                });
            }
        });
        list = (ListView) findViewById(R.id.list);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(StudentListActivity.this, StudentActivity.class);
                i.putExtra("tz", students.get(position).tz);
                startActivity(i);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        students = Db.get(this).allStudents();
        list.setAdapter(new ArrayAdapter<Db.Student>(this, android.R.layout.simple_list_item_1, students));
    }
}
