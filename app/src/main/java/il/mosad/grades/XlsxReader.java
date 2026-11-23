package il.mosad.grades;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * קורא את הגליון הראשון של קובץ xlsx (Excel) בלי ספריות חיצוניות.
 * עובד על Android 4.1: קובץ xlsx הוא ZIP, והנתונים הם XML.
 */
public class XlsxReader {

    /** מחזיר את השורות של הגליון הראשון. כל שורה היא מערך מחרוזות לפי עמודות (A=0). */
    public static List<String[]> readFirstSheet(File file) throws IOException {
        ZipFile zip = new ZipFile(file);
        try {
            List<String> shared = new ArrayList<String>();
            ZipEntry se = zip.getEntry("xl/sharedStrings.xml");
            if (se != null) {
                NodeList sis = parse(zip, se).getElementsByTagName("si");
                for (int i = 0; i < sis.getLength(); i++) shared.add(textOf((Element) sis.item(i)));
            }
            ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheet == null) throw new IOException("לא נמצא גליון ראשון בקובץ Excel");

            NodeList rows = parse(zip, sheet).getElementsByTagName("row");
            List<String[]> out = new ArrayList<String[]>();
            for (int r = 0; r < rows.getLength(); r++) {
                NodeList cells = ((Element) rows.item(r)).getElementsByTagName("c");
                List<String> line = new ArrayList<String>();
                for (int c = 0; c < cells.getLength(); c++) {
                    Element cell = (Element) cells.item(c);
                    int col = columnIndex(cell.getAttribute("r"));
                    String type = cell.getAttribute("t");
                    String value;
                    if ("s".equals(type)) {
                        int idx = Integer.parseInt(textOf(child(cell, "v")).trim());
                        value = idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
                    } else if ("inlineStr".equals(type)) {
                        value = textOf(cell);
                    } else {
                        value = textOf(child(cell, "v"));
                    }
                    while (line.size() <= col) line.add("");
                    line.set(col, value.trim());
                }
                out.add(line.toArray(new String[line.size()]));
            }
            return out;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("קובץ Excel לא תקין: " + e.getMessage());
        } finally {
            zip.close();
        }
    }

    private static Document parse(ZipFile zip, ZipEntry entry) throws Exception {
        InputStream in = zip.getInputStream(entry);
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            return f.newDocumentBuilder().parse(in);
        } finally {
            in.close();
        }
    }

    /** "C5" -> 2, "AA1" -> 26 */
    static int columnIndex(String ref) {
        int n = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch < 'A' || ch > 'Z') break;
            n = n * 26 + (ch - 'A' + 1);
        }
        return Math.max(0, n - 1);
    }

    private static Element child(Element parent, String name) {
        NodeList nl = parent.getElementsByTagName(name);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    /** כל הטקסט שבתוך האלמנט (כולל טקסט מעוצב שמפוצל לכמה חלקים). */
    private static String textOf(Element e) {
        if (e == null) return "";
        StringBuilder sb = new StringBuilder();
        NodeList ts = e.getElementsByTagName("t");
        if (ts.getLength() > 0) {
            for (int i = 0; i < ts.getLength(); i++) sb.append(ts.item(i).getTextContent());
            return sb.toString();
        }
        Node n = e.getFirstChild();
        for (; n != null; n = n.getNextSibling()) sb.append(n.getTextContent() == null ? "" : n.getTextContent());
        return sb.toString();
    }
}
