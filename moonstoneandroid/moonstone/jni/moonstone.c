/*
 * Copyright (C) 2009 moonstone inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include<stdio.h>
#include <string.h>
#include <jni.h>
#include "rtmp_sys.h"
#include "log.h"
/* This is a trivial JNI example where we use a native method
 * to return a new VM String. See the corresponding Java source
 * file located at:
 *
 *   apps/samples/hello-jni/project/src/com/example/hellojni/HelloJni.java
 */
///////////////////////////////////////////////////////////////////
RTMP* g_hrtmp=NULL;             //handle of rtmp
char*  g_server_url=NULL;

///////////////////////////////////////////////////////////////////

static const char *classPathName = "com/ksy/recordlib/service/core/KsyRecordSender";  
  


static char* jstringTostring(JNIEnv* env, jstring jstr)
{
       char* rtn = NULL;
       jclass clsstring = (*env)->FindClass(env, "java/lang/String");
       jstring strencode = (*env)->NewStringUTF(env, "utf-8");
       jmethodID mid = (*env)->GetMethodID(env, clsstring, "getBytes", "(Ljava/lang/String;)[B");
       jbyteArray barr= (jbyteArray)(*env)->CallObjectMethod(env, jstr, mid, strencode);
       jsize alen = (*env)->GetArrayLength(env, barr);
       jbyte* ba = (*env)->GetByteArrayElements(env, barr, JNI_FALSE);
       if (alen > 0)
       {
                 rtn = (char*)malloc(alen + 1);
                 memcpy(rtn, ba, alen);
                 rtn[alen] = 0;
       }
       (*env)->ReleaseByteArrayElements(env, barr, ba, 0);
       return rtn;
}
jint static moonstone_set_out_url( JNIEnv* env,jobject thiz,jstring url )
{
   if(g_server_url) free(g_server_url);
    g_server_url=jstringTostring(env,url);
    return 0;

}
jint static moonstone_open( JNIEnv* env, jobject thiz )
{
 
   if(g_hrtmp) return -1;
   g_hrtmp=RTMP_Alloc();
   if(!g_hrtmp) return -2;
   RTMP_Init(g_hrtmp);
   int err=RTMP_SetupURL(g_hrtmp,g_server_url);

   if(err <=0) return  err;
   RTMP_EnableWrite(g_hrtmp);
   err = RTMP_Connect(g_hrtmp,NULL);
    
   if(err <=0) return err;
   err = RTMP_ConnectStream(g_hrtmp,0);
   
   if(err <=0) return -5;
    
    return 0;

}
jint static moonstone_close( JNIEnv* env,jobject thiz )
{
	
    if(g_server_url) 
    {
	free(g_server_url);
	g_server_url=NULL;
    }
    RTMP_Close(g_hrtmp);
    RTMP_Free(g_hrtmp);
    g_hrtmp=NULL;
    return 0;

}
jint static moonstone_write( JNIEnv* env,jobject thiz,jbyteArray buffer,jint size )
{
   int ret=0;
   char *chArr = (char*)(*env)->GetByteArrayElements(env,buffer,NULL);

    //if(!RTMP_IsConnected(g_hrtmp)) return -1;
    ret=RTMP_Write(g_hrtmp,chArr,size);
    (*env)->ReleaseByteArrayElements(env,buffer,chArr,0);
    /*
    RTMPPacket rtmp_packet;
    RTMPPacket_Reset(&rtmp_packet);
    RTMPPacket_Alloc(&rtmp_packet,size);
    rtmp_packet.m_packetType=0;//包类型
    rtmp_packet.m_nBodySize=size;
    rtmp_packet.m_nTimeStamp=0;
    rtmp_packet.m_nChannel=4;
    rtmp_packet.m_headerType=RTMP_PACKET_SIZE_LARGE;
    rtmp_packet.m_nInfoField2=g_hrtmp->m_stream_id;
    memcpy(rtmp_packet.m_body,buffer,size);
    RTMP_SendPacket(g_hrtmp,&rtmp_packet,0);
    RTMPPacket_Free(&rtmp_packet);*/
    return ret;
}
  
static JNINativeMethod methods[] = {  
  
  {"_set_output_url", "(Ljava/lang/String;)I", (void*)moonstone_set_out_url },  
  {"_open", "()I", (void*)moonstone_open }, 
  {"_close", "()I", (void*)moonstone_close }, 
  {"_write", "([BI)I", (void*)moonstone_write },  
  
};  
  
  
/* 
 
 * Register several native methods for one class. 
 
 */  
  
static int registerNativeMethods(JNIEnv* env, const char* className,  
    JNINativeMethod* gMethods, int numMethods)  
{  
    jclass clazz;  
  
    clazz = (*env)->FindClass(env, className);  
    if (clazz == NULL)  
        return JNI_FALSE;  
  
    if ((*env)->RegisterNatives(env, clazz, gMethods, numMethods) < 0)  
    {  
    
        return JNI_FALSE;  
    }  
  
    return JNI_TRUE;  
}  
/* 
 
 * Register native methods for all classes we know about. 
 
 * 
 
 * returns JNI_TRUE on success. 
 
 */  
  
static int registerNatives(JNIEnv* env)  
  
{  
  
  if (!registerNativeMethods(env, classPathName,  
  
                 methods, sizeof(methods) / sizeof(methods[0]))) {  
  
    return JNI_FALSE;  
  
  }  
  
  return JNI_TRUE;  
  
}  
/* 
 
 * This is called by the VM when the shared library is first loaded. 
 
 */  
  
jint JNI_OnLoad(JavaVM* vm, void* reserved)  
{  
    JNIEnv* env = NULL;  
    jint result = 0;  
 
    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK)  
        goto bail;  
  
  
  
    if (!registerNatives(env))  
        goto bail;  
  
    /* success -- return valid version number */  
    result = JNI_VERSION_1_4;  
  
bail:  
   
    return result;  
} 


