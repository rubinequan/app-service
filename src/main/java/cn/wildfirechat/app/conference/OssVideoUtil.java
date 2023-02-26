package cn.wildfirechat.app.conference;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.green.model.v20180509.VideoAsyncScanRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 校验视频是否违规，返回false 既不通过
 */
public class OssVideoUtil {

    public static boolean getScene(String url) throws Exception{
        IClientProfile profile = DefaultProfile.getProfile("oss-beijing", "LTAI5tN4daQRKSBMx865uP8y", "A28GboYEcZTbPjuuXLxv8lHRdZJyGG");
        DefaultProfile.addEndpoint("oss-beijing", "Green", "green.cn-beijing.aliyuncs.com");
        IAcsClient client = new DefaultAcsClient(profile);

        VideoAsyncScanRequest videoAsyncScanRequest = new VideoAsyncScanRequest();
        videoAsyncScanRequest.setAcceptFormat(FormatType.JSON); // 指定API返回格式。
        videoAsyncScanRequest.setMethod(com.aliyuncs.http.MethodType.POST); // 指定请求方法。

        List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
        Map<String, Object> task = new LinkedHashMap<String, Object>();
        task.put("dataId", UUID.randomUUID().toString());
        task.put("url", url);//请填写公网可访问的视频HTTP、HTTPS URL地址

        tasks.add(task);
        /**
         * 设置要检测的场景。计费是依据此处传递的场景计算。
         * 视频默认1秒截取一帧，您可以自行控制截帧频率。收费按照视频的截帧数量以及每一帧的检测场景计算。
         * 举例：1分钟的视频截帧60张，检测色情（对应场景参数porn）和暴恐涉政（对应场景参数terrorism）2个场景，收费按照60张色情+60张暴恐涉政进行计费。
         */
        JSONObject data = new JSONObject();
        data.put("scenes", Arrays.asList("porn", "terrorism"));
        data.put("tasks", tasks);
        data.put("callback", "http://www.aliyundoc.com/xxx.json");
        data.put("seed", "yourPersonalSeed");

        videoAsyncScanRequest.setHttpContent(data.toJSONString().getBytes("UTF-8"), "UTF-8", FormatType.JSON);

        /**
         * 请务必设置超时时间。
         */
        videoAsyncScanRequest.setConnectTimeout(3000);
        videoAsyncScanRequest.setReadTimeout(6000);
        try {
            HttpResponse httpResponse = client.doAction(videoAsyncScanRequest);

            if (httpResponse.isSuccess()) {
                JSONObject scrResponse = JSON.parseObject(new String(httpResponse.getHttpContent(), "UTF-8"));
                System.out.println(JSON.toJSONString(scrResponse, true));
                int requestCode = scrResponse.getIntValue("code");
                // 每一个视频的检测结果。
                JSONArray taskResults = scrResponse.getJSONArray("data");
                if (200 == requestCode) {
                    for (Object taskResult : taskResults) {
                        // 单个视频的处理结果。
                        int taskCode = ((JSONObject) taskResult).getIntValue("code");
                        if (200 == taskCode) {
                            // 保存taskId用于轮询结果。
                            System.out.println(((JSONObject) taskResult).getString("taskId"));
                            return true;
                        } else {
                            // 单个视频处理失败，原因视具体的情况详细分析,扫描涉黄，中止
                            System.out.println("task process fail. task response:" + JSON.toJSONString(taskResult));
                            return false;
                        }
                    }
                } else {
                    /**
                     * 表明请求整体处理失败，原因视具体的情况详细分析。
                     */
                    System.out.println("the whole scan request failed. response:" + JSON.toJSONString(scrResponse));
                }
            } else {
                System.out.println("response not success. status:" + httpResponse.getStatus());
            }
        } catch (ServerException e) {
            e.printStackTrace();
        } catch (ClientException e) {
            e.printStackTrace();
        }
        return false;
    }


    public static void main(String[] args)  throws Exception{
        OssVideoUtil.getScene("https://haokan.baidu.com/v?pd=wisenatural&vid=13755902194268986696");
    }
}
