package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;

import com.example.myapplication.richTextUtil.RichTextTool;

public class MainActivity extends AppCompatActivity {
    RichTextTool richTextTool = new RichTextTool();

    @Override
    @SuppressLint("SetTextI18n")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv_raw = findViewById(R.id.tv_raw);
        TextView tv_text = findViewById(R.id.tv_text);

        String content = "2020年 %本地图片春节% 节，和IT大 %本地图片大牛% 们一起在极客时间"
                + " %本地图片点燃% 部落"
                + " <img src=\"http://www.5tu.cn/attachments/dvbbs/2008-1/2008139144488392.gif\"/>";

        tv_raw.setText("工具类解析前：" + content);
        richTextTool.setRichText(tv_text, "工具类解析后：" + content);
    }
}
