### 阅读对象
        本文档面向所有使用该SDK的开发人员, 测试人员等, 要求读者具有一定的Android编程开发经验.
### 介绍
        该工具实现了一种将数据流推送到流媒体服务器上的高效方案，我们提供简洁的api供调用者使用
        ，我们不仅提供了丰富的个性化接口，而且用户还可以方便的进行二次开发。
### 主要功能
        1.  音频编码：AAC
        2.  视频编码：H.264
        3.  推流协议：RTMP
        4.  视频分辨率：640x360 
        5.  屏幕朝向： 竖屏
        6.  iOS摄像头：前, 后置摄像头（可动态切换）
        7.  音视频目标码率：可设
        8.  根据网络带宽自适应调整视频的码率
        9.  闪光灯：开/关
        10. 自动美颜，磨皮
        11. H265支持

### 使用方法
       1. 环境配置
          安装android studio 和ndk 以及java运行环境，ndk版本为android-ndk64-r10e-linux-x86_64.tar.bz2,
          交错编译请查看auto.sh文件
       2. sdk使用方法
          在项目中导入'package com.ksy.ksyrecordsdk;'
          
    创建实例代码：
    'private void setupRecord() {
        client = KsyRecordClient.getInstance(getApplicationContext());
        client.setConfig(config);
        client.setDisplayPreview(mSurfaceView);
        client.setOrientationActivity(this);
        client.setNetworkChangeListener(this);
        client.setPushStreamStateListener(this);
        client.setSwitchCameraStateListener(this);
        client.setStartListener(this);
        client.setOnClientErrorListener(this);
    }'
    开启推流：
    ' private void startRecord() {
        Log.d(Constants.LOG_TAG, "startRecord..");
        try {
            client.startRecord();
            mRecording = true;
            mFab.setImageDrawable(getResources().getDrawable(R.mipmap.btn_stop));
        } catch (KsyRecordException e) {
            e.printStackTrace();
            Log.d(Constants.LOG_TAG, "Client Error, reason = " + e.getMessage());
        }
    }'
    停止推流：
    ’ protected void onStop() {
        super.onStop();
        showToast("程序关闭");
        orientationObserver.disable();
        client.unregisterNetworkMonitor();
        if (mRecording) {
            stopRecord();
        }
    }‘
