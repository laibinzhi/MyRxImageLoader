# 基于RxJava2的轻量级图片缓存框架
Android的图片缓存框架有很多，在**Universal-Image-Loader**断更之后，就经常使用**Glide**和**Picasso**。最近基于Picasso的框架实现一个轻量级的图片缓存（三级缓存），加载图片的流程图如图所示:
```
graph TD
    A[请求加载图片] -->|检查|B{缓存Memory是否有数据}
    B -->|否检查| C{本地Disk是否有数据}
    B -->|是| D[显示图片]
    C -->|是| D
    C -->|否| E[加载网络]
    E -->D
    E -->|缓存到内存Memory|B
    E -->|缓存到本地Disk| C
```

---
结合图所得出的流程大概如下
1. 请求加载图片。
2. 首先检测内存中是否存在数据，如果存在，显示数据，如果没有数据，则下一步。
3. 检测本地文件是否存在数据，如果存在，显示数据，并缓存到内存中，如果不存在，则下一步。
4. 请求网络获取网络图片显示数据，并把数据缓存到内存和本地文件中。

---
- 在本个项目中，我们用到的内存缓存是Android自带的**LruCaChe**,本地缓存是**DiskLruCache**，其中后者不是属于Android自带的，[附下载地址](https://android.googlesource.com/platform/libcore/+/jb-mr2-release/luni/src/main/java/libcore/io/DiskLruCache.java)
- 对于以上两个类不作过多陈述，google一下会出来很多demo

---

## 现在，我们开始编写这个简单框架
1. 首先，先来确定我们项目的基本骨架的使用
```Java
        RxImageLoader.with(MainActivity.this).load(url).into(image_view);
```
2. 通过with(context)方法创建RxImageLoader的单例,这里使用的是**构建者模式**创建RxImageLoader的对象。为什么要用构建者模式呢？因为链式调用很简洁易懂，例如OKHttp和Retrofit的创建就是通过构建者Builder创建对象的，额，这是题外话，不做过多讲解。
```Java
    static volatile RxImageLoader singleton = null;
    
    private RxImageLoader(){
        //定义私有的构造方法，防止外部可以通过new来获取RxImageLoader对象
    }

    //单例模式
    public static RxImageLoader with(Context context) {
        if (singleton == null) {
            synchronized (RxImageLoader.class) {
                if (singleton == null) {
                    singleton = new Builder(context).build();
                }
            }
        }
        return singleton;
    }
    
    private RxImageLoader(Builder builder) {
        mRequestCreator = new RequestCreator(builder.mContext);
    }
    
    //构建者模式
    private static class Builder {

        private Context mContext;

        public Builder(Context context) {
            this.mContext = context;
        }

        public RxImageLoader build() {
            return new RxImageLoader(this);
        }
    }
```
3.再看load方法，注意要返回RxImageLoader对象，否者无法链式调用下面的into方法
```Java
    public RxImageLoader load(String url) {
        this.mUrl = url;
        return singleton;
    }
```
4.重头戏来了，into方法。我们要获取来自内存，本地，网络三个数据源的数据，按照顺序获取，任一环节有数据，马上调用下面的方法，所以这里调用RxJava的**concat**操作符和**firstElement()**
> concat操作符---从concatMap操作我们知道，concat操作符肯定也是有序的，而concat操作符是接收若干个Observables，发射数据是有序的，不会交叉。
![image](https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png)
> firstElement()---和RxJava1的**first**操作符功能相类似，源Observable产生的结果的第一个提交给订阅者。

接下来，看代码
```Java
public void into(final ImageView imageView) {
        Observable.concat(mRequestCreator.getImageFromMemory(mUrl),
                mRequestCreator.getImageFromDisk(mUrl),
                mRequestCreator.getImageFromNetwork(mUrl))
                .filter(new Predicate<Image>() {
                    @Override
                    public boolean test(Image image) throws Exception {
                        return image != null;
                    }
                })
                .firstElement()
                .subscribe(new Consumer<Image>() {
                    @Override
                    public void accept(Image image) throws Exception {
                        imageView.setImageBitmap(image.getBitmap());
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        throwable.printStackTrace();
                    }
                });
    }
```

5.大概框架已经搭建好，现在就是三个数据源获取的问题，首先我们定义一个抽象类，里面包含两个抽象方法，分别是读数据和些数据的。还有一个getImage方法。为什么用抽象类而不用接口呢，抽象类可以有方法的实现体。
```Java
public abstract class DataSourceObservable {

    public Observable<Image> getImage(final String url) {

        return Observable.create(new ObservableOnSubscribe<Image>() {

            @Override
            public void subscribe(ObservableEmitter<Image> e) throws Exception {
                Image image = getDataFromDataSource(url);
                if (!e.isDisposed()) {
                    if (image != null) {
                        e.onNext(image);
                    }
                    e.onComplete();
                }

            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public abstract Image getDataFromDataSource(String url);

    public abstract void putDataToDataSource(Image image);
}

```
6.编写三个不同数据源的类实现该抽象类，分别是MemoryDataSourceObservable，DiskDataSourceObservable，NetworkDataSourceObservable。
```Java
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

```

```Java
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

```

```Java
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

```
7.写一个数据管理类来管理这三个类
```Java
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

```
需要注意的是我们请求数据成功之后需要执行保存数据到内存和本地的操作，我们可以在返回Observable<Image>之前执行doOnNext方法。这里的getImageFromMemory方法就不需要了。他属于最高一级。


具体代码请看项目地址[项目地址](https://github.com/laibinzhi/MyRxImageLoader/)



