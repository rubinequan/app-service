package cn.wildfirechat.app;

import cn.wildfirechat.app.annotation.Log;
import cn.wildfirechat.app.conference.OssImgUtil;
import cn.wildfirechat.app.conference.OssMessageUtil;
import cn.wildfirechat.app.jpa.*;
import cn.wildfirechat.app.pojo.*;
import cn.wildfirechat.app.shiro.AuthDataSource;
import cn.wildfirechat.app.shiro.PhoneCodeToken;
import cn.wildfirechat.app.shiro.TokenAuthenticationToken;
import cn.wildfirechat.app.sms.SmsService;
import cn.wildfirechat.app.tools.RateLimiter;
import cn.wildfirechat.app.tools.ShortUUIDGenerator;
import cn.wildfirechat.app.tools.Utils;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.*;
import cn.wildfirechat.proto.ProtoConstants;
import cn.wildfirechat.sdk.*;
import cn.wildfirechat.sdk.model.IMResult;
import com.aliyun.oss.*;
import com.aliyun.oss.model.PutObjectRequest;
import com.google.gson.Gson;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpProtocol;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import io.minio.MinioClient;
import io.minio.PutObjectOptions;
import io.minio.errors.MinioException;
import org.apache.commons.io.FileUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.crypto.hash.Sha1Hash;
import org.apache.shiro.subject.Subject;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static cn.wildfirechat.app.RestResult.RestCode.*;
import static cn.wildfirechat.app.jpa.PCSession.PCSessionStatus.*;

