package android.lbz.com.myrximageloader;

import android.content.Context;
import android.util.Log;

import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;

/**
 * Created by laibinzhi on 2017/3/18.
 */
public class RequestCreator {

    private static final String TAG = "RequestCreator";

    private MemoryDataSourceObservable mMemoryCacheObservable;

    private DiskDataSourceObservable mDiskCacheObservable;

    private NetworkDataSourceObservable mNetworkCacheObservable;

    public RequestCreator(Context context) {
        mMemoryCacheObservable = new MemoryDataSourceObservable();
        mDiskCacheObservable = new DiskDataSourceObservable(context);
        mNetworkCacheObservable = new NetworkDataSourceObservable();
    }

    public Observable<Image> getImageFromMemory(String url) {
        return mMemoryCacheObservable.getImage(url).filter(new Predicate<Image>() {
            @Override
            public boolean test(Image image) throws Exception {
                return image!=null;
            }
        }).doOnNext(new Consumer<Image>() {
            @Override
            public void accept(Image image) throws Exception {
                Log.d(TAG, "get data from Memory");
            }
        });
    }

    public Observable<Image> getImageFromDisk(String url) {
        return mDiskCacheObservable.getImage(url)
                .filter(new Predicate<Image>() {
                    @Override
                    public boolean test(Image image) throws Exception {
                        return image!=null;
                    }
                }).doOnNext(new Consumer<Image>() {
                    @Override
                    public void accept(Image image) throws Exception {
                        Log.d(TAG, "get data from Disk");
                        mMemoryCacheObservable.putDataToDataSource(image);
                    }
                });
    }

    public Observable<Image> getImageFromNetwork(String url) {
        return mNetworkCacheObservable.getImage(url)
                .filter(new Predicate<Image>() {
                    @Override
                    public boolean test(Image image) throws Exception {
                        return image != null;
                    }
                }).doOnNext(new Consumer<Image>() {
                    @Override
                    public void accept(Image image) throws Exception {
                        Log.d(TAG, "get data from Network");
                        mMemoryCacheObservable.putDataToDataSource(image);
                        mDiskCacheObservable.putDataToDataSource(image);
                    }
                });
    }

}
