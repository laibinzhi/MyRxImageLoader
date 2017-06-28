package android.lbz.com.myrximageloader;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by laibinzhi on 2017/3/18.
 */
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
