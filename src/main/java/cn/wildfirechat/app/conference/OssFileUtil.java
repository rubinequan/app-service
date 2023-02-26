package cn.wildfirechat.app.conference;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.green.model.v20180509.FileAsyncScanRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;

import java.io.File;
import java.util.Arrays;

/**
 * 校验文件是否违规，返回false 既不通过
 */
public class OssFileUtil {

    public static boolean getScene(String url){
        IClientProfile profile = DefaultProfile.getProfile("oss-beijing", "LTAI5tN4daQRKSBMx865uP8y", "A28GboYEcZTbPjuuXLxv8lHRdZJyGG");
        DefaultProfile
                .addEndpoint("oss-beijing", "Green", "green.cn-beijing.aliyuncs.com");
        IAcsClient client = new DefaultAcsClient(profile);

        FileAsyncScanRequest fileAsyncScanRequest = new FileAsyncScanRequest();
        // 指定API返回格式。
        fileAsyncScanRequest.setAcceptFormat(FormatType.JSON);
        // 指定请求方法。
        fileAsyncScanRequest.setMethod(com.aliyuncs.http.MethodType.POST);
        fileAsyncScanRequest.setEncoding("utf-8");
        // textScenes：检测内容包含文本时，指定检测场景，取值：antispam。imageScenes：检测内容包含图片时，指定检测场景。
        JSONObject data = new JSONObject();
        data.put("textScenes", Arrays.asList("antispam"));
        data.put("imageScenes", Arrays.asList("porn", "ad"));
        JSONObject task = new JSONObject();
        task.put("dataId", "1");//检测数据ID
        task.put("url", url);
        data.put("tasks", Arrays.asList(task));
        fileAsyncScanRequest.setHttpContent(org.apache.commons.codec.binary.StringUtils.getBytesUtf8(data.toJSONString()),
                "UTF-8", FormatType.JSON);

        /**
         * 请设置超时时间，服务端全链路处理超时时间为10秒，请做相应设置。
         * 如果您设置的ReadTimeout小于服务端处理的时间，程序中会获得一个ReadTimeout异常。
         */
        fileAsyncScanRequest.setConnectTimeout(3000);
        fileAsyncScanRequest.setReadTimeout(10000);
        HttpResponse httpResponse = null;
        try {
            httpResponse = client.doAction(fileAsyncScanRequest);
            if (httpResponse.isSuccess()) {
                JSONObject scrResponse = JSON.parseObject(new String(httpResponse.getHttpContent(), "UTF-8"));
                System.out.println(JSON.toJSONString(scrResponse, true));
                int requestCode = scrResponse.getIntValue("code");
                // 每一个文件的检测结果。
                JSONArray taskResults = scrResponse.getJSONArray("data");
                if (200 == requestCode) {
                    for (Object taskResult : taskResults) {
                        // 单个文件的处理结果。
                        int taskCode = ((JSONObject) taskResult).getIntValue("code");
                        if (200 == taskCode) {
                            // 保存taskId用于轮询结果。
                            System.out.println(((JSONObject) taskResult).getString("taskId"));
                        } else {
                            // 单个文件处理失败，原因视具体的情况详细分析。
                            System.out.println("task process fail. task response:" + JSON.toJSONString(taskResult));
                        }
                    }
                    return true;
                } else {
                    /**
                     * 表明请求整体处理失败，原因视具体的情况详细分析。
                     */
                    System.out.println("the whole scan request failed. response:" + JSON.toJSONString(scrResponse));
                    return false;
                }
            } else {
                System.out.println("response not success. status:" + httpResponse.getStatus());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        OssFileUtil.getScene("https://img2.baidu.com/it/u=2823696894,1161736988&fm=253&fmt=auto&app=138&f=JPEG?w=400&h=400");
    }
}
