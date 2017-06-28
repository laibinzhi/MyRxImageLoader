package android.lbz.com.myrximageloader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by laibinzhi on 2017/3/18.
 */
public class NetworkDataSourceObservable extends DataSourceObservable {
    @Override
    public Image getDataFromDataSource(String url) {
        Bitmap bitmap = getBitmapFromNet(url);
        if (bitmap != null) {
            return new Image(url, bitmap);
        }
        return null;
    }

    private Bitmap getBitmapFromNet(String url) {
        URLConnection mURLConnection = null;
        InputStream mInputStream = null;
        Bitmap mBitmap = null;
        try {
            mURLConnection = new URL(url).openConnection();
            mInputStream = mURLConnection.getInputStream();
            mBitmap = BitmapFactory.decodeStream(mInputStream);
            if (mBitmap != null) {
                return mBitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return mBitmap;
    }

    @Override
    public void putDataToDataSource(Image image) {

    }
}
