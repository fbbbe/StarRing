
package com.example.starring;

import android.app.Application;
import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

public class StarRingApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Map<String, String> config = new HashMap<>();

        // Cloudinary 대시보드에서 확인한 Cloud name 입력
        config.put("cloud_name", "여기에_CLOUD_NAME");

        MediaManager.init(this, config);
    }
}
