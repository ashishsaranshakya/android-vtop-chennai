package tk.therealsuji.vtopchennai;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Objects;

public class SpotlightActivity extends AppCompatActivity {
    ScrollView announcements;
    HorizontalScrollView categoriesContainer;
    WebView download;
    ArrayList<TextView> categories = new ArrayList<>();
    ArrayList<LinearLayout> announcementViews = new ArrayList<>();
    String downloadLink;
    float pixelDensity;
    int index, halfWidth;
    int STORAGE_PERMISSION_CODE = 1;
    boolean isDownloadOpened, terminateThread = false;

    public void setAnnouncements(View view) {
        int selectedIndex = Integer.parseInt(view.getTag().toString());
        if (selectedIndex == index) {
            return;
        } else {
            index = selectedIndex;
        }

        announcements.scrollTo(0, 0);
        announcements.removeAllViews();
        announcements.setAlpha(0);
        announcements.addView(announcementViews.get(index));
        announcements.animate().alpha(1);

        for (int i = 0; i < categories.size(); ++i) {
            categories.get(i).setBackground(ContextCompat.getDrawable(this, R.drawable.button_secondary));
        }
        categories.get(index).setBackground(ContextCompat.getDrawable(this, R.drawable.button_secondary_selected));

        float location = 0;
        for (int i = 0; i < index; ++i) {
            location += 10 * pixelDensity + (float) categories.get(i).getWidth();
        }
        location += 20 * pixelDensity + (float) categories.get(index).getWidth() / 2;
        categoriesContainer.smoothScrollTo((int) location - halfWidth, 0);
    }

    public void downloadDocument(String link) {
        downloadLink = link;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            return;
        }

        isDownloadOpened = false;

        if (download == null) {
            download = new WebView(this);
            download.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.99 Mobile Safari/537.36");
            download.setWebViewClient(new WebViewClient() {
                public void onPageFinished(WebView view, String url) {
                    if (isDownloadOpened) {
                        return;
                    }

                    isDownloadOpened = true;
                    download.loadUrl("http://vtopcc.vit.ac.in/vtop/" + downloadLink + "?&x=");
                }
            });
            download.setDownloadListener(new DownloadListener() {
                @Override
                public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                    Toast.makeText(SpotlightActivity.this, "Downloading document", Toast.LENGTH_SHORT).show();

                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String fileName = contentDisposition.split("filename=")[1];
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "VTOP Spotlight/" + fileName);
                    request.setMimeType(mimetype);
                    request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url));
                    DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    downloadManager.enqueue(request);
                }
            });
        } else {
            download.clearCache(true);
            download.clearHistory();
            CookieManager.getInstance().removeAllCookies(null);
        }

        download.loadUrl("http://vtopcc.vit.ac.in/vtop");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadDocument(downloadLink);
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spotlight);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        final Context context = this;
        final LinearLayout categoryButtons = findViewById(R.id.categories);
        announcements = findViewById(R.id.announcements);
        pixelDensity = context.getResources().getDisplayMetrics().density;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        halfWidth = displayMetrics.widthPixels / 2;

        categoriesContainer = findViewById(R.id.categoriesContainer);

        new Thread(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase myDatabase = context.openOrCreateDatabase("vtop", Context.MODE_PRIVATE, null);

                myDatabase.execSQL("CREATE TABLE IF NOT EXISTS spotlight (id INT(3) PRIMARY KEY, category VARCHAR, announcement VARCHAR, link VARCHAR)");
                Cursor c = myDatabase.rawQuery("SELECT DISTINCT category FROM spotlight", null);

                int categoryIndex = c.getColumnIndex("category");
                c.moveToFirst();

                LayoutGenerator myLayouot = new LayoutGenerator(context);
                ButtonGenerator myButton = new ButtonGenerator(context);
                CardGenerator myAnnouncement = new CardGenerator(context, CardGenerator.CARD_SPOTLIGHT);
                LinkButtonGenerator myLink = new LinkButtonGenerator(context);

                for (int i = 0; i < c.getCount(); ++i, c.moveToNext()) {
                    if (terminateThread) {
                        return;
                    }

                    if (i == 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                findViewById(R.id.noData).setVisibility(View.GONE);
                            }
                        });
                    }

                    String categoryTitle = c.getString(categoryIndex);

                    /*
                        Creating the announcements view
                     */
                    final LinearLayout announcementsView = myLayouot.generateLayout();

                    announcementViews.add(announcementsView);    //Storing the view

                    /*
                        Creating the category button
                     */
                    final TextView category = myButton.generateButton(categoryTitle);
                    if (i == 0 && i == index) {
                        category.setBackground(ContextCompat.getDrawable(context, R.drawable.button_secondary_selected));
                    }
                    category.setTag(i);
                    category.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            setAnnouncements(category);
                        }
                    });

                    categories.add(category);    //Storing the button

                    /*
                        Finally adding the button to the HorizontalScrollView
                     */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            categoryButtons.addView(category);
                        }
                    });

                    Cursor s = myDatabase.rawQuery("SELECT announcement, link FROM spotlight WHERE category = '" + categoryTitle + "'", null);

                    int announcementIndex = s.getColumnIndex("announcement");
                    int linkIndex = s.getColumnIndex("link");
                    s.moveToFirst();

                    for (int j = 0; j < s.getCount(); ++j, s.moveToNext()) {
                        if (terminateThread) {
                            return;
                        }

                        String announcement = s.getString(announcementIndex);
                        final String link = s.getString(linkIndex);

                        final LinearLayout card = myAnnouncement.generateCard(announcement, link);

                        if (link.toLowerCase().startsWith("http")) {
                            card.addView(myLink.generateButton(link, LinkButtonGenerator.LINK_LINK));
                        } else if (!link.equals("null")) {
                            LinearLayout linkView = myLink.generateButton(null, LinkButtonGenerator.LINK_DOWNLOAD);
                            linkView.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    downloadDocument(link);
                                }
                            });
                            card.addView(linkView);
                        }

                        /*
                            Adding the block to the announcements layout
                         */
                        announcementsView.addView(card);
                    }

                    if (i == index) {
                        announcementsView.setAlpha(0);
                        announcementsView.animate().alpha(1);
                    }

                    if (i == 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                announcements.addView(announcementsView);
                            }
                        });
                    }

                    s.close();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.loading).animate().alpha(0);
                    }
                });

                c.close();
                myDatabase.close();

                SharedPreferences sharedPreferences = context.getSharedPreferences("tk.therealsuji.vtopchennai", Context.MODE_PRIVATE);
                sharedPreferences.edit().remove("newSpotlight").apply();
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        terminateThread = true;
    }
}