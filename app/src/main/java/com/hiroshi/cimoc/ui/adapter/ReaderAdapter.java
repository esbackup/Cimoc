package com.hiroshi.cimoc.ui.adapter;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.support.annotation.IntDef;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.facebook.drawee.backends.pipeline.PipelineDraweeControllerBuilder;
import com.facebook.drawee.backends.pipeline.PipelineDraweeControllerBuilderSupplier;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.listener.BaseRequestListener;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.hiroshi.cimoc.R;
import com.hiroshi.cimoc.fresco.processor.MangaPostprocessor;
import com.hiroshi.cimoc.model.ImageUrl;
import com.hiroshi.cimoc.ui.custom.photo.PhotoDraweeView;
import com.hiroshi.cimoc.ui.custom.photo.PhotoDraweeView.OnLongPressListener;
import com.hiroshi.cimoc.ui.custom.photo.PhotoDraweeView.OnSingleTapListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import butterknife.BindView;

/**
 * Created by Hiroshi on 2016/8/5.
 */
public class ReaderAdapter extends BaseAdapter<ImageUrl> {

    public static final int READER_PAGE = 0;
    public static final int READER_STREAM = 1;

    private static final int TYPE_LOADING = 2016101214;
    private static final int TYPE_IMAGE = 2016101215;

    @IntDef({READER_PAGE, READER_STREAM})
    @Retention(RetentionPolicy.SOURCE)
    @interface ReaderMode {}

    private PipelineDraweeControllerBuilderSupplier mControllerSupplier;
    private OnSingleTapListener mSingleTapListener;
    private OnLongPressListener mLongPressListener;
    private OnLazyLoadListener mLazyLoadListener;
    private @ReaderMode int reader;
    private boolean isVertical;
    private boolean isPaging;
    private boolean isWhiteEdge;

    public ReaderAdapter(Context context, List<ImageUrl> list) {
        super(context, list);
    }

    static class ImageHolder extends BaseViewHolder {
        @BindView(R.id.reader_image_view) PhotoDraweeView photoView;

        ImageHolder(View view) {
            super(view);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return mDataSet.get(position).isLazy() ? TYPE_LOADING : TYPE_IMAGE;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int resId = viewType == TYPE_IMAGE ? R.layout.item_picture : R.layout.item_loading;
        View view = mInflater.inflate(resId, parent, false);
        return new ImageHolder(view);
    }

    private boolean a = false;

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        final ImageUrl imageUrl = mDataSet.get(position);
        if (imageUrl.isLazy()) {
            if (!imageUrl.isLoading() && mLazyLoadListener != null) {
                imageUrl.setLoading(true);
                mLazyLoadListener.onLoad(imageUrl);
            }
            return;
        }

        final PhotoDraweeView draweeView = ((ImageHolder) holder).photoView;
        draweeView.setOnSingleTapListener(mSingleTapListener);
        draweeView.setOnLongPressListener(mLongPressListener);
        draweeView.setScrollMode(isVertical ? PhotoDraweeView.MODE_VERTICAL : PhotoDraweeView.MODE_HORIZONTAL);

        PipelineDraweeControllerBuilder builder = mControllerSupplier.get();
        switch (reader) {
            case READER_PAGE:
                builder.setControllerListener(new BaseControllerListener<ImageInfo>() {
                    @Override
                    public void onFinalImageSet(String id, ImageInfo imageInfo, Animatable animatable) {
                        if (imageInfo != null) {
                            imageUrl.setSuccess(true);
                            draweeView.update(imageUrl.getId(), imageInfo.getWidth(), imageInfo.getHeight());
                        }
                    }
                });
                break;
            case READER_STREAM:
                builder.setControllerListener(new BaseControllerListener<ImageInfo>() {
                    @Override
                    public void onFinalImageSet(String id, ImageInfo imageInfo, Animatable animatable) {
                        if (imageInfo != null) {
                            imageUrl.setSuccess(true);
                            if (isVertical) {
                                draweeView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            } else {
                                draweeView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                            }
                            draweeView.setAspectRatio((float) imageInfo.getWidth() / imageInfo.getHeight());
                            draweeView.update(imageUrl.getId(), imageInfo.getWidth(), imageInfo.getHeight());
                        }
                    }
                });
                break;
        }

        String[] urls = imageUrl.getUrls();
        ImageRequest[] request = new ImageRequest[urls.length];
        for (int i = 0; i != urls.length; ++i) {
            final String url = urls[i];
            ImageRequestBuilder imageRequestBuilder = ImageRequestBuilder
                    .newBuilderWithSource(Uri.parse(url));

            MangaPostprocessor processor = new MangaPostprocessor(imageUrl);
            processor.setPaging(isPaging);
            processor.setWhiteEdge(isWhiteEdge);
            imageRequestBuilder.setPostprocessor(processor);
            imageRequestBuilder.setRequestListener(new BaseRequestListener() {
                @Override
                public void onRequestSuccess(ImageRequest request, String requestId, boolean isPrefetch) {
                    imageUrl.setUrl(url);
                }
            });
            request[i] = imageRequestBuilder.build();
        }
        builder.setOldController(draweeView.getController()).setTapToRetryEnabled(true);
        draweeView.setController(builder.setFirstAvailableImageRequests(request).build());
    }

    public void setControllerSupplier(PipelineDraweeControllerBuilderSupplier supplier) {
        mControllerSupplier = supplier;
    }

    public void setSingleTapListener(OnSingleTapListener listener) {
        mSingleTapListener = listener;
    }

    public void setLongPressListener(OnLongPressListener listener) {
        mLongPressListener = listener;
    }

    public void setLazyLoadListener(OnLazyLoadListener listener) {
        mLazyLoadListener = listener;
    }

    public void setVertical(boolean vertical) {
        isVertical = vertical;
    }

    public void setPaging(boolean paging) {
        isPaging = paging;
    }

    public void setWhiteEdge(boolean whiteEdge) {
        isWhiteEdge = whiteEdge;
    }

    public void setReaderMode(@ReaderMode int reader) {
        this.reader = reader;
    }

    @Override
    public RecyclerView.ItemDecoration getItemDecoration() {
        switch (reader) {
            default:
            case READER_PAGE:
                return new RecyclerView.ItemDecoration() {
                    @Override
                    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                        outRect.set(0, 0, 0, 0);
                    }
                };
            case READER_STREAM:
                return new RecyclerView.ItemDecoration() {
                    @Override
                    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                        if (isVertical) {
                            outRect.set(0, 10, 0, 10);
                        } else {
                            outRect.set(10, 0, 10, 0);
                        }
                    }
                };
        }
    }

    /**
     * 假设一定找得到
     */
    public int getPositionByNum(int current, int num, boolean reverse) {
        while (mDataSet.get(current).getNum() != num) {
            current = reverse ? current - 1 : current + 1;
        }
        return current;
    }

    public int getPositionById(int id) {
        int size = mDataSet.size();
        for (int i = 0; i < size; ++i) {
            if (mDataSet.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    public void update(int id, String url) {
        for (int i = 0; i < mDataSet.size(); ++i) {
            ImageUrl imageUrl = mDataSet.get(i);
            if (imageUrl.getId() == id && imageUrl.isLoading()) {
                if (url == null) {
                    imageUrl.setLoading(false);
                    return;
                }
                imageUrl.setUrl(url);
                imageUrl.setLoading(false);
                imageUrl.setLazy(false);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public interface OnLazyLoadListener {
        void onLoad(ImageUrl imageUrl);
    }

}
