package com.example.myapplication.richTextUtil;

import java.io.Serializable;

/**
 * 日期 ---------- 维护人 ------------ 变更内容 --------
 * 2020/1/21        Sunshine          请填写变更内容
 */
public class ImageLineBean implements Serializable {
    public String imageTag = "";
    public int lineNum = -1;
    public int lineHeight = -1;
    public int textWordHeight = -1;
    public boolean isLastLine = false;

    public ImageLineBean(String imageTag) {
        this.imageTag = imageTag;
    }

    @Override
    public String toString() {
        return "ImageLineBean{" +
                "imageTag='" + imageTag + '\'' +
                ", lineNum=" + lineNum +
                ", lineHeight=" + lineHeight +
                ", textWordHeight=" + textWordHeight +
                ", isLastLine=" + isLastLine +
                '}';
    }
}
