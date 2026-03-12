package app.revanced.extension.gamehub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Component Manager Activity — injected into GameHub's dex via ReVanced extension.
 *
 * <p>Provides a simple ListView UI to:
 * <ul>
 *   <li>Browse installed components under {@code getFilesDir()/usr/home/components/}</li>
 *   <li>Inject a WCP/ZIP file via SAF (replaces the component folder)</li>
 *   <li>Backup a component folder to {@code Downloads/BannerHub/{name}/}</li>
 * </ul>
 * </p>
 *
 * <p>Must be registered in AndroidManifest.xml (done by the resource patch).</p>
 */
@SuppressWarnings("unused")
public class ComponentManagerActivity extends Activity {

    private static final String TAG = "BannerHub";
    private static final int REQUEST_CODE_PICK_WCP = 1001;

    private File componentsDir;
    private List<String> componentNames = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private String selectedComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        componentsDir = new File(getFilesDir(), "usr/home/components");

        // Build UI programmatically (no layout XML needed)
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Component Manager");
        title.setTextSize(20f);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        ListView listView = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, componentNames);
        listView.setAdapter(adapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(listView, lp);

        // Action buttons
        Button btnInject = new Button(this);
        btnInject.setText("Inject WCP/ZIP");
        root.addView(btnInject);

        Button btnBackup = new Button(this);
        btnBackup.setText("Backup Selected");
        root.addView(btnBackup);

        Button btnRefresh = new Button(this);
        btnRefresh.setText("Refresh");
        root.addView(btnRefresh);

        setContentView(root);

        // Listeners
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selectedComponent = componentNames.get(position);
            Toast.makeText(this, "Selected: " + selectedComponent, Toast.LENGTH_SHORT).show();
        });

        btnInject.setOnClickListener(v -> {
            if (selectedComponent == null) {
                Toast.makeText(this, "Select a component first", Toast.LENGTH_SHORT).show();
                return;
            }
            openFilePicker();
        });

        btnBackup.setOnClickListener(v -> {
            if (selectedComponent == null) {
                Toast.makeText(this, "Select a component first", Toast.LENGTH_SHORT).show();
                return;
            }
            backupComponent(selectedComponent);
        });

        btnRefresh.setOnClickListener(v -> refreshComponents());

        refreshComponents();
    }

    private void refreshComponents() {
        componentNames.clear();
        if (componentsDir.isDirectory()) {
            File[] dirs = componentsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                Arrays.sort(dirs);
                for (File d : dirs) componentNames.add(d.getName());
            }
        }
        if (componentNames.isEmpty()) componentNames.add("(no components found)");
        adapter.notifyDataSetChanged();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_WCP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_PICK_WCP || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        File destDir = new File(componentsDir, selectedComponent);

        // Run extraction on background thread
        Handler uiHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                WcpExtractor.extract(getContentResolver(), uri, destDir);
                uiHandler.post(() -> {
                    Toast.makeText(this, "Injected successfully", Toast.LENGTH_SHORT).show();
                    refreshComponents();
                });
            } catch (Throwable t) {
                Log.e(TAG, "Extraction failed", t);
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                uiHandler.post(() ->
                        Toast.makeText(this, "Inject failed: " + msg, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void backupComponent(String name) {
        File src = new File(componentsDir, name);
        if (!src.isDirectory()) {
            Toast.makeText(this, "Component directory not found", Toast.LENGTH_SHORT).show();
            return;
        }

        File backupRoot = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BannerHub/" + name);
        backupRoot.mkdirs();

        Handler uiHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                copyDir(src, backupRoot);
                uiHandler.post(() ->
                        Toast.makeText(this, "Backed up to Downloads/BannerHub/" + name,
                                Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Backup failed", e);
                uiHandler.post(() ->
                        Toast.makeText(this, "Backup failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static void copyDir(File src, File dst) throws Exception {
        dst.mkdirs();
        File[] files = src.listFiles();
        if (files == null) return;
        byte[] buf = new byte[8192];
        for (File f : files) {
            if (f.isDirectory()) {
                copyDir(f, new File(dst, f.getName()));
            } else {
                try (InputStream in = new FileInputStream(f);
                     OutputStream out = new FileOutputStream(new File(dst, f.getName()))) {
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
        }
    }
}
