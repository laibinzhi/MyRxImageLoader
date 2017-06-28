package android.lbz.com.myrximageloader;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

/**
 * Created by laibinzhi on 2017/3/18.
 */
public class MainActivity extends AppCompatActivity {

    private Button load_img_btn;
    private ImageView image_view;
    private static String url = "http://imgsrc.baidu.com/baike/pic/item/aa64034f78f0f7366fe44ee30a55b319ebc4133d.jpg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        load_img_btn = (Button) findViewById(R.id.load_img_btn);
        image_view = (ImageView) findViewById(R.id.image_view);
        load_img_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadImg();
            }
        });
    }

    private void loadImg() {
        RxImageLoader.with(MainActivity.this).load(url).into(image_view);
        Picasso.with(MainActivity.this).load(url).into(image_view);
    }
}
