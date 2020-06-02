package com.example.camera2SampleApp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.util.ArrayList;

public class GalleryAdapter extends BaseAdapter {
    private ArrayList<String> pictures;
    private Context context;

    public GalleryAdapter(ArrayList<String> pictures, Context context) {
        this.pictures = pictures;
        this.context = context;
    }

    @Override
    public int getCount() {
        return pictures.size();
    }

    @Override
    public Object getItem(int i) {
        return pictures.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ImageView imageView;
        if (view == null) {
            imageView = new ImageView(context);
            imageView.setLayoutParams(new GridView.LayoutParams(300, 300));
            imageView.setAdjustViewBounds(false);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            imageView = (ImageView) view;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(pictures.get(i));
        imageView.setImageBitmap(bitmap);
        return imageView;

    }
}
