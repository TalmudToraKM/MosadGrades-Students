package il.mosad.grades;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** מסך ראשי: ייבוא מהמערכת (Excel תלמידים או JSON ציונים), ייצוא לקובץ, בחירת כיתה. */
public class MainActivity extends Activity {

    private Db db;
    private TextView status;
    private ListView list;
    private List<String> classes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = Db.get(this);
        status = (TextView) findViewById(R.id.status);
        list = (ListView) findViewById(R.id.list);

        ((Button) findViewById(R.id.btnImport)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { chooseImportFile(); }
        });
        ((Button) findViewById(R.id.btnExport)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { exportFile(); }
        });
        ((Button) findViewById(R.id.btnAddStudent)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Forms.addStudent(MainActivity.this, null, new Forms.Done() {
                    public void run() { refresh(); }
                });
            }
        });
        ((Button) findViewById(R.id.btnAllStudents)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, StudentListActivity.class));
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(MainActivity.this, ExamListActivity.class);
                i.putExtra("cls", classes.get(position));
                startActivity(i);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        classes = db.classNames();
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, classes));
        status.setText("תלמידים: " + db.count("students") + "   ·   מבחנים: " + db.count("exams")
                + "   ·   ציונים: " + db.count("scores") + "\nתיקיית קבצים: " + Io.folder().getAbsolutePath());
        if (classes.isEmpty()) {
            status.append("\nאין נתונים עדיין. העתיקו לתיקייה את רשימת התלמידים (Excel) ואת ייצוא הציונים (JSON) מהמערכת, ולחצו \"ייבוא מהמערכת\".");
        }
    }

    private void chooseImportFile() {
        final File[] files = Io.importableFiles();
        if (files.length == 0) {
            Toast.makeText(this, "לא נמצאו קבצים בתיקייה " + Io.folder().getAbsolutePath(), Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("בחרו קובץ לייבוא")
                .setItems(Io.names(files), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) { importFile(files[which]); }
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void importFile(File f) {
        try {
            Toast.makeText(this, Io.importFile(db, f), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "הייבוא נכשל: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void exportFile() {
        try {
            File f = Io.exportScores(db);
            Toast.makeText(this, "נשמר: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
