package android.lbz.com.myrximageloader;

import android.graphics.Bitmap;
import android.util.LruCache;

/**
 * Created by laibinzhi on 2017/3/18.
 */
public class MemoryDataSourceObservable extends DataSourceObservable {

    int memorySize = (int) (Runtime.getRuntime().maxMemory() / 1024);

    int maxSize = memorySize / 8;

    private LruCache<String, Bitmap> mLruCache = new LruCache<String, Bitmap>(maxSize) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getRowBytes() * value.getHeight() / 1024;
        }
    };

    @Override
    public Image getDataFromDataSource(String url) {
        Bitmap bitmap = mLruCache.get(url);
        if (bitmap != null) {
            return new Image(url, bitmap);
        }
        return null;
    }

    @Override
    public void putDataToDataSource(Image image) {
        mLruCache.put(image.getUrl(), image.getBitmap());
    }
}
