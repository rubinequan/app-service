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
import com.google.gson.JsonObject;

import java.util.*;

public class OssVoiceUtils {

    public static String getScene(String url) throws Exception{

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
                            return taskId;
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
        return "";
    }

    public static boolean pollingScanResult(IAcsClient client, String taskId) throws InterruptedException {
        int failCount = 0;
        boolean stop = false;
        do {
            // 设置每10秒查询一次。
            Thread.sleep(10 * 1000);
            JSONObject scanResult = getScanResult(client, taskId);
            if (scanResult == null || 200 != scanResult.getInteger("code")) {
                failCount++;
                System.out.println(taskId + ": get result fail, failCount=" + failCount);
                if (scanResult != null) {
                    System.out.println(taskId + ": errorMsg:" + scanResult.getString("msg"));
                }
                if (failCount > 20) {
                    break;
                }
                continue;
            }

            JSONArray taskResults = scanResult.getJSONArray("data");
            if (taskResults.isEmpty()) {
                System.out.println("failed");
                break;
            }

            for (Object taskResult : taskResults) {
                JSONObject result = (JSONObject) taskResult;
                Integer code = result.getInteger("code");
                if (280 == code) {
                    System.out.println(taskId + ": processing status: " + result.getString("msg"));
                } else if (200 == code) {
                    System.out.println(taskId + ": ========== SUCCESS ===========");
                    System.out.println(JSON.toJSONString(scanResult, true));
                    if(scanResult.get("data")!=null){
                        JSONObject results= JSONObject.parseObject(scanResult.get("data").toString());
                        //判断返回类型不等于normal正常的都拦截下来了
                        if(results.get("label")!=null && !results.get("label").toString().equals("normal")){
                            return false;
                        }
                    }
                    System.out.println(taskId + ": ========== SUCCESS ===========");
                    stop = true;
                    return true;
                } else {
                    System.out.println(taskId + ": ========== FAILED ===========");
                    System.out.println(JSON.toJSONString(scanResult, true));
                    System.out.println(taskId + ": ========== FAILED ===========");
                    stop = true;
                    return false;
                }
            }
        } while (!stop);
        return stop;
    }

    private static JSONObject getScanResult(IAcsClient client, String taskId) {
        VoiceAsyncScanResultsRequest getResultsRequest = new VoiceAsyncScanResultsRequest();
        getResultsRequest.setAcceptFormat(FormatType.JSON); // 指定API返回格式。
        getResultsRequest.setMethod(com.aliyuncs.http.MethodType.POST); // 指定请求方法。
        getResultsRequest.setEncoding("utf-8");
        getResultsRequest.setRegionId("cn-beijing");


        List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
        Map<String, Object> task1 = new LinkedHashMap<String, Object>();
        task1.put("taskId", taskId);
        tasks.add(task1);

        /**
         * 请务必设置超时时间。
         */
        getResultsRequest.setConnectTimeout(3000);
        getResultsRequest.setReadTimeout(6000);

        try {
            getResultsRequest.setHttpContent(JSON.toJSONString(tasks).getBytes("UTF-8"), "UTF-8", FormatType.JSON);

            HttpResponse httpResponse = client.doAction(getResultsRequest);
            if (httpResponse.isSuccess()) {
                return JSON.parseObject(new String(httpResponse.getHttpContent(), "UTF-8"));
            } else {
                System.out.println("response not success. status: " + httpResponse.getStatus());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        //返回true合法，返回fasle违规
        // 请替换成您的AccessKey ID、AccessKey Secret。
        IClientProfile profile = DefaultProfile
                .getProfile("oss-beijing", "LTAI5tN4daQRKSBMx865uP8y", "A28GboYEcZTbPjuuXLxv8lHRdZJyGG");
        final IAcsClient client = new DefaultAcsClient(profile);

        pollingScanResult(client, OssVoiceUtils.getScene("http://169.254.10.211:80/fs/2/2023/03/05/12/b3lncW13czJr-2-1677992120-GZE4nxJAfc72.mp3"));
        String taskId = OssVoiceUtils.getScene("http://169.254.10.211:80/fs/2/2023/03/05/12/b3lncW13czJr-2-1677992120-GZE4nxJAfc72.mp3");
        //String taskId = OssVoiceUtils.getScene("http://169.254.10.211:80/fs/2/2023/03/05/12/b3lncW13czJr-2-1677992176-bGJ64FfJoWii.mp3");
        pollingScanResult(client, taskId);
    }

    public static boolean getFlag(String amrUrl) {
        try {
            // 请替换成您的AccessKey ID、AccessKey Secret。
            IClientProfile profile = DefaultProfile
                    .getProfile("oss-beijing", "LTAI5tN4daQRKSBMx865uP8y", "A28GboYEcZTbPjuuXLxv8lHRdZJyGG");
            final IAcsClient client = new DefaultAcsClient(profile);
           return pollingScanResult(client, OssVoiceUtils.getScene(amrUrl));
        }catch (Exception e){
            System.out.println("e"+e.getMessage());
        }

        return false;
    }
}
