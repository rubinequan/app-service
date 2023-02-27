package cn.wildfirechat.app.conference;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.green.model.v20180509.VoiceAsyncScanRequest;
import com.aliyuncs.green.model.v20180509.VoiceAsyncScanResultsRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;

import java.util.*;

public class OssVoiceUtils {

    public static boolean getScene(String url) throws Exception{

        // 请替换成您的AccessKey ID、AccessKey Secret。
        IClientProfile profile = DefaultProfile.getProfile("oss-beijing", "LTAI5tN4daQRKSBMx865uP8y", "A28GboYEcZTbPjuuXLxv8lHRdZJyGG");
        final IAcsClient client = new DefaultAcsClient(profile);

        VoiceAsyncScanRequest asyncScanRequest = new VoiceAsyncScanRequest();
        asyncScanRequest.setAcceptFormat(FormatType.JSON); // 指定API返回格式。
        asyncScanRequest.setMethod(com.aliyuncs.http.MethodType.POST); // 指定请求方法。
        asyncScanRequest.setRegionId("cn-beijing");
        asyncScanRequest.setConnectTimeout(3000);
        asyncScanRequest.setReadTimeout(6000);

        List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
        Map<String, Object> task1 = new LinkedHashMap<String, Object>();
        // 请将下面的地址修改为要检测的语音文件的地址。
        task1.put("url", url);
        tasks.add(task1);
        JSONObject data = new JSONObject();

        System.out.println("==========Task count:" + tasks.size());
        data.put("scenes", Arrays.asList("antispam"));
        data.put("tasks", tasks);
        // 如果是语音流检测，则修改为true。
        data.put("live", false);
        asyncScanRequest.setHttpContent(data.toJSONString().getBytes("UTF-8"), "UTF-8", FormatType.JSON);
        System.out.println(JSON.toJSONString(data, true));

        try {
            HttpResponse httpResponse = client.doAction(asyncScanRequest);

            if (httpResponse.isSuccess()) {
                JSONObject scrResponse = JSON.parseObject(new String(httpResponse.getHttpContent(), "UTF-8"));
                System.out.println(JSON.toJSONString(scrResponse, true));
                if (200 == scrResponse.getInteger("code")) {
                    JSONArray taskResults = scrResponse.getJSONArray("data");
                    for (Object taskResult : taskResults) {
                        Integer code = ((JSONObject) taskResult).getInteger("code");
                        if (200 == code) {
                            final String taskId = ((JSONObject) taskResult).getString("taskId");
                            System.out.println("submit async task success, taskId = [" + taskId + "]");
                        } else {
                            System.out.println("task process fail: " + code);
                        }
                    }
                } else {
                    System.out.println("detect not success. code: " + scrResponse.getInteger("code"));
                }
            } else {
                System.out.println("response not success. status: " + httpResponse.getStatus());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        //返回true合法，返回fasle违规
        System.out.println("是否违规："+OssVoiceUtils.getScene("http://169.254.10.211/fs/2/2023/02/27/23/OXlncW13czJr-2-1677512219-dQLXospIopLz.mp3"));
    }
}
