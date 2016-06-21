#KSYLiveSDK for Android用户手册
---
##由于一些原因,这个项目已经不在维护.
## This project is no longer maintenance ,very sorry
##SDK更新日志
  
  2015-11-13
  
  - [x] 修正了SmartsanOS下Mp4无法解析的问题
  - [x] 更新了主动丢帧策略

  2015-11-05
  
  - [x]	修正了一些状态切换导致的bug
  - [x]	增加了启动的错误回掉,防止在一些国产设备下,录音权限被禁用crash的问题[由于android6.0修改了权限模型,如果在Mainfest里声明targetAPI=23依然会Crash]
  - [x]	增加了闪光灯控制接口
  - [x]	更新了丢帧策略

  2015-10-19

  - [x]  修正了摄像头切换后，再次打开失败的问题
  - [x]  修正了切换摄像头时，时间戳未调整导致延时增大的问题
  - [x]  增加了推流数据发送状态回调
  - [x]  修正了在网络状态不佳时，延时增大的问题
  - [x]  修正了部分机型SPS和PPS获取不到导致崩溃的问题
  - [x]  增加了摄像头切换状态回调，修复频繁切换崩溃问题

##SDK说明
KSYLiveSDK for Android(以下简称SDK)是金山云官方推出的基于RTMP协议的Android推流器,意图是帮助开发这者速构建***直播类的Android应用***.本文档面向所有使用该SDK的开发人员、测试人员，要求有一定的Android编程经验。

###主要功能点
  - [x] 基于MediaRecorder实现
  - [x] 支持AAC音频编码 
  - [x] 支持H264视频编码 
  - [x] 支持RTMP协议推流
  - [x] 支持常见Android设备CPU指令集包括 ARM, ARMv7a, ARM64v8a, X86[选配]
  - [x] 支持前后置摄像头，以及动态切换 
  - [x] 支持自动对焦
  - [x] Min_API-14,兼容市场主流设备
  - [x] 支持根据网络情况的主动丢帧,避免延时过大
  - java层~~native层100%开源(部分底层代码还在优化整理,逐步上传中)~~
  - 留有MediaCodec和FFmpeg软编方案的接口,后期更多编码方案支持
  - 后期更多功能支持
  
###SDK下载地址
点击[下载SDK包](https://github.com/ks3sdk/KSYLiveAndroidSDK "我们使用githu进行托管")，包含demo程序。

###开发环境
本SDK使用了Android Studio + Gradle的方式进行构建,***一般不提供eclipse的支持***,目前开源提供工程项目,日后会发布AAR形式的SKD,并上传Gradle仓库。
目前使用Android Studio1.4 + gradle1.2.3构建通过.

###运行环境和兼容性
目前SDK使用MediaRecorder的方式利用Android硬件资源进行编码,主要是支持H264和AAC方式的编码,主要支持API14(android4.0)以上设备。

##集成使用引导

###SDK结构
- recordlib 推流器的核心组件,需要以library形式引入项目
- app SDK的demo工程,包含简单的使用Sample

###声明权限
- Android权限申明

```
	<!-- 使用权限 -->
    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.WAKE_LOCK"/>
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
	<!-- 硬件特性 -->
    <uses-feature android:name="android.hardware.camera" />
    <uses-feature android:name="android.hardware.camera.autofocus" />
```

###使用

- 可以直接clone工程之后 使用Android Studio的Import project(Eclipse ADT,Gradle,etc.)导入工程,app即为示例Demo,直接运行即可.

###集成
####初始化

- 创建KsyRecordClient实例 KsyRecordClient是SDK的核心控制器,一切交互通过这个类进行交互.KsyRecordClient为单例模式,全局只能创建一个.创建后必须先设置Config和预览的SurfaceView或TextureView

```
	client = KsyRecordClient.getInstance(getApplicationContext());
	client.setConfig(config);
	client.setDisplayPreview(mSurfaceView);

```
- KsyRecordClientConfig 为Client的配置,可以使用自带的Builder简单构建,config主要配置推流器音视频大小编码类型采样率等。必须要设置推流的server地址，简单配置可以直接使用VideoProfile进行初始化.

```
	KsyRecordClientConfig.Builder builder = new KsyRecordClientConfig.Builder();
	builder.setVideoProfile(CamcorderProfile.QUALITY_480P).setUrl(Constants.URL_DEFAULT);
	config = builder.build();
		
```

####配置

- KsyRecordClientConfig为Client的主要配置描述,主要是设置如下

```
    int mCameraType;//摄像头前后类型
    int mVoiceType;//
    int mAudioSampleRate;//音频采样率
    int mAudioBitRate;//音频比特码率
    int mAudioEncoder;//音频编码器(目前仅支持AAC)
    int mVideoFrameRate;//视频帧率(只在日后FFmpeg或者MediaCodec方式有效)
    int mVideoBitRate;//视频编比特码率
    int mDropFrameFrequency;//视频帧率(只在日后FFmpeg或者MediaCodec方式有效)
    int mVideoWidth;//视频宽度
    int mVideoHeight;//视频高度
    int mVideoEncoder;//视频编码器类型(目前仅支持H264)
    int mVideoProfile;//视频编码预设,对应CameraProfile
```

####主要动作

- 开始推流
```
 client.startRecord();
```

- 停止推流
```
 client.stopRecord();
```

- 切换前后摄像头
```
 client.switchCamera();
```

- 设置Camera预览Preview
```
 client.setDisplayPreview(SurfaceView);
 client.setDisplayPreview(TextureView);
```

####

##已知问题
- 切换摄像头可能导致一段时间的音视频不同步(avDistance>500ms),一般会在5S以内自动调整.
- 音视频同步问题,目前使用MediaRecorder的3GPP封装没有timestemp,需要手工编码TS
- 前后摄像头切换,导致音视频播放时候可能出现帧间隔过大的情况,切换摄像头时候,在某些手机上(Mi2A, Meizu MX4Pro)可能会导致音视频帧间隔改变导致的画面跳动卡顿等(1~3S左右).
- 其他已知问题请在ISSUE里提出,欢迎PR