@org.springframework.stereotype.Service
public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

    @Autowired
    private SmsService smsService;

    @Autowired
    private IMConfig mIMConfig;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private TaskCommpal taskCommpal;

    @Autowired
    private UserPasswordRepository userPasswordRepository;

    @Value("${sms.super_code}")
    private String superCode;

    @Value("${logs.user_logs_path}")
    private String userLogPath;

    @Autowired
    private ShortUUIDGenerator userNameGenerator;

    @Autowired
    private AuthDataSource authDataSource;

    @Autowired
    private ReportRepository reportRepository;

    private RateLimiter rateLimiter;

    @Value("${wfc.compat_pc_quick_login}")
    protected boolean compatPcQuickLogin;

    @Value("${media.server.media_type}")
    private int ossType;

    @Value("${media.server_url}")
    private String ossUrl;

    @Value("${media.access_key}")
    private String ossAccessKey;

    @Value("${media.secret_key}")
    private String ossSecretKey;

    @Value("${media.bucket_general_name}")
    private String ossGeneralBucket;
    @Value("${media.bucket_general_domain}")
    private String ossGeneralBucketDomain;

    @Value("${media.bucket_image_name}")
    private String ossImageBucket;
    @Value("${media.bucket_image_domain}")
    private String ossImageBucketDomain;

    @Value("${media.bucket_voice_name}")
    private String ossVoiceBucket;
    @Value("${media.bucket_voice_domain}")
    private String ossVoiceBucketDomain;

    @Value("${media.bucket_video_name}")
    private String ossVideoBucket;
    @Value("${media.bucket_video_domain}")
    private String ossVideoBucketDomain;


    @Value("${media.bucket_file_name}")
    private String ossFileBucket;
    @Value("${media.bucket_file_domain}")
    private String ossFileBucketDomain;

    @Value("${media.bucket_sticker_name}")
    private String ossStickerBucket;
    @Value("${media.bucket_sticker_domain}")
    private String ossStickerBucketDomain;

    @Value("${media.bucket_moments_name}")
    private String ossMomentsBucket;
    @Value("${media.bucket_moments_domain}")
    private String ossMomentsBucketDomain;

    @Value("${media.bucket_favorite_name}")
    private String ossFavoriteBucket;
    @Value("${media.bucket_favorite_domain}")
    private String ossFavoriteBucketDomain;

    @Value("${local.media.temp_storage}")
    private String ossTempPath;

    @Value("${portrait.address}")
    private String portraitAddress;

    private ConcurrentHashMap<String, Boolean> supportPCQuickLoginUsers = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        AdminConfig.initAdmin(mIMConfig.admin_url, mIMConfig.admin_secret);
        rateLimiter = new RateLimiter(60, 200);
    }

    private String getIp() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = requestAttributes.getRequest();
        String ip = request.getHeader("X-Real-IP");
        if (!StringUtils.isEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.isEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // ?????????????????????????????????IP????????????????????????IP???
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            } else {
                return ip;
            }
        } else {
            return request.getRemoteAddr();
        }
    }

    private int getUserStatus(String mobile) {
        try {
            IMResult<InputOutputUserInfo> inputOutputUserInfoIMResult = UserAdmin.getUserByMobile(mobile);
            if(inputOutputUserInfoIMResult != null && inputOutputUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                IMResult<OutputUserStatus> outputUserStatusIMResult = UserAdmin.checkUserBlockStatus(inputOutputUserInfoIMResult.getResult().getUserId());
                if(outputUserStatusIMResult != null && outputUserStatusIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    return outputUserStatusIMResult.getResult().getStatus();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
    @Override
    public RestResult sendLoginCode(String mobile) {
        String remoteIp = getIp();
        LOG.info("request send sms from {}", remoteIp);

        //????????????IP?????????????????????
        //?????? cn.wildfirechat.app.shiro.AuthDataSource.Count ??????????????????????????????
//        if (!rateLimiter.isGranted(remoteIp)) {
//            return RestResult.result(ERROR_SEND_SMS_OVER_FREQUENCY.code, "IP " + remoteIp + " ??????????????????", null);
//        }

        try {
            //???????????????????????????
            //https://docs.wildfirechat.cn/server/admin_api/user_api.html#??????????????????
            int userStatus = getUserStatus(mobile);
            if(userStatus == 2) {
                return RestResult.error(ERROR_USER_FORBIDDEN);
            }

            String code = Utils.getRandomCode(6);
            RestResult.RestCode restCode = authDataSource.insertRecord(mobile, code);

            if (restCode != SUCCESS) {
                return RestResult.error(restCode);
            }


            restCode = smsService.sendCode(mobile, code);
            if (restCode == RestResult.RestCode.SUCCESS) {
                return RestResult.ok(restCode);
            } else {
                authDataSource.clearRecode(mobile);
                return RestResult.error(restCode);
            }
        } catch (Exception e) {
            // json????????????
            e.printStackTrace();
            authDataSource.clearRecode(mobile);
        }
        return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
    }

    @Override
    public RestResult sendResetCode(String mobile) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        String remoteIp = getIp();
        LOG.info("request send sms from {}", remoteIp);

        if (StringUtils.isEmpty(userId)) {
            if (StringUtils.isEmpty(mobile)) {
                return RestResult.error(ERROR_INVALID_PARAMETER);
            }
        } else {
            try {
                IMResult<InputOutputUserInfo> outputUserInfoIMResult = UserAdmin.getUserByUserId(userId);
                if (outputUserInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    mobile = outputUserInfoIMResult.getResult().getMobile();
                } else {
                    if (StringUtils.isEmpty(mobile)) {
                        return RestResult.error(ERROR_NOT_EXIST);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (StringUtils.isEmpty(mobile)) {
                    return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
                }
            }
        }

        //????????????IP?????????????????????
        //?????? cn.wildfirechat.app.shiro.AuthDataSource.Count ??????????????????????????????
        if (!rateLimiter.isGranted(remoteIp)) {
            return RestResult.result(ERROR_SEND_SMS_OVER_FREQUENCY.code, "IP " + remoteIp + " ??????????????????", null);
        }

        //???????????????????????????
        //https://docs.wildfirechat.cn/server/admin_api/user_api.html#??????????????????
        int userStatus = getUserStatus(mobile);
        if(userStatus == 2) {
            return RestResult.error(ERROR_USER_FORBIDDEN);
        }

        try {
            String code = Utils.getRandomCode(6);
            RestResult.RestCode restCode = smsService.sendCode(mobile, code);
            if (restCode == RestResult.RestCode.SUCCESS) {
                Optional<UserPassword> optional = userPasswordRepository.findById(userId);
                UserPassword up = optional.orElseGet(() -> new UserPassword(userId));
                up.setResetCode(code);
                up.setResetCodeTime(System.currentTimeMillis());
                userPasswordRepository.save(up);
                return RestResult.ok(restCode);
            } else {
                return RestResult.error(restCode);
            }
        } catch (Exception e) {
            // json????????????
            e.printStackTrace();
        }
        return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
    }

    @Override
    public RestResult loginWithMobileCode(HttpServletResponse httpResponse, String mobile, String code, String clientId, int platform) {
        try{
            IMResult<InputOutputUserInfo> userResult = UserAdmin.getUserByMobile(mobile);
            if (userResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
                return RestResult.error(USER_NOT_EXISTS);
            }
        }catch (Exception e){
            return RestResult.error(USER_NOT_EXISTS);
        }

        if(StringUtils.isEmpty(code)){
            return RestResult.error(CODE_NOT_EXISTS);
        }
//        RestResult.RestCode restCode = smsService.sendCode(mobile, code);
//        if (restCode != RestResult.RestCode.SUCCESS) {
//            return RestResult.error(CODE_ERROR);
//        }
        Subject subject = SecurityUtils.getSubject();
        // ???????????????????????? token????????????
        PhoneCodeToken token = new PhoneCodeToken(mobile, code);
        // ??????????????????
        try {
            subject.login(token);
        } catch (UnknownAccountException uae) {
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        } catch (IncorrectCredentialsException ice) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
        } catch (LockedAccountException lae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
        } catch (ExcessiveAttemptsException eae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
        } catch (AuthenticationException ae) {

        }
        if (subject.isAuthenticated()) {
            long timeout = subject.getSession().getTimeout();
            LOG.info("Login success " + timeout);
            authDataSource.clearRecode(mobile);
        } else {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
        }

        return onLoginSuccess(httpResponse, mobile, clientId, platform, true);
    }

    @Override
    public RestResult loginWithPassword(HttpServletResponse response, String mobile, String password, String clientId, int platform) {
        try {
            IMResult<InputOutputUserInfo> userResult = UserAdmin.getUserByMobile(mobile);
            if (userResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
                return RestResult.error(USER_NOT_EXISTS);
            }
            if (userResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
            }

            Optional<UserPassword> optional = userPasswordRepository.findById(userResult.getResult().getUserId());
            if (!optional.isPresent()) {
                return RestResult.error(USER_NOT_EXISTS);
            }
            if(optional.get()!=null){
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                String passwdMd5 = Base64.getEncoder().encodeToString(md5.digest(password.getBytes("utf-8")));
                if(!optional.get().getPassword().equals(passwdMd5)){
                    return RestResult.error(ERROR_PASSWORD_INCORRECT);
                }
            }

            UserPassword up = optional.get();
            if (up.getTryCount() > 5) {
                if (System.currentTimeMillis() - up.getLastTryTime() < 1000) {
                    return RestResult.error(ERROR_FAILURE_TOO_MUCH_TIMES);
                }
                up.setTryCount(0);
            }
            up.setTryCount(up.getTryCount()+1);
            up.setLastTryTime(System.currentTimeMillis());
            userPasswordRepository.save(up);

            //???????????????????????????
            int userStatus = getUserStatus(mobile);
            LOG.info("??????????????????????????????" + userStatus);
            if(userStatus == 2) {
                return RestResult.error(ERROR_USER_FORBIDDEN);
            }

            Subject subject = SecurityUtils.getSubject();
            // ???????????????????????? token????????????
            UsernamePasswordToken token = new UsernamePasswordToken(userResult.getResult().getUserId(), password);
            // ??????????????????
            try {
                LOG.info("?????????token???" + token);
                subject.login(token);
            } catch (UnknownAccountException uae) {
                return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
            } catch (IncorrectCredentialsException ice) {
                return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
            } catch (LockedAccountException lae) {
                return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
            } catch (ExcessiveAttemptsException eae) {
                return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
            } catch (AuthenticationException ae) {

            }

            LOG.info("?????????subject???" + subject.isAuthenticated());
           /* if (subject.isAuthenticated()) {
                long timeout = subject.getSession().getTimeout();
                LOG.info("Login success " + timeout);
                up.setTryCount(0);
                up.setLastTryTime(0);
                userPasswordRepository.save(up);
            } else {
                return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
            }*/
            long timeout = subject.getSession().getTimeout();
            LOG.info("Login success " + timeout);
            up.setTryCount(0);
            up.setLastTryTime(0);
            userPasswordRepository.save(up);
        } catch (Exception e) {
            e.printStackTrace();
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        }
        LOG.info("?????????success");
        return onLoginSuccess(response, mobile, clientId, platform, false);
    }

    @Override
    public RestResult report(ReportRequest response) {
        Report report=new Report();
        report.setTid(response.getTid());
        report.setUid(response.getUid());
        try {
            IMResult<InputOutputUserInfo> data=UserAdmin.getUserByUserId(response.getTid());
            if(data.getResult()!=null){
                report.setTname(data.getResult().getName());
            }else{
                return RestResult.error(RestResult.RestCode.BEIJUBAO_NO_EXISTS);
            }

            IMResult<InputOutputUserInfo> result=UserAdmin.getUserByUserId(response.getUid());
            if(result.getResult()!=null){
                report.setUname(result.getResult().getName());
            }else{
                return RestResult.error(RestResult.RestCode.JUBAOREN_NO_EXISTS);
            }
        }catch (Exception e){
            report.setTname("none");
            report.setTname("none");

            return RestResult.error(RestResult.RestCode.USER_NO_EXISTS);
        }

        report.setContent(response.getContent());
        report.setStatus(1);
        report.setCreateTime(new Date());

        reportRepository.save(report);
        return RestResult.ok("????????????");
    }

    @Log(phone = "#request.phone")
    @Override
    public RestResult register(RegisterRequest request) {
        if(Objects.isNull(request)){
            return RestResult.error(ERROR_PARAMS);
        }
        if(StringUtils.isEmpty(request.getPhone())){
            return RestResult.error(ERROR_INVALID_MOBILE);
        }

//        if(StringUtils.isEmpty(request.getCode())){
//            return RestResult.error(ERROR_CODE_EXPIRED);
//        }
        if(StringUtils.isEmpty(request.getPassword())){
            return RestResult.error(ERROR_PASSWORD_NULL);
        }

        if (!superCode.equals(request.getCode())) {
            String captcha = "";
            try {
                if(recordRepository.findById(request.getPhone())!=null){
                    captcha = recordRepository.findById(request.getPhone()).get().getCode();
                }
            }catch (Exception e){
                //  return RestResult.error(ERROR_CODE_EXPIRED);
                return RestResult.error(ERROR_CODE_EXPIRED);
            }

            if(!request.getCode().equals(captcha)) {//????????????
                return RestResult.error(ERROR_CODE);
            }
        }

        try {
            IMResult<InputOutputUserInfo> userResult = UserAdmin.getUserByMobile(request.getPhone());

            String mobile=request.getPhone();
            //??????????????????????????????????????????
            InputOutputUserInfo user;
            boolean isNewUser = false;
        //    if (userResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
            if(userResult.getCode() != 0){
                LOG.info("User not exist, try to create");

                // ??????
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                String passwdMd5 = Base64.getEncoder().encodeToString(md5.digest(request.getPassword().getBytes("utf-8")));

                //?????????????????????????????????shortUUID?????????????????????????????????????????????????????????????????????????????????????????????userName???
                //ShortUUIDGenerator??????main????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                //?????????????????????????????????????????????????????????id???????????????????????????????????????????????????????????????????????????????????????????????????
                String userName;
                int tryCount = 0;
                do {
                    tryCount++;
                    userName = userNameGenerator.getUserName(mobile);
                    if (tryCount > 10) {
                        return RestResult.error(ERROR_SERVER_ERROR);
                    }
                } while (!isUsernameAvailable(userName));


                user = new InputOutputUserInfo();
                user.setName(userName);
                if (mIMConfig.use_random_name) {
                    String displayName = "??????" + (int) (Math.random() * 10000);
                    user.setDisplayName(displayName);
                } else {
                    user.setDisplayName(mobile);
                }
                //  ?????????????????????????????????????????????
                user.setPassword(request.getPassword());
                user.setMobile(mobile);
                IMResult<OutputCreateUser> userIdResult = UserAdmin.createUser(user);
                if (userIdResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    user.setUserId(userIdResult.getResult().getUserId());
                    isNewUser = true;
                } else {
                    LOG.info("Create user failure {}", userIdResult.code);
                    return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
                }
                UserPassword password=new UserPassword();
                password.setUserId(user.getUserId());
                password.setLastTryTime(0);
                String code = Utils.getRandomCode(6);
                password.setResetCode(code);
                if(!StringUtils.isEmpty(request.getInvitationCode())){
                    password.setResetCode(request.getInvitationCode());
                }
                password.setPassword(passwdMd5);
                password.setResetCodeTime(new Date().getTime());
                userPasswordRepository.save(password);
            } else if (userResult.getCode() == 0) {
                LOG.error("Get user failure {}", userResult.code);
                return RestResult.error(RestResult.RestCode.USER_EXISTS);
            } else {
                user = userResult.getResult();

                if( userPasswordRepository.findById(user.getUserId())!=null){
                    return RestResult.error(RestResult.RestCode.USER_EXISTS);
                }
            }
        }catch (Exception e){
            System.out.println("?????????"+e.getMessage());
            return RestResult.error(REGISTER_FAIL);
        }
        return RestResult.ok("????????????");
    }

    @Override
    public RestResult updateUser(InputOutputUserInfo request, MultipartFile file) {
        try {
            if (file != null) {
                // ?????????????????????????????????
                String portrait = null;
                try {
                    portrait = this.validateAvatar(file);
                } catch (Exception e) {
                    System.out.println("??????????????????:" + e.getLocalizedMessage());
                    return RestResult.error(PORTRAIT_VALIDATE_FAIL);
                }
                if (StringUtils.isEmpty(portrait)) {
                    System.out.println("????????????");
                    return RestResult.error(PORTRAIT_VALIDATE_FAIL);
                }
                request.setPortrait(portrait);
            }
            if (!StringUtils.isEmpty(request.getDisplayName())) {
                // ???????????????
                if (!OssMessageUtil.getScene(request.getDisplayName())) {
                    System.out.println("????????????");
                    return RestResult.error(NICKNAME_VALIDATE_FAIL);
                }
            }
            taskCommpal.updateUser(request);
        }catch (Exception e){
            System.out.println("?????????"+e.getMessage());
        }
        return RestResult.ok("????????????");
    }

    private String validateAvatar(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename().toLowerCase();
        List<String> pictureList = Arrays.asList(".PNG", ".JPG", ".JPEG", ".BMP", ".GIF", ".WEBP");
        // ????????????
        for (String pictures : pictureList) {
            if (name.toUpperCase().endsWith(pictures)) {
                SimpleDateFormat time=new SimpleDateFormat("yyyy/MM/dd/HH");
                String datePath = time.format(new Date());
                String dir = "./fs/portrait/" + datePath;
                //String dir = ClassUtils.getDefaultClassLoader().getResource("").getPath()+"/media/"+datePath+"/";
                System.out.println(dir);
                File dirFile = new File(dir);
                boolean fileDir  = dirFile.exists();

                if(!fileDir) {
                    fileDir = dirFile.mkdirs();
                    if (!fileDir) {
                        throw new RuntimeException("??????????????????");
                    }
                }
                File targetFile = new File(dirFile + "\\" + name);
                FileUtils.writeByteArrayToFile(targetFile, file.getBytes());
                // ?????????????????????
                boolean scene = false;
                try {
                    scene = OssImgUtil.getScene(targetFile, false);
                    if(scene){
                        return null;
                    }
                } catch (Exception e) {
                    //???????????????????????????
                    if(targetFile.exists()) {
                        targetFile.delete();
                    }
                    return null;
                } finally {
                    if (scene) {
                        //????????????????????????
                        if(targetFile.exists()) {
                            targetFile.delete();
                        }
                    }
                }
                return portraitAddress + (dir+"/"+name).substring(1);
            }
        }
        System.out.println("????????????jpg???png??????");
        return null;
    }

    @Override
    public RestResult changePassword(String oldPwd, String newPwd) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        Optional<UserPassword> optional = userPasswordRepository.findById(userId);
        if (optional.isPresent()) {
            try {
                // ??????
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                String oldPasswdMd5 = Base64.getEncoder().encodeToString(md5.digest(oldPwd.getBytes("utf-8")));
                if(optional.get().getPassword().equals(oldPasswdMd5)) {
                    String newPasswordMD5 = Base64.getEncoder().encodeToString(md5.digest(newPwd.getBytes("utf-8")));
                    changePassword(optional.get(), newPasswordMD5);
                    return RestResult.ok(null);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            return RestResult.error(ERROR_NOT_EXIST);
        }
        return RestResult.error(ERROR_PASSWORD_INCORRECT);
    }

    @Override
    public RestResult resetPassword(String mobile, String resetCode, String newPwd) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        String code = "";
        try {
          code = recordRepository.findById(mobile).get().getCode();
        }catch (Exception e){
            return RestResult.error(ERROR_CODE_EXPIRED);
        }
        if (!StringUtils.isEmpty(mobile)) {
            try {
                IMResult<InputOutputUserInfo> userResult = UserAdmin.getUserByMobile(mobile);
                if (userResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                    return RestResult.error(ERROR_SERVER_ERROR);
                }
                if (StringUtils.isEmpty(userId)) {
                    userId = userResult.getResult().getUserId();
                } else {
                   /* if(!userId.equals(userResult.getResult().getUserId())) {
                        //??????????????????
                        LOG.error("reset password error, user is correct {}, {}", userId, userResult.getResult().getUserId());
                        return RestResult.error(ERROR_SERVER_ERROR);
                    }*/
                }
            } catch (Exception e) {
                e.printStackTrace();
                return RestResult.error(ERROR_SERVER_ERROR);
            }
        }

        Optional<UserPassword> optional = userPasswordRepository.findById(userId);
        if (optional.isPresent()) {
            UserPassword up = optional.get();
            if(resetCode.equals(code)) {
                try {
                    MessageDigest md5 = MessageDigest.getInstance("MD5");
                    String newPasswordMD5 = Base64.getEncoder().encodeToString(md5.digest(newPwd.getBytes("utf-8")));
                    changePassword(up, newPasswordMD5);
                    up.setResetCode(null);
                    userPasswordRepository.save(up);
                    return RestResult.ok(null);
                } catch (Exception e) {
                    e.printStackTrace();
                    return RestResult.error(ERROR_SERVER_ERROR);
                }
            } else {
                return RestResult.error(ERROR_CODE_INCORRECT);
            }
        } else {
            return RestResult.error(ERROR_NOT_EXIST);
        }
    }

    private void changePassword(UserPassword up, String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(Sha1Hash.ALGORITHM_NAME);
        digest.reset();
        String salt = UUID.randomUUID().toString();
        digest.update(salt.getBytes(StandardCharsets.UTF_8));
        //byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        //String hashedPwd = Base64.getEncoder().encodeToString(hashed);
        //up.setPassword(hashedPwd);
        up.setPassword(password);
        up.setSalt(salt);
        userPasswordRepository.save(up);
    }

    private boolean verifyPassword(UserPassword up, String password) throws Exception {
        String salt = up.getSalt();
        MessageDigest digest = MessageDigest.getInstance(Sha1Hash.ALGORITHM_NAME);
        if (salt != null) {
            digest.reset();
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
        }

        byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        String hashedPwd = Base64.getEncoder().encodeToString(hashed);
        LOG.info("hashedPwd??? " + hashedPwd);
        return hashedPwd.equals(up.getPassword());
    }

    private RestResult onLoginSuccess(HttpServletResponse httpResponse, String mobile, String clientId, int platform, boolean withResetCode) {
        Subject subject = SecurityUtils.getSubject();
        try {
            //???????????????????????????????????????
            IMResult<InputOutputUserInfo> userResult = UserAdmin.getUserByMobile(mobile);

            //??????????????????????????????????????????
            InputOutputUserInfo user;
            boolean isNewUser = false;
            if (userResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
                LOG.info("User not exist, try to create");

                //?????????????????????????????????shortUUID?????????????????????????????????????????????????????????????????????????????????????????????userName???
                //ShortUUIDGenerator??????main????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                //?????????????????????????????????????????????????????????id???????????????????????????????????????????????????????????????????????????????????????????????????
                String userName;
                int tryCount = 0;
                do {
                    tryCount++;
                    userName = userNameGenerator.getUserName(mobile);
                    if (tryCount > 10) {
                        return RestResult.error(ERROR_SERVER_ERROR);
                    }
                } while (!isUsernameAvailable(userName));


                user = new InputOutputUserInfo();
                user.setName(userName);
                if (mIMConfig.use_random_name) {
                    String displayName = "??????" + (int) (Math.random() * 10000);
                    user.setDisplayName(displayName);
                } else {
                    user.setDisplayName(mobile);
                }
                user.setMobile(mobile);
                IMResult<OutputCreateUser> userIdResult = UserAdmin.createUser(user);
                if (userIdResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    user.setUserId(userIdResult.getResult().getUserId());
                    isNewUser = true;
                } else {
                    LOG.info("Create user failure {}", userIdResult.code);
                    return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
                }


            } else if (userResult.getCode() != 0) {
                LOG.error("Get user failure {}", userResult.code);
                return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
            } else {
                user = userResult.getResult();
            }

            //????????????id??????token
            IMResult<OutputGetIMTokenData> tokenResult = UserAdmin.getUserToken(user.getUserId(), clientId, platform);
            if (tokenResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                LOG.error("Get user failure {}", tokenResult.code);
                return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
            }

            subject.getSession().setAttribute("userId", user.getUserId());

            //????????????id???token???????????????
            LoginResponse response = new LoginResponse();
            response.setUserId(user.getUserId());
            response.setToken(tokenResult.getResult().getToken());
            response.setRegister(isNewUser);
            response.setPortrait(user.getPortrait());
            response.setUserName(user.getName());
            boolean pwd=false;
            if (withResetCode) {
                String code = Utils.getRandomCode(6);
                Optional<UserPassword> optional = userPasswordRepository.findById(user.getUserId());
                UserPassword up;
                if (optional.isPresent()) {
                    up = optional.get();
                    pwd = true;
                } else {
                    up = new UserPassword(user.getUserId(), null, null);
                    pwd = false;
                }
                up.setResetCode(code);
                up.setResetCodeTime(System.currentTimeMillis());
                userPasswordRepository.save(up);
                response.setResetCode(code);
            }
            response.setPwd(pwd);

            if (isNewUser) {
                if (!StringUtils.isEmpty(mIMConfig.welcome_for_new_user)) {
                    sendTextMessage("admin", user.getUserId(), mIMConfig.welcome_for_new_user);
                }

                if (mIMConfig.new_user_robot_friend && !StringUtils.isEmpty(mIMConfig.robot_friend_id)) {
                    RelationAdmin.setUserFriend(user.getUserId(), mIMConfig.robot_friend_id, true, null);
                    if (!StringUtils.isEmpty(mIMConfig.robot_welcome)) {
                        sendTextMessage(mIMConfig.robot_friend_id, user.getUserId(), mIMConfig.robot_welcome);
                    }
                }

                if (!StringUtils.isEmpty(mIMConfig.new_user_subscribe_channel_id)) {
                    try {
                        GeneralAdmin.subscribeChannel(mIMConfig.getNew_user_subscribe_channel_id(), user.getUserId());
                    } catch (Exception e) {

                    }
                }
            } else {
                if (!StringUtils.isEmpty(mIMConfig.welcome_for_back_user)) {
                    sendTextMessage("admin", user.getUserId(), mIMConfig.welcome_for_back_user);
                }
                if (!StringUtils.isEmpty(mIMConfig.back_user_subscribe_channel_id)) {
                    try {
                        IMResult<OutputBooleanValue> booleanValueIMResult = GeneralAdmin.isUserSubscribedChannel(user.getUserId(), mIMConfig.getBack_user_subscribe_channel_id());
                        if (booleanValueIMResult != null && booleanValueIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS && !booleanValueIMResult.getResult().value) {
                            GeneralAdmin.subscribeChannel(mIMConfig.back_user_subscribe_channel_id, user.getUserId());
                        }
                    } catch (Exception e) {

                    }
                }
            }

            Object sessionId = subject.getSession().getId();
            httpResponse.setHeader("authToken", sessionId.toString());
            return RestResult.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Exception happens {}", e);
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        }
    }
    @Override
    public RestResult sendDestroyCode() {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        try {
            IMResult<InputOutputUserInfo> getUserResult = UserAdmin.getUserByUserId(userId);
            if(getUserResult != null && getUserResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                String mobile = getUserResult.getResult().getMobile();
                if(!StringUtils.isEmpty(mobile)) {
                    return sendLoginCode(mobile);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        }
        return RestResult.error(RestResult.RestCode.ERROR_NOT_EXIST);
    }

    @Override
    public RestResult destroy(HttpServletResponse response, String code) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        try {
            IMResult<InputOutputUserInfo> getUserResult = UserAdmin.getUserByUserId(userId);
            if(getUserResult != null && getUserResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                String mobile = getUserResult.getResult().getMobile();
                if(!StringUtils.isEmpty(mobile)) {
                    if(authDataSource.verifyCode(mobile, code) == SUCCESS) {
                        UserAdmin.destroyUser(userId);
                        authDataSource.clearRecode(mobile);
                        userPasswordRepository.deleteById(userId);
                        subject.logout();
                        return RestResult.ok(null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        }
        return RestResult.error(RestResult.RestCode.ERROR_NOT_EXIST);
    }

    private boolean isUsernameAvailable(String username) {
        try {
            IMResult<InputOutputUserInfo> existUser = UserAdmin.getUserByName(username);
            if (existUser.code == ErrorCode.ERROR_CODE_NOT_EXIST.code) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void sendPcLoginRequestMessage(String fromUser, String toUser, int platform, String token) {
        Conversation conversation = new Conversation();
        conversation.setTarget(toUser);
        conversation.setType(ProtoConstants.ConversationType.ConversationType_Private);
        MessagePayload payload = new MessagePayload();
        payload.setType(94);
        if (platform == ProtoConstants.Platform.Platform_WEB) {
            payload.setPushContent("Web???????????????");
        } else if (platform == ProtoConstants.Platform.Platform_OSX) {
            payload.setPushContent("Mac ???????????????");
        } else if (platform == ProtoConstants.Platform.Platform_LINUX) {
            payload.setPushContent("Linux ???????????????");
        } else if (platform == ProtoConstants.Platform.Platform_Windows) {
            payload.setPushContent("Windows ???????????????");
        } else {
            payload.setPushContent("PC ???????????????");
        }

        payload.setExpireDuration(60 * 1000);
        payload.setPersistFlag(ProtoConstants.PersistFlag.Not_Persist);
        JSONObject data = new JSONObject();
        data.put("p", platform);
        data.put("t", token);
        payload.setBase64edData(Base64Utils.encodeToString(data.toString().getBytes()));

        try {
            IMResult<SendMessageResult> resultSendMessage = MessageAdmin.sendMessage(fromUser, conversation, payload);
            if (resultSendMessage != null && resultSendMessage.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                LOG.info("send message success");
            } else {
                LOG.error("send message error {}", resultSendMessage != null ? resultSendMessage.getErrorCode().code : "unknown");
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("send message error {}", e.getLocalizedMessage());
        }

    }

    private void sendTextMessage(String fromUser, String toUser, String text) {
        Conversation conversation = new Conversation();
        conversation.setTarget(toUser);
        conversation.setType(ProtoConstants.ConversationType.ConversationType_Private);
        MessagePayload payload = new MessagePayload();
        payload.setType(1);
        payload.setSearchableContent(text);


        try {
            IMResult<SendMessageResult> resultSendMessage = MessageAdmin.sendMessage(fromUser, conversation, payload);
            if (resultSendMessage != null && resultSendMessage.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                LOG.info("send message success");
            } else {
                LOG.error("send message error {}", resultSendMessage != null ? resultSendMessage.getErrorCode().code : "unknown");
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("send message error {}", e.getLocalizedMessage());
        }

    }


    @Override
    public RestResult createPcSession(CreateSessionRequest request) {
        String userId = request.getUserId();
        // pc????????????????????????????????????????????????cookie?????????????????????????????????userId????????????????????????????????????
        if (request.getFlag() == 1 && !StringUtils.isEmpty(userId)) {
            Subject subject = SecurityUtils.getSubject();
            userId = (String) subject.getSession().getAttribute("userId");
        }

        if (compatPcQuickLogin) {
            if (userId != null && supportPCQuickLoginUsers.get(userId) == null) {
                userId = null;
            }
        }

        PCSession session = authDataSource.createSession(userId, request.getClientId(), request.getToken(), request.getPlatform());
        if (userId != null) {
            sendPcLoginRequestMessage("admin", userId, request.getPlatform(), session.getToken());
        }
        SessionOutput output = session.toOutput();
        LOG.info("client {} create pc session, key is {}", request.getClientId(), output.getToken());
        return RestResult.ok(output);
    }

    @Override
    public RestResult loginWithSession(String token) {
        Subject subject = SecurityUtils.getSubject();
        // ???????????????????????? token????????????
        // comment start ??????????????????????????????????????????Shiro???????????????
        TokenAuthenticationToken tt = new TokenAuthenticationToken(token);
        PCSession session = authDataSource.getSession(token, false);

        if (session == null) {
            return RestResult.error(ERROR_CODE_EXPIRED);
        } else if (session.getStatus() == Session_Created) {
            return RestResult.error(ERROR_SESSION_NOT_SCANED);
        } else if (session.getStatus() == Session_Scanned) {
            session.setStatus(Session_Pre_Verify);
            authDataSource.saveSession(session);
            LoginResponse response = new LoginResponse();
            try {
                IMResult<InputOutputUserInfo> result = UserAdmin.getUserByUserId(session.getConfirmedUserId());
                if (result.getCode() == 0) {
                    response.setUserName(result.getResult().getDisplayName());
                    response.setPortrait(result.getResult().getPortrait());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return RestResult.result(ERROR_SESSION_NOT_VERIFIED, response);
        } else if (session.getStatus() == Session_Pre_Verify) {
            return RestResult.error(ERROR_SESSION_NOT_VERIFIED);
        } else if (session.getStatus() == Session_Canceled) {
            return RestResult.error(ERROR_SESSION_CANCELED);
        }
        // comment end

        // ??????????????????
        // comment start ??????PC??????????????????????????????app server???????????????????????????????????????, PC???????????????????????????app server??????session???
        try {
            subject.login(tt);
        } catch (UnknownAccountException uae) {
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        } catch (IncorrectCredentialsException ice) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
        } catch (LockedAccountException lae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
        } catch (ExcessiveAttemptsException eae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
        } catch (AuthenticationException ae) {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
        }
        if (subject.isAuthenticated()) {
            LOG.info("Login success");
        } else {
            return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
        }
        // comment end

        session = authDataSource.getSession(token, true);
        if (session == null) {
            subject.logout();
            return RestResult.error(RestResult.RestCode.ERROR_CODE_EXPIRED);
        }
        subject.getSession().setAttribute("userId", session.getConfirmedUserId());

        try {
            //????????????id??????token
            IMResult<OutputGetIMTokenData> tokenResult = UserAdmin.getUserToken(session.getConfirmedUserId(), session.getClientId(), session.getPlatform());
            if (tokenResult.getCode() != 0) {
                LOG.error("Get user failure {}", tokenResult.code);
                subject.logout();
                return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
            }
            //????????????id???token???????????????
            LoginResponse response = new LoginResponse();
            response.setUserId(session.getConfirmedUserId());
            response.setToken(tokenResult.getResult().getToken());
            return RestResult.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            subject.logout();
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        }
    }

    @Override
    public RestResult scanPc(String token) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");

        LOG.info("user {} scan pc, session is {}", userId, token);
        return authDataSource.scanPc(userId, token);
    }

    @Override
    public RestResult confirmPc(ConfirmSessionRequest request) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        if (compatPcQuickLogin) {
            if (request.getQuick_login() > 0) {
                supportPCQuickLoginUsers.put(userId, true);
            } else {
                supportPCQuickLoginUsers.remove(userId);
            }
        }

        LOG.info("user {} confirm pc, session is {}", userId, request.getToken());
        return authDataSource.confirmPc(userId, request.getToken());
    }

    @Override
    public RestResult cancelPc(CancelSessionRequest request) {
        return authDataSource.cancelPc(request.getToken());
    }

    @Override
    public RestResult changeName(String newName) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        try {
            IMResult<InputOutputUserInfo> existUser = UserAdmin.getUserByName(newName);
            if (existUser != null) {
                if (existUser.code == ErrorCode.ERROR_CODE_SUCCESS.code) {
                    if (userId.equals(existUser.getResult().getUserId())) {
                        return RestResult.ok(null);
                    } else {
                        return RestResult.error(ERROR_USER_NAME_ALREADY_EXIST);
                    }
                } else if (existUser.code == ErrorCode.ERROR_CODE_NOT_EXIST.code) {
                    existUser = UserAdmin.getUserByUserId(userId);
                    if (existUser == null || existUser.code != ErrorCode.ERROR_CODE_SUCCESS.code || existUser.getResult() == null) {
                        return RestResult.error(ERROR_SERVER_ERROR);
                    }

                    existUser.getResult().setName(newName);
                    IMResult<OutputCreateUser> createUser = UserAdmin.createUser(existUser.getResult());
                    if (createUser.code == ErrorCode.ERROR_CODE_SUCCESS.code) {
                        return RestResult.ok(null);
                    } else {
                        return RestResult.error(ERROR_SERVER_ERROR);
                    }
                } else {
                    return RestResult.error(ERROR_SERVER_ERROR);
                }
            } else {
                return RestResult.error(ERROR_SERVER_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return RestResult.error(ERROR_SERVER_ERROR);
        }
    }

    @Override
    public RestResult complain(String text) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        LOG.error("Complain from user {} where content {}", userId, text);
        sendTextMessage(userId, "cgc8c8VV", text);
        return RestResult.ok(null);
    }

    @Override
    public RestResult getGroupAnnouncement(String groupId) {
        Optional<Announcement> announcement = announcementRepository.findById(groupId);
        if (announcement.isPresent()) {
            GroupAnnouncementPojo pojo = new GroupAnnouncementPojo();
            pojo.groupId = announcement.get().getGroupId();
            pojo.author = announcement.get().getAuthor();
            pojo.text = announcement.get().getAnnouncement();
            pojo.timestamp = announcement.get().getTimestamp();
            return RestResult.ok(pojo);
        } else {
            return RestResult.error(ERROR_GROUP_ANNOUNCEMENT_NOT_EXIST);
        }
    }

    @Override
    public RestResult putGroupAnnouncement(GroupAnnouncementPojo request) {
        if (!StringUtils.isEmpty(request.text)) {
            Subject subject = SecurityUtils.getSubject();
            String userId = (String) subject.getSession().getAttribute("userId");
            boolean isGroupMember = false;
            try {
                IMResult<OutputGroupMemberList> imResult = GroupAdmin.getGroupMembers(request.groupId);
                if (imResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS && imResult.getResult() != null && imResult.getResult().getMembers() != null) {
                    for (PojoGroupMember member : imResult.getResult().getMembers()) {
                        if (member.getMember_id().equals(userId)) {
                            if (member.getType() != ProtoConstants.GroupMemberType.GroupMemberType_Removed
                                && member.getType() != ProtoConstants.GroupMemberType.GroupMemberType_Silent) {
                                isGroupMember = true;
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!isGroupMember) {
                return RestResult.error(ERROR_NO_RIGHT);
            }

            Conversation conversation = new Conversation();
            conversation.setTarget(request.groupId);
            conversation.setType(ProtoConstants.ConversationType.ConversationType_Group);
            MessagePayload payload = new MessagePayload();
            payload.setType(1);
            payload.setSearchableContent("@????????? " + request.text);
            payload.setMentionedType(2);


            try {
                IMResult<SendMessageResult> resultSendMessage = MessageAdmin.sendMessage(request.author, conversation, payload);
                if (resultSendMessage != null && resultSendMessage.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    LOG.info("send message success");
                } else {
                    LOG.error("send message error {}", resultSendMessage != null ? resultSendMessage.getErrorCode().code : "unknown");
                    return RestResult.error(ERROR_SERVER_ERROR);
                }
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error("send message error {}", e.getLocalizedMessage());
                return RestResult.error(ERROR_SERVER_ERROR);
            }
        }

        Announcement announcement = new Announcement();
        announcement.setGroupId(request.groupId);
        announcement.setAuthor(request.author);
        announcement.setAnnouncement(request.text);
        request.timestamp = System.currentTimeMillis();
        announcement.setTimestamp(request.timestamp);

        announcementRepository.save(announcement);
        return RestResult.ok(request);
    }

    @Override
    public RestResult saveUserLogs(String userId, MultipartFile file) {
        File localFile = new File(userLogPath, userId + "_" + file.getOriginalFilename());
        try {
            if(file.getOriginalFilename().indexOf(".jpg") >= 0 || file.getOriginalFilename().indexOf(".png") >= 0){//????????????
                if(!OssImgUtil.getScene(multipartFileToFile(file), true)){
                    return RestResult.error(FILE_ILLEGALTY);
                }
            }
            if(file.getOriginalFilename().indexOf(".mp4") >= 0){//????????????
//                if(!OssVideoUtil.getScene(multipartFileToFile(file))){
//                    return RestResult.error(VIEDO_ILLEGALTY);
//                }
            }
            File sb = multipartFileToFile(file);
            delteTempFile(sb);
            file.transferTo(sb);
        } catch (Exception e) {
            e.printStackTrace();
            return RestResult.error(ERROR_SERVER_ERROR);
        }

        return RestResult.ok(null);
    }

    public static File multipartFileToFile(MultipartFile file) throws Exception {

        File toFile = null;
        if (file.equals("") || file.getSize() <= 0) {
            file = null;
        } else {
            InputStream ins = null;
            ins = file.getInputStream();

            toFile = new File(file.getOriginalFilename());
            inputStreamToFile(ins, toFile);
            ins.close();
        }
        return toFile;
    }


    private static void inputStreamToFile(InputStream ins, File file) {
        try {
            OutputStream os = new FileOutputStream(file);
            int bytesRead = 0;
            byte[] buffer = new byte[8192];
            while ((bytesRead = ins.read(buffer, 0, 8192)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.close();
            ins.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void delteTempFile(File file) {
        if (file != null) {
            File del = new File(file.toURI());
            del.delete();
        }
    }

    @Override
    public RestResult addDevice(InputCreateDevice createDevice) {
        try {
            Subject subject = SecurityUtils.getSubject();
            String userId = (String) subject.getSession().getAttribute("userId");

            if (!StringUtils.isEmpty(createDevice.getDeviceId())) {
                IMResult<OutputDevice> outputDeviceIMResult = UserAdmin.getDevice(createDevice.getDeviceId());
                if (outputDeviceIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    if (!createDevice.getOwners().contains(userId)) {
                        return RestResult.error(ERROR_NO_RIGHT);
                    }
                } else if (outputDeviceIMResult.getErrorCode() != ErrorCode.ERROR_CODE_NOT_EXIST) {
                    return RestResult.error(ERROR_SERVER_ERROR);
                }
            }

            IMResult<OutputCreateDevice> result = UserAdmin.createOrUpdateDevice(createDevice);
            if (result != null && result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                return RestResult.ok(result.getResult());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return RestResult.error(ERROR_SERVER_ERROR);
    }

    @Override
    public RestResult getDeviceList() {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        try {
            IMResult<OutputDeviceList> imResult = UserAdmin.getUserDevices(userId);
            if (imResult != null && imResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                return RestResult.ok(imResult.getResult().getDevices());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return RestResult.error(ERROR_SERVER_ERROR);
    }


    @Override
    public RestResult delDevice(InputCreateDevice createDevice) {
        try {
            Subject subject = SecurityUtils.getSubject();
            String userId = (String) subject.getSession().getAttribute("userId");

            if (!StringUtils.isEmpty(createDevice.getDeviceId())) {
                IMResult<OutputDevice> outputDeviceIMResult = UserAdmin.getDevice(createDevice.getDeviceId());
                if (outputDeviceIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    if (outputDeviceIMResult.getResult().getOwners().contains(userId)) {
                        createDevice.setExtra(outputDeviceIMResult.getResult().getExtra());
                        outputDeviceIMResult.getResult().getOwners().remove(userId);
                        createDevice.setOwners(outputDeviceIMResult.getResult().getOwners());
                        IMResult<OutputCreateDevice> result = UserAdmin.createOrUpdateDevice(createDevice);
                        if (result != null && result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                            return RestResult.ok(result.getResult());
                        } else {
                            return RestResult.error(ERROR_SERVER_ERROR);
                        }
                    } else {
                        return RestResult.error(ERROR_NO_RIGHT);
                    }
                } else {
                    if (outputDeviceIMResult.getErrorCode() != ErrorCode.ERROR_CODE_NOT_EXIST) {
                        return RestResult.error(ERROR_SERVER_ERROR);
                    } else {
                        return RestResult.error(ERROR_NOT_EXIST);
                    }
                }
            } else {
                return RestResult.error(ERROR_INVALID_PARAMETER);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return RestResult.error(ERROR_SERVER_ERROR);
    }

    @Override
    public RestResult sendMessage(SendMessageRequest request) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");

        Conversation conversation = new Conversation();
        conversation.setType(request.type);
        conversation.setTarget(request.target);
        conversation.setLine(request.line);

        MessagePayload payload = new MessagePayload();
        payload.setType(request.content_type);
        payload.setSearchableContent(request.content_searchable);
        payload.setPushContent(request.content_push);
        payload.setPushData(request.content_push_data);
        payload.setContent(request.content);
        payload.setBase64edData(request.content_binary);
        payload.setMediaType(request.content_media_type);
        payload.setRemoteMediaUrl(request.content_remote_url);
        payload.setMentionedType(request.content_mentioned_type);
        payload.setMentionedTarget(request.content_mentioned_targets);
        payload.setExtra(request.content_extra);

        try {
            IMResult<SendMessageResult> imResult = MessageAdmin.sendMessage(userId, conversation, payload);
            if (imResult != null && imResult.getCode() == ErrorCode.ERROR_CODE_SUCCESS.code) {
                return RestResult.ok(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return RestResult.error(ERROR_SERVER_ERROR);
    }

    @Override
    public RestResult uploadMedia(int mediaType, MultipartFile file) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");
        String uuid = new ShortUUIDGenerator().getUserName(userId);
        String fileName = userId + "-" + System.currentTimeMillis() + "-" + uuid + "-" + file.getOriginalFilename();
        File localFile = new File(ossTempPath, fileName);

        try {
            file.transferTo(localFile);
        } catch (IOException e) {
            e.printStackTrace();
            return RestResult.error(ERROR_SERVER_ERROR);
        }
        /*
        #Media_Type_GENERAL = 0,
#Media_Type_IMAGE = 1,
#Media_Type_VOICE = 2,
#Media_Type_VIDEO = 3,
#Media_Type_FILE = 4,
#Media_Type_PORTRAIT = 5,
#Media_Type_FAVORITE = 6,
#Media_Type_STICKER = 7,
#Media_Type_MOMENTS = 8
         */
        String bucket;
        String bucketDomain;
        switch (mediaType) {
            case 0:
            default:
                bucket = ossGeneralBucket;
                bucketDomain = ossGeneralBucketDomain;
                break;
            case 1:
                bucket = ossImageBucket;
                bucketDomain = ossImageBucketDomain;
                break;
            case 2:
                bucket = ossVoiceBucket;
                bucketDomain = ossVideoBucketDomain;
                break;
            case 3:
                bucket = ossVideoBucket;
                bucketDomain = ossVideoBucketDomain;
                break;
            case 4:
                bucket = ossFileBucket;
                bucketDomain = ossFileBucketDomain;
                break;
            case 7:
                bucket = ossMomentsBucket;
                bucketDomain = ossMomentsBucketDomain;
                break;
            case 8:
                bucket = ossStickerBucket;
                bucketDomain = ossStickerBucketDomain;
                break;
        }

        String url = bucketDomain + "/" + fileName;
        if (ossType == 1) {
            //????????????????????? Region ??????????????????
            Configuration cfg = new Configuration(Region.region0());
            //...???????????????????????????
            UploadManager uploadManager = new UploadManager(cfg);
            //...???????????????????????????????????????

            //?????????Windows????????????????????? D:\\qiniu\\test.png
            String localFilePath = localFile.getAbsolutePath();
            //???????????????key?????????????????????????????????hash??????????????????
            String key = fileName;
            Auth auth = Auth.create(ossAccessKey, ossSecretKey);
            String upToken = auth.uploadToken(bucket);
            try {
                Response response = uploadManager.put(localFilePath, key, upToken);
                //???????????????????????????
                DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
                System.out.println(putRet.key);
                System.out.println(putRet.hash);
            } catch (QiniuException ex) {
                Response r = ex.response;
                System.err.println(r.toString());
                try {
                    System.err.println(r.bodyString());
                } catch (QiniuException ex2) {
                    //ignore
                }
                return RestResult.error(ERROR_SERVER_ERROR);
            }
        } else if (ossType == 2) {
            // ??????OSSClient?????????
            OSS ossClient = new OSSClientBuilder().build(ossUrl, ossAccessKey, ossSecretKey);

            // ??????PutObjectRequest?????????
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, fileName, localFile);

            // ???????????????
            try {
                ossClient.putObject(putObjectRequest);
            } catch (OSSException | ClientException e) {
                e.printStackTrace();
                return RestResult.error(ERROR_SERVER_ERROR);
            }
            // ??????OSSClient???
            ossClient.shutdown();
        } else if (ossType == 3) {
            try {
                // ??????MinIO?????????URL????????????Access key???Secret key????????????MinioClient??????
//                MinioClient minioClient = new MinioClient("https://play.min.io", "Q3AM3UQ867SPQQA43P2F", "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG");
                MinioClient minioClient = new MinioClient(ossUrl, ossAccessKey, ossSecretKey);

                // ??????putObject????????????????????????????????????
//                minioClient.putObject("asiatrip",fileName, localFile.getAbsolutePath(), new PutObjectOptions(PutObjectOptions.MAX_OBJECT_SIZE, PutObjectOptions.MIN_MULTIPART_SIZE));
                minioClient.putObject(bucket, fileName, localFile.getAbsolutePath(), new PutObjectOptions(file.getSize(), 0));
            } catch (MinioException e) {
                System.out.println("Error occurred: " + e);
                return RestResult.error(ERROR_SERVER_ERROR);
            } catch (NoSuchAlgorithmException | IOException | InvalidKeyException e) {
                e.printStackTrace();
                return RestResult.error(ERROR_SERVER_ERROR);
            } catch (Exception e) {
                e.printStackTrace();
                return RestResult.error(ERROR_SERVER_ERROR);
            }
        } else if(ossType == 4) {
            //Todo ??????????????????????????????????????????
        } else if(ossType == 5) {
            COSCredentials cred = new BasicCOSCredentials(ossAccessKey, ossSecretKey);
            ClientConfig clientConfig = new ClientConfig();
            String [] ss = ossUrl.split("\\.");
            if(ss.length > 3) {
                if(!ss[1].equals("accelerate")) {
                    clientConfig.setRegion(new com.qcloud.cos.region.Region(ss[1]));
                } else {
                    clientConfig.setRegion(new com.qcloud.cos.region.Region("ap-shanghai"));
                    try {
                        URL u = new URL(ossUrl);
                        clientConfig.setEndPointSuffix(u.getHost());
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                        return RestResult.error(ERROR_SERVER_ERROR);
                    }
                }
            }

            clientConfig.setHttpProtocol(HttpProtocol.https);
            COSClient cosClient = new COSClient(cred, clientConfig);

            try {
                cosClient.putObject(bucket, fileName, localFile.getAbsoluteFile());
            } catch (CosClientException e) {
                e.printStackTrace();
                return RestResult.error(ERROR_SERVER_ERROR);
            } finally {
                cosClient.shutdown();
            }
        }

        UploadFileResponse response = new UploadFileResponse();
        response.url = url;
        return RestResult.ok(response);
    }

    @Override
    public RestResult putFavoriteItem(FavoriteItem request) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");

        if(!StringUtils.isEmpty(request.url)){
            try {
                //???????????????????????????????????????bucket???
                URL mediaURL = new URL(request.url);

                String bucket = null;
                if (mediaURL.getHost().equals(new URL(ossGeneralBucketDomain).getHost())) {
                    bucket = ossGeneralBucket;
                } else if (mediaURL.getHost().equals(new URL(ossImageBucketDomain).getHost())) {
                    bucket = ossImageBucket;
                } else if (mediaURL.getHost().equals(new URL(ossVoiceBucketDomain).getHost())) {
                    bucket = ossVoiceBucket;
                } else if (mediaURL.getHost().equals(new URL(ossVideoBucketDomain).getHost())) {
                    bucket = ossVideoBucket;
                } else if (mediaURL.getHost().equals(new URL(ossFileBucketDomain).getHost())) {
                    bucket = ossFileBucket;
                } else if (mediaURL.getHost().equals(new URL(ossMomentsBucketDomain).getHost())) {
                    bucket = ossMomentsBucket;
                } else if (mediaURL.getHost().equals(new URL(ossStickerBucketDomain).getHost())) {
                    bucket = ossStickerBucket;
                } else if (mediaURL.getHost().equals(new URL(ossFavoriteBucketDomain).getHost())) {
                    //It's already in fav bucket, no need to copy
                    //bucket = ossFavoriteBucket;
                }

                if (bucket != null) {
                    String path = mediaURL.getPath();
                    if (ossType == 1) {
                        Configuration cfg = new Configuration(Region.region0());
                        String fromKey = path.substring(1);
                        Auth auth = Auth.create(ossAccessKey, ossSecretKey);

                        String toBucket = ossFavoriteBucket;
                        String toKey = fromKey;
                        if (!toKey.startsWith(userId)) {
                            toKey = userId + "-" + toKey;
                        }

                        BucketManager bucketManager = new BucketManager(auth, cfg);
                        bucketManager.copy(bucket, fromKey, toBucket, toKey);
                        request.url = ossFavoriteBucketDomain + "/" + fromKey;
                    } else if (ossType == 2) {
                        OSS ossClient = new OSSClient(ossUrl, ossAccessKey, ossSecretKey);
                        path = path.substring(1);
                        String objectName = path;
                        String toKey = path;
                        if (!toKey.startsWith(userId)) {
                            toKey = userId + "-" + toKey;
                        }

                        ossClient.copyObject(bucket, objectName, ossFavoriteBucket, toKey);
                        request.url = ossFavoriteBucketDomain + "/" + toKey;
                        ossClient.shutdown();
                    } else if (ossType == 3) {
                        path = path.substring(bucket.length() + 2);
                        String objectName = path;
                        String toKey = path;
                        if (!toKey.startsWith(userId)) {
                            toKey = userId + "-" + toKey;
                        }
                        MinioClient minioClient = new MinioClient(ossUrl, ossAccessKey, ossSecretKey);
                        minioClient.copyObject(ossFavoriteBucket, toKey, null, null, bucket, objectName, null, null);
                        request.url = ossFavoriteBucketDomain + "/" + toKey;
                    } else if(ossType == 4) {
                        //Todo ????????????????????????????????????????????????
                    } else if(ossType == 5) {
                        COSCredentials cred = new BasicCOSCredentials(ossAccessKey, ossSecretKey);
                        ClientConfig clientConfig = new ClientConfig();
                        String [] ss = ossUrl.split("\\.");
                        if(ss.length > 3) {
                            if(!ss[1].equals("accelerate")) {
                                clientConfig.setRegion(new com.qcloud.cos.region.Region(ss[1]));
                            } else {
                                clientConfig.setRegion(new com.qcloud.cos.region.Region("ap-shanghai"));
                                try {
                                    URL u = new URL(ossUrl);
                                    clientConfig.setEndPointSuffix(u.getHost());
                                } catch (MalformedURLException e) {
                                    e.printStackTrace();
                                    return RestResult.error(ERROR_SERVER_ERROR);
                                }
                            }
                        }

                        clientConfig.setHttpProtocol(HttpProtocol.https);
                        COSClient cosClient = new COSClient(cred, clientConfig);

                        path = path.substring(1);
                        String objectName = path;
                        String toKey = path;
                        if (!toKey.startsWith(userId)) {
                            toKey = userId + "-" + toKey;
                        }

                        try {
                            cosClient.copyObject(bucket, objectName, ossFavoriteBucket, toKey);
                            request.url = ossFavoriteBucketDomain + "/" + toKey;
                        } catch (CosClientException e) {
                            e.printStackTrace();
                            return RestResult.error(ERROR_SERVER_ERROR);
                        } finally {
                            cosClient.shutdown();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        request.userId = userId;
        request.timestamp = System.currentTimeMillis();
        favoriteRepository.save(request);
        return RestResult.ok(null);
    }

    @Override
    public RestResult removeFavoriteItems(long id) {
        favoriteRepository.deleteById(id);
        return RestResult.ok(null);
    }

    @Override
    public RestResult getFavoriteItems(long id, int count) {
        Subject subject = SecurityUtils.getSubject();
        String userId = (String) subject.getSession().getAttribute("userId");

        id = id > 0 ? id : Long.MAX_VALUE;
        List<FavoriteItem> favs = favoriteRepository.loadFav(userId, id, count);
        LoadFavoriteResponse response = new LoadFavoriteResponse();
        response.items = favs;
        response.hasMore = favs.size() == count;
        return RestResult.ok(response);
    }

    @Override
    public RestResult getGroupMembersForPortrait(String groupId) {
        try {
            IMResult<OutputGroupMemberList> groupMemberListIMResult = GroupAdmin.getGroupMembers(groupId);
            if(groupMemberListIMResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                LOG.error("getGroupMembersForPortrait failure {},{}", groupMemberListIMResult.getErrorCode().getCode(), groupMemberListIMResult.getErrorCode().getMsg());
                return RestResult.error(ERROR_SERVER_ERROR);
            }
            List<PojoGroupMember> groupMembers = new ArrayList<>();
            for (PojoGroupMember member:groupMemberListIMResult.getResult().getMembers()) {
                if(member.getType() != 4)
                    groupMembers.add(member);
            }

            if (groupMembers.size() > 9) {
                groupMembers.sort((o1, o2) -> {
                    if(o1.getType() == 2)
                        return -1;
                    if(o2.getType() == 2)
                        return 1;
                    if(o1.getType() == 1 && o2.getType() != 1)
                        return -1;
                    if(o2.getType() == 1 && o1.getType() != 1)
                        return 1;
                    return Long.compare(o1.getCreateDt(), o2.getCreateDt());
                });
                groupMembers = groupMembers.subList(0, 9);
            }
            List<UserIdPortraitPojo> mids = new ArrayList<>();
            for (PojoGroupMember member:groupMembers) {
                IMResult<InputOutputUserInfo> userInfoIMResult = UserAdmin.getUserByUserId(member.getMember_id());
                if(userInfoIMResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    mids.add(new UserIdPortraitPojo(member.getMember_id(), userInfoIMResult.result.getPortrait()));
                } else {
                    mids.add(new UserIdPortraitPojo(member.getMember_id(), ""));
                }
            }
            return RestResult.ok(mids);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("getGroupMembersForPortrait exception", e);
            return RestResult.error(ERROR_SERVER_ERROR);
        }
    }

    @Override
    @Async
    public void saveLog(InputLog log) {
        try {
            LogAdmin.saveLog(log);
        } catch (Exception e) {
            System.out.println("?????????????????????"+e.getMessage());
        }
    }

    @Override
    public RestResult saveMessageLog(SaveLogPojo pojo) {
        try {
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = requestAttributes.getRequest();
            InputLog inputLog = new InputLog();
            inputLog.setMessageId(pojo.getMessageId());
            inputLog.setRemark(pojo.getUserId() + "->" +pojo.getTargetId());
            inputLog.setFlag(true);
            inputLog.setType(2);
            inputLog.setIp(getIp());
            inputLog.setMac(pojo.getMac());
            inputLog.setModel(pojo.getModel());
            inputLog.setPhone(pojo.getPhone());
            inputLog.setPort(request.getRemotePort());
            inputLog.setServerIp(request.getRemoteUser());
            LogAdmin.saveLog(inputLog);
        } catch (Exception e) {
            System.out.println("?????????????????????"+e.getMessage());
            return RestResult.error(SAVE_LOG_FAIL);
        }
        return RestResult.ok("????????????");
    }
}
