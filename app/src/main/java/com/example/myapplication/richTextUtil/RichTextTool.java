package com.example.myapplication.richTextUtil;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.example.myapplication.R;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static android.graphics.Bitmap.Config.ARGB_8888;

/**
 * created by: Sunshine at 2019/12/4
 * 图文混排富文本工具类
 */
public class RichTextTool {
    // 默认图片高度修正比例
    private static final float FIX_RATE_NORMAL = 1.15f;
    // 本地解析图片字典集合
    private static HashMap<String, Integer> localImgDictionary = new HashMap<>();
    private static ArrayList<String> localImgTag = new ArrayList<>();
    // img标签成功解析后的匹配
    private static final Pattern pattern = Pattern.compile("￼");

    // 有新的图文混排需要在这里加上就行了
    static {
        localImgDictionary.put("%本地图片春节%", R.mipmap.local1);
        localImgDictionary.put("%本地图片大牛%", R.mipmap.local2);
        localImgDictionary.put("%本地图片点燃%", R.mipmap.local3);

        localImgTag.add("%本地图片春节%");
        localImgTag.add("%本地图片大牛%");
        localImgTag.add("%本地图片点燃%");
    }

    /**
     * 判断文案内容中是否含有img标签
     *
     * @param content
     * @return
     */
    public boolean hadImgTag(String content) {
        Pattern pattern = Pattern.compile("<img src=[\\S]+>");
        for (int i = 0; i < localImgTag.size(); i++) {
            if (content.contains(localImgTag.get(i))) {
                return true;
            } else if (i + 1 == localImgTag.size()) {
                if (pattern.matcher(content).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * TextView富文本设置展示的方法
     *
     * @param textView TextView自己
     * @param content  设置展示的内容，需要加工成Html的格式类型才可以正常展示，html格式示例如下：
     *                 "<p><font size=\"3\" color=\"red\">设置了字号和颜色</font></p>" +
     *                 "<b><font size=\"5\" color=\"blue\">设置字体加粗 蓝色 5号</font></font></b></br>" +
     *                 "<h1>这个是H1标签</h1></br>" +
     *                 "<p>这里显示图片：</p><img src=\"https://img0.pconline.com.cn/pconline/1808/06/11566885_13b_thumb.jpg\">"
     */
    @SuppressLint("CheckResult")
    public void setRichText(final TextView textView, String content) {
        if (content == null || content.equals("") || textView == null)
            return;
        // 初始化每个图片标签所在行数的缓存List
        ArrayList<ImageLineBean> imgLineList = new ArrayList<>();
        // 富文本内容预处理
        content = contentPrefix(textView, content, imgLineList, false);
        // 将占位符文本替换为 <img> 标签的文本
        for (int i = 0; i < localImgTag.size(); i++) {
            String imageTag = localImgTag.get(i);
            content = content.replace(imageTag, "<img src=\"" + imageTag + "\">");
        }
        // 图片标签队列按顺序初步填充
        Html.fromHtml(content, source -> {
            imgLineList.add(new ImageLineBean(source));
            return null;
        }, null);
        // 正式开始给TextView设置文案
        final String finalContent = content;
        ArrayList<String> delayIndexArray = new ArrayList<>(); // 初始化图片标签解析完成的计数队列
        textView.setText(Html.fromHtml(content, source -> {
            // 参数初始化和预判断
            final LevelListDrawable mDrawable = new LevelListDrawable();
            if (source == null || source.equals("")) return mDrawable;
            // 初始化图文混排的图片高度调整比例
            float[] fixRatePlant = {FIX_RATE_NORMAL};
            // 获取App资源对象
            final Resources res = textView.getContext().getResources();
            // 由于此时不知道这些图片标签的综合信息，所以这里统一置空
            ImageLineBean imageLineBean = null;
            // 解析 img 标签下的图片内容
            boolean hadLocalImgTag = false;
            for (int i = 0; i < localImgTag.size(); i++) {
                String imageTag = localImgTag.get(i);
                if (source.equals(imageTag)) {
                    hadLocalImgTag = true;
                    Integer resId = localImgDictionary.get(imageTag);
                    if (resId != null)
                        drawLocalImg(res, textView, resId, mDrawable, imageLineBean, fixRatePlant);
                    // 检测TextView是否需要二次测量再展示
                    checkIsUseDelay(delayIndexArray, source, imgLineList, textView, finalContent);
                }
            }
            // 真实图片url地址解析
            if (!hadLocalImgTag) {
                Observable.create((ObservableOnSubscribe<Bitmap>) emitter -> {
                    Bitmap oldBmp = BitmapFactory.decodeStream(new URL(source).openStream());
                    if (oldBmp == null) return;
                    // 修改适应文本基线的图片高度，并通过数组的方式得到我们的计算比例
                    Bitmap fixBitmap = fixBitmap(oldBmp, textView, imageLineBean, fixRatePlant);
                    Bitmap resultBitmap = fixImgHeight2Text(fixBitmap, textView);
                    if (resultBitmap != null) emitter.onNext(resultBitmap);
                }).subscribeOn(Schedulers.io()) // 被订阅者的逻辑走io线程
                        .observeOn(AndroidSchedulers.mainThread()) // 订阅者的逻辑走主线程observeOn;
                        .subscribe(resultBitmap -> {
                            // 刷新TextView的文案
                            richTextRefresh(res, resultBitmap, textView, mDrawable, fixRatePlant[0]);
                            // 检测TextView是否需要二次测量再展示
                            checkIsUseDelay(delayIndexArray, source, imgLineList, textView, finalContent);
                        });
            }
            return mDrawable;
        }, null));
    }

    /**
     * 检测当前TextView是否需要二次延迟展示的方法
     */
    private void checkIsUseDelay(ArrayList<String> delayIndexArray, String source,
                                 ArrayList<ImageLineBean> imgLineList, TextView textView, String content) {
        delayIndexArray.add(source);
        if (textView.getLineSpacingExtra() != 0 || textView.getLineSpacingMultiplier() != 1) {
            textView.setVisibility(View.INVISIBLE);
            if (delayIndexArray.size() >= imgLineList.size()) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> setRichTextDelay(textView, content, imgLineList), 100);
            }
        } else {
            textView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * TextView富文本延迟设置展示的方法（当图文混排展示的TextView设置了行间距的时候，会调用此方法）
     *
     * @param textView TextView自己
     * @param content  设置展示的内容，需要加工成Html的格式类型才可以正常展示，html格式示例如下：
     *                 "<p><font size=\"3\" color=\"red\">设置了字号和颜色</font></p>" +
     *                 "<b><font size=\"5\" color=\"blue\">设置字体加粗 蓝色 5号</font></font></b></br>" +
     *                 "<h1>这个是H1标签</h1></br>" +
     *                 "<p>这里显示图片：</p><img src=\"https://img0.pconline.com.cn/pconline/1808/06/11566885_13b_thumb.jpg\">"
     */
    @SuppressLint("CheckResult")
    private void setRichTextDelay(final TextView textView, String content, ArrayList<ImageLineBean> imgLineList) {
        if (content == null || content.equals("") || textView == null)
            return;
        if (textView.getLineCount() <= 1) {
            textView.setVisibility(View.VISIBLE);
            return;
        }
        // 图文内容二次解析
        Observable.create((ObservableOnSubscribe<String>) emitter -> {
            // 富文本内容预处理
            String newContent = contentPrefix(textView, content, imgLineList, true);
            if (newContent != null) emitter.onNext(newContent);
        }).subscribeOn(Schedulers.io()) // 被订阅者的逻辑走io线程
                .observeOn(AndroidSchedulers.mainThread()) // 订阅者的逻辑走主线程observeOn;
                .subscribe(newContent -> {
                    textView.setText(Html.fromHtml(newContent, source -> {
                        // 参数初始化和预判断
                        final LevelListDrawable mDrawable = new LevelListDrawable();
                        if (source == null || source.equals("")) return mDrawable;
                        // 初始化图文混排的图片高度调整比例
                        float[] fixRatePlant = {FIX_RATE_NORMAL};
                        // 获取当前图片标签的綜合信息對象
                        ImageLineBean imageLineBean = null;
                        for (int i = 0; i < imgLineList.size(); i++) {
                            if (imgLineList.get(i).imageTag.equals(source)) {
                                imageLineBean = imgLineList.get(i);
                                imgLineList.remove(imageLineBean);
                                break;
                            }
                        }
                        final ImageLineBean finalImageLineBean = imageLineBean;
                        // 获取App资源对象
                        final Resources res = textView.getContext().getResources();
                        // 解析 img 标签下的图片内容
                        boolean hadLocalImgTag = false;
                        for (int i = 0; i < localImgTag.size(); i++) {
                            String imageTag = localImgTag.get(i);
                            if (source.equals(imageTag)) {
                                hadLocalImgTag = true;
                                Integer resId = localImgDictionary.get(imageTag);
                                if (resId != null)
                                    drawLocalImg(res, textView, resId, mDrawable, finalImageLineBean, fixRatePlant);
                            }
                        }
                        // 真实图片url地址解析
                        if (!hadLocalImgTag)
                            Observable.create((ObservableOnSubscribe<Bitmap>) emitter -> {
                                Bitmap oldBmp = BitmapFactory.decodeStream(new URL(source).openStream());
                                if (oldBmp == null) return;
                                // 修改适应文本基线的图片高度，并通过数组的方式得到我们的计算比例
                                Bitmap fixBitmap = fixBitmap(oldBmp, textView, finalImageLineBean, fixRatePlant);
                                Bitmap resultBitmap = fixImgHeight2Text(fixBitmap, textView);
                                if (resultBitmap != null) emitter.onNext(resultBitmap);
                            }).subscribeOn(Schedulers.io()) // 被订阅者的逻辑走io线程
                                    .observeOn(AndroidSchedulers.mainThread()) // 订阅者的逻辑走主线程observeOn;
                                    .subscribe(resultBitmap -> {
                                        // 刷新TextView的文案
                                        richTextRefresh(res, resultBitmap, textView, mDrawable, fixRatePlant[0]);
                                    });
                        return mDrawable;
                    }, null));
                    textView.setVisibility(View.VISIBLE);
                });
    }

    /**
     * 富文本绘制本地图片资源方法
     *
     * @param res
     * @param textView
     * @param localResId
     * @return
     */
    private LevelListDrawable drawLocalImg(Resources res, TextView textView, int localResId, LevelListDrawable mDrawable,
                                           ImageLineBean imageLineBean, float[] fixRatePlant) {
        if (localResId == 0) return mDrawable;
        Bitmap oldBmp = BitmapFactory.decodeResource(res, localResId);
        if (oldBmp == null) return mDrawable;
        // 修改适应文本基线的图片高度，并通过数组的方式得到我们的计算出来的比例
        Bitmap fixBitmap = fixBitmap(oldBmp, textView, imageLineBean, fixRatePlant);
        Bitmap resultBitmap = fixImgHeight2Text(fixBitmap, textView);
        if (resultBitmap == null) return mDrawable;
        // 刷新TextView的文案
        richTextRefresh(res, resultBitmap, textView, mDrawable, fixRatePlant[0]);
        return mDrawable;
    }

    /**
     * 将Bitmap绘制到一个全新且高度稍大点儿的画布上，让图文混排可以居中展示
     *
     * @param oldBmp
     * @return
     */
    private Bitmap fixBitmap(Bitmap oldBmp, TextView textView, ImageLineBean imageLineBean, float[] fixRatePlant) {
        Bitmap fixBitmap = null;
        // 计算文字绘制基线的高度和当前所属行像素高度的比例，从而得出图文混排时，图片底部应该多出多少空白高度
        if (textView.getLayout() == null) textView.measure(0, 0);
        if (imageLineBean != null && imageLineBean.lineNum < 0) imageLineBean.lineNum = 0;
        if (imageLineBean != null && (textView.getLineSpacingMultiplier() != 1 || textView.getLineSpacingExtra() != 0)) {
            if (imageLineBean.lineHeight > 0 && imageLineBean.textWordHeight > 0)
                fixRatePlant[0] = imageLineBean.lineHeight * 0.90f / imageLineBean.textWordHeight;
        }
        // 根据最新比例拓展图片的宽度和高度
        int fixBitmapHeight = (int) (oldBmp.getHeight() * fixRatePlant[0]);
        fixBitmap = Bitmap.createBitmap(oldBmp.getWidth(), fixBitmapHeight, ARGB_8888);
        Canvas canvas = new Canvas(fixBitmap);
        canvas.drawBitmap(oldBmp, 0f, 0f, null);
        oldBmp.recycle();
        return fixBitmap;
    }

    /**
     * 根据TextView第一行的文字高度调整图片的宽高
     *
     * @param fixBitmap
     * @param textView
     * @return
     */
    private Bitmap fixImgHeight2Text(Bitmap fixBitmap, TextView textView) {
        if (textView.getLayout() == null)
            textView.measure(0, 0);
        int firstTextHeight = textView.getLayout().getLineBaseline(0) - textView.getPaddingTop();
        float rate = firstTextHeight * 1f / fixBitmap.getHeight();
        int dstWidth = (int) (fixBitmap.getWidth() * rate);
        int dstHeight = (int) (fixBitmap.getHeight() * rate);
        Bitmap newBitmap = Bitmap.createScaledBitmap(fixBitmap, dstWidth, dstHeight, false);
        fixBitmap.recycle();
        return newBitmap;
    }

    /**
     * TextView 设置富文时的刷新方法
     *
     * @param res       App的Resource资源对象
     * @param newBitmap 图文混排的图片
     * @param textView  需要刷新的TextView
     */
    private void richTextRefresh(Resources res, Bitmap newBitmap, TextView textView, LevelListDrawable mDrawable, float fixRateReal) {
        if (res == null || newBitmap == null || textView == null || newBitmap.isRecycled())
            return;
        // 新的图片赋值给mDrawable
        Drawable result = new BitmapDrawable(res, newBitmap);
        mDrawable.addLevel(1, 1, result);
        mDrawable.setBounds(0, -(int) (newBitmap.getHeight() * (fixRateReal - 1f)), (int) (newBitmap.getWidth() * fixRateReal), newBitmap.getHeight());
        mDrawable.setLevel(1);
        // 重设文字
        CharSequence charSequence = textView.getText();
        textView.setText(charSequence);
        textView.invalidate();
    }

    /**
     * 图文混排内容预处理方法
     *
     * @param content
     * @return
     */
    private String contentPrefix(TextView textView, String content, ArrayList<ImageLineBean> imgLineList, boolean isGetImgLine) {
        if (isGetImgLine) {
            String hadText = textView.getText().toString();
            int hadfixindex = 0;
            for (int i = 0; i < textView.getLineCount(); i++) {
                // 截取文案以获得当前行数的文字
                String splitResult = TextUtils.ellipsize(hadText, textView.getPaint(), textView.getWidth(), TextUtils.TruncateAt.END).toString();
                if (splitResult.endsWith("…")) // 截掉省略号
                    splitResult = splitResult.substring(0, splitResult.length() - 1);
                // 当前行文字成功获取，TextView正在展示的全量文案裁掉这一行的文字
                hadText = hadText.replace(splitResult, "");
                // 计算当前行数的image标签个数
                Matcher matcher = pattern.matcher(splitResult);
                int count = 0;
                while (matcher.find()) count++;
                // 逐个对图片标签的所在行数赋值
                for (int j = hadfixindex; j < hadfixindex + count; j++) {
                    if (j >= 0 && j < imgLineList.size()) {
                        ImageLineBean imageLineBean = imgLineList.get(j);
                        // 赋值当前的行数
                        imageLineBean.lineNum = i;
                        // 赋值当前的行高
                        Rect rect = new Rect();
                        textView.getLineBounds(i, rect);
                        imageLineBean.lineHeight = Math.abs(rect.bottom - rect.top);
                        // 获取统一的文字高度
                        imageLineBean.textWordHeight = textView.getLayout().getLineBaseline(0) - textView.getPaddingTop();
                        // 注明是否是最后一行
                        if (i == textView.getLineCount() - 1) imageLineBean.isLastLine = true;
                    }
                }
                hadfixindex += count;
            }
            return content;
        } else {
            // 最后加一空格是为了防止单个图片另起一行的时候图片顶部被切掉或者和上一行重叠
            return content + " ";
        }
    }
}
