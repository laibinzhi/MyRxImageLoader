package android.lbz.com.myrximageloader;

import android.content.Context;
import android.widget.ImageView;

import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;

/**
 * Created by laibinzhi on 2017/3/18.
 */
public class RxImageLoader {

    static volatile RxImageLoader singleton = null;

    private String mUrl;

    private RequestCreator mRequestCreator;

    private RxImageLoader(Builder builder) {
        mRequestCreator = new RequestCreator(builder.mContext);
    }

    private RxImageLoader() {
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

    public RxImageLoader load(String url) {
        this.mUrl = url;
        return singleton;
    }

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
}
