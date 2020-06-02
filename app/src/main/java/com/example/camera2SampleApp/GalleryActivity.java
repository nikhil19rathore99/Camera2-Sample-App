package com.example.camera2SampleApp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;
import java.util.ArrayList;

public class GalleryActivity extends Activity {
    private ArrayList<String> pictures = new ArrayList<>();
    private GridView gv_gallery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        gv_gallery = findViewById(R.id.gv_gallery);

        Intent intent = getIntent();
        pictures = intent.getStringArrayListExtra("pictures");
        Toast.makeText(GalleryActivity.this, pictures.size() + " ", Toast.LENGTH_SHORT).show();
        GalleryAdapter galleryAdapter = new GalleryAdapter(pictures, this);
        gv_gallery.setAdapter(galleryAdapter);
        gv_gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                Intent intent = new Intent(Intent.ACTION_VIEW);
                Bitmap bitmap = BitmapFactory.decodeFile(pictures.get(i));
                Uri uri = Uri.parse(MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, null, null));
                intent.setDataAndType(uri, "image/*");

                startActivity(intent);
            }
        });
    }
}
