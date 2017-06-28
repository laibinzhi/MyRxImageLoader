package android.lbz.com.myrximageloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by laibinzhi on 2017/3/18.
 */
public class DiskDataSourceObservable extends DataSourceObservable {


    private DiskLruCache mDiskLruCache;

    DiskCacheUtil mDiskCacheUtil;

    private Context mContext;

    private static long maxSize = 20 * 1024 * 1024;

    public DiskDataSourceObservable(Context context) {
        this.mContext = context;
        initDiskLruCache();
    }

    public void initDiskLruCache() {
        mDiskCacheUtil = DiskCacheUtil.getSingleton();
        File cacheDirectory = mDiskCacheUtil.getDiskCacheDir(mContext, "image_cache");
        int appVersion = mDiskCacheUtil.getAppVersion(mContext);
        try {
            mDiskLruCache=DiskLruCache.open(cacheDirectory, appVersion, 1, maxSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public Image getDataFromDataSource(String url) {
        Bitmap bitmap = getDataFromDiskCache(url);
        if (bitmap != null) {
            return new Image(url, bitmap);
        }
        return null;
    }

    private Bitmap getDataFromDiskCache(String url) {
        DiskLruCache.Snapshot mSnapshot = null;
        FileInputStream mFileInputStream = null;
        FileDescriptor mFileDescriptor = null;
        Bitmap bitmap = null;
        String key = mDiskCacheUtil.hashKeyForDisk(url);
        try {
            mSnapshot = mDiskLruCache.get(key);
            if (mSnapshot != null) {
                mFileInputStream = (FileInputStream) mSnapshot.getInputStream(0);
                mFileDescriptor = mFileInputStream.getFD();
            }
            if (mFileDescriptor != null) {
                bitmap = BitmapFactory.decodeFileDescriptor(mFileDescriptor);
            }
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mFileDescriptor == null && mFileInputStream != null) {
                try {
                    mFileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bitmap;
    }

    @Override
    public void putDataToDataSource(final Image image) {
        Observable.create(new ObservableOnSubscribe<Image>() {
            @Override
            public void subscribe(ObservableEmitter<Image> e) throws Exception {
                putDataToDiskCache(image);
            }
        }).subscribeOn(Schedulers.io()).subscribe();
    }

    private void putDataToDiskCache(Image image) {
        DiskLruCache.Editor mEditor = null;
        OutputStream mOutputStream = null;
        String key = mDiskCacheUtil.hashKeyForDisk(image.getUrl());
        try {
            mEditor = mDiskLruCache.edit(key);
            mOutputStream = mEditor.newOutputStream(0);
            boolean success = downloadUrlToStream(image.getUrl(), mOutputStream);
            if (success) {
                mEditor.commit();
            } else {
                mEditor.abort();
            }
            mDiskLruCache.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 建立HTTP请求，并获取Bitmap对象。
     *
     * @param urlString 图片的URL地址
     * @return 解析后的Bitmap对象
     */
    private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
            out = new BufferedOutputStream(outputStream, 8 * 1024);
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
