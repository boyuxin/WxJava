package me.chanjar.weixin.cp;

import com.alibaba.excel.EasyExcel;
import com.google.common.collect.Lists;
import com.tencent.wework.Finance;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.util.XmlUtils;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.bean.message.WxCpXmlMessage;
import me.chanjar.weixin.cp.bean.msgaudit.WxCpChatDatas;
import me.chanjar.weixin.cp.bean.msgaudit.WxCpChatModel;
import me.chanjar.weixin.cp.bean.msgaudit.WxCpChatModelWrite;
import me.chanjar.weixin.cp.bean.msgaudit.WxCpFileItem;
import me.chanjar.weixin.cp.config.WxCpConfigStorage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author boyuxin
 * @description
 * @date 2022/10/25 21:17
 */
@Slf4j
public class Application {
  private static WxCpConfigStorage wxCpConfigStorage;
  private static WxCpService cpService;

  public static void main(String[] args) throws Exception {
    InputStream inputStream = ClassLoader.getSystemResourceAsStream("test-config.xml");
    WxCpDemoInMemoryConfigStorage config = WxCpDemoInMemoryConfigStorage.fromXml(inputStream);

    wxCpConfigStorage = config;
    cpService = new WxCpServiceImpl();
    cpService.setWxCpConfigStorage(config);


    /**
     * 仔细配置：
     * <xml>
     * <corpId>ww45xxx88865xxx</corpId>
     * <corpSecret>xIpum7Yt4NMXcyxdzcQ2l_46BG4QIQDR57MhA45ebIw</corpSecret> // secret
     * <agentId>200000</agentId> // 会话存档的应用id
     * <token></token> // 回调配置的token
     * <aesKey></aesKey> // 回调配置的EncodingAESKey
     *
     * // 企业微信会话存档
     * // 1、会话存档私钥，最好去除前缀和换行，如下所示！
     * // 2、仔细配置windows以及linux环境sdk路径
     * <msgAuditPriKey>MIxxx893B2pggd1r95T8k2QxxxxbD6xxxxmXsskn+5XunyR1WJlJGqgi0OMVGYvSfkNb9kD50fM21CGLcN1y4miL9fVNBIsvJmIUeJCNS8TioAVGFvh2EgzjqTR1gH</msgAuditPriKey>
     * <msgAuditLibPath>/www/osfile/libcrypto-1_1-x64.dll,libssl-1_1-x64.dll,libcurl-x64.dll,WeWorkFinanceSdk.dll,libWeWorkFinanceSdk_Java.so</msgAuditLibPath>
     * </xml>
     *
     * 注意：最好先配置lib开头的系统库，再配置sdk类库，配置绝对路径，最好配置为linux路径
     * Windows:
     * <msgAuditLibPath>D:/WorkSpace/libcrypto-1_1-x64.dll,libssl-1_1-x64.dll,libcurl-x64.dll,WeWorkFinanceSdk.dll,libWeWorkFinanceSdk_Java.so</msgAuditLibPath>
     * Linux:
     * <msgAuditLibPath>/www/osfile/work_msg_storage/libcrypto-1_1-x64.dll,libssl-1_1-x64.dll,libcurl-x64.dll,WeWorkFinanceSdk.dll,libWeWorkFinanceSdk_Java.so</msgAuditLibPath>
     *
     *
     * yml配置（支持多个corpId）：
     * wx:
     *   cp:
     *     appConfigs:
     *     - agentId: 10001 #客户联系
     *       corpId: xxxxxxxxxxx
     *       secret: T5fTj1n-sBAT4rKNW5c9IYNfPdXZxxxxxxxxxxx
     *       token: 2bSNqTcLtxxxxxxxxxxx
     *       aesKey: AXazu2Xyw44SNY1x8go2phn9p9B2xxxxxxxxxxx
     *     - agentId: 10002 #会话内容存档
     *       corpId: xxxxxxxxxxx
     *       secret: xIpum7Yt4NMXcyxdzcQ2l_46BG4Qxxxxxxxxxxx
     *       token:
     *       aesKey:
     *       msgAuditPriKey: MIxxx893B2pggd1r95T8k2QxxxxbD6xxxxmXsskn+5XunyR1WJlJGqgi0OMVGYvSfkNb9kD50fM21CGLcN1y4miL9fVNBIsvJmIUeJCNS8TioAVGFvh2EgzjqTR1gHxxx
     *       msgAuditLibPath: /www/osfile/libcrypto-1_1-x64.dll,libssl-1_1-x64.dll,libcurl-x64.dll,WeWorkFinanceSdk.dll,libWeWorkFinanceSdk_Java.so
     *
     *
     * 在线生成非对称加密公钥私钥对：
     * http://web.chacuo.net/netrsakeypair
     *
     *
     * 或者可以在linux上使用如下命令生成公钥私钥对：
     * openssl genrsa -out private_key.pem 2048
     * openssl rsa -in private_key.pem -pubout -out public_key.pem
     * /

     /**
     * 建议放到redis，本次请求获取消息记录开始的seq值。首次访问填写0，非首次使用上次企业微信返回的最大seq。允许从任意seq重入拉取。
     */
    long seq = 0L;

    /**
     * 图片，语音，视频，表情，文件，音频存档消息,音频共享文档消息调用  获取媒体消息
     */
    List<String> mediaType = Arrays.asList(new String[]{"image", "voice", "video", "emotion", "file", "meeting_voice_call", "voip_doc_share"});


    // 本次请求获取消息记录开始的seq值。首次访问填写0，非首次使用上次企业微信返回的最大seq。允许从任意seq重入拉取。
    ArrayList<WxCpChatModel> allMessage = new ArrayList<>();
    int bujin = 500;
    int length = bujin;
    while (length > 0) {
      WxCpChatDatas chatDatas = cpService.getMsgAuditService().getChatDatas(seq, bujin, null, null, 1000L);
      length = chatDatas.getChatData().size();
      if (chatDatas != null && chatDatas.getChatData().size() > 0) {
        seq = seq + bujin;
        List<WxCpChatDatas.WxCpChatData> chatdata = chatDatas.getChatData();
        Iterator<WxCpChatDatas.WxCpChatData> iterator = chatdata.iterator();
        while (iterator.hasNext()) {

          WxCpChatDatas.WxCpChatData chatData = iterator.next();
          seq = chatData.getSeq();

          // 数据
//          String msgId = chatData.getMsgId();
//          String encryptChatMsg = chatData.getEncryptChatMsg();
//          String encryptRandomKey = chatData.getEncryptRandomKey();
//          Integer publickeyVer = chatData.getPublickeyVer();

          // 获取明文数据
          final String chatPlainText = cpService.getMsgAuditService().getChatPlainText(chatDatas.getSdk(), chatData, 2);
          final WxCpChatModel wxCpChatModel = WxCpChatModel.fromJson(chatPlainText);

          // 获取消息数据
          // https://developer.work.weixin.qq.com/document/path/91774
          final WxCpChatModel decryptData = cpService.getMsgAuditService().getDecryptData(chatDatas.getSdk(), chatData, 2);
          System.out.println("获取消息数据为 : "+ decryptData.toJson());
          String toId = decryptData.getTolist()[0];
          decryptData.setToId(toId);
          decryptData.getMyRoomId();
          /**
           * 注意：
           * 根据上面返回的文件类型来获取媒体文件，
           * 不同的文件类型，拼接好存放文件的绝对路径，写入文件流，获取媒体文件。（拼接绝对文件路径的原因，以便上传到腾讯云或阿里云对象存储）
           *
           * 目标文件绝对路径+实际文件名，比如：/usr/local/file/20220114/474f866b39d10718810d55262af82662.gif
           */
          String path = "C:\\WorkSpace\\dowm\\";
          String msgType = decryptData.getMsgType();
          if (mediaType.contains(decryptData.getMsgType())) {
            // 文件后缀
            String suffix = "";
            // 文件名md5
            String md5Sum = "";
            // sdkFileId
            String sdkFileId = "";
            switch (msgType) {
              case "image":
                suffix = ".jpg";
                md5Sum = decryptData.getImage().getMd5Sum();
                sdkFileId = decryptData.getImage().getSdkFileId();
                break;
              case "voice":
                suffix = ".amr";
                md5Sum = decryptData.getVoice().getMd5Sum();
                sdkFileId = decryptData.getVoice().getSdkFileId();
                break;
              case "video":
                suffix = ".mp4";
                md5Sum = decryptData.getVideo().getMd5Sum();
                sdkFileId = decryptData.getVideo().getSdkFileId();
                break;
              case "emotion":
                md5Sum = decryptData.getEmotion().getMd5Sum();
                sdkFileId = decryptData.getEmotion().getSdkFileId();
                int type = decryptData.getEmotion().getType();
                switch (type) {
                  case 1:
                    suffix = ".gif";
                    break;
                  case 2:
                    suffix = ".png";
                    break;
                  default:
                    return;
                }
                break;
              case "file":
                md5Sum = decryptData.getFile().getMd5Sum();
                suffix = "." + decryptData.getFile().getFileExt();
                sdkFileId = decryptData.getFile().getSdkFileId();
                break;
              // 音频存档消息
              case "meeting_voice_call":

                md5Sum = decryptData.getVoiceId();
                sdkFileId = decryptData.getMeetingVoiceCall().getSdkFileId();
                for (WxCpChatModel.MeetingVoiceCall.DemoFileData demofiledata : decryptData.getMeetingVoiceCall().getDemoFileData()) {
                  String demoFileDataFileName = demofiledata.getFileName();
                  suffix = demoFileDataFileName.substring(demoFileDataFileName.lastIndexOf(".") + 1);
                }

                break;
              // 音频共享文档消息
              case "voip_doc_share":

                md5Sum = decryptData.getVoipId();
                WxCpFileItem docShare = decryptData.getVoipDocShare();
                String fileName = docShare.getFileName();
                suffix = fileName.substring(fileName.lastIndexOf(".") + 1);

                break;
              default:
                return;
            }

            /**
             * 拉取媒体文件
             *
             * 注意：
             * 1、根据上面返回的文件类型，拼接好存放文件的绝对路径即可。此时绝对路径写入文件流，来达到获取媒体文件的目的。
             * 2、拉取完媒体文件之后，此时文件已经存在绝对路径，可以通过mq异步上传到对象存储
             * 3、比如可以上传到阿里云oss或者腾讯云cos
             */
            String targetPath = path + md5Sum + suffix;
            cpService.getMsgAuditService().getMediaFile(chatDatas.getSdk(), sdkFileId, null, null, 1000L, targetPath);

            /*
             * 拉取媒体文件
             *
             * 传入一个each函数，用于遍历每个分片的数据
             */
//            File file = new File(targetPath);
//            if (!file.getParentFile().exists()) {
//              file.getParentFile().mkdirs();
//            } else {
//              file.delete();
//            }
//
//            cpService.getMsgAuditService().getMediaFile(chatDatas.getSdk(), sdkFileId, null, null, 1000L, data -> {
//              try {
//                // 大于512k的文件会分片拉取，此处需要使用追加写，避免后面的分片覆盖之前的数据。
//                FileOutputStream outputStream = new FileOutputStream(targetPath, true);
//                outputStream.write(data);
//                outputStream.close();
//              } catch (Exception e) {
//                e.printStackTrace();
//              }
//            });


            decryptData.setTargetPath(targetPath);
          }
          allMessage.add(decryptData);

        }
      }
      // 注意：
      // 当此批次数据拉取完毕后，应释放此次sdk
      System.out.println("释放sdk :" + chatDatas.getSdk());
      Finance.DestroySdk(chatDatas.getSdk());
    }



    //多个字段排序
    Comparator<WxCpChatModel> comparator = Comparator.comparing(WxCpChatModel::getMyRoomId)
        .thenComparing(Comparator.comparing(WxCpChatModel::getMsgTime));

    List<WxCpChatModel> collect = allMessage.parallelStream()
      .filter(Objects::nonNull)
      .sorted(comparator)
      .collect(Collectors.toList());
//    WxCpExternalContact externalContact = cpService.getSchoolUserService().getExternalContact(exUserId);


    List<WxCpChatModelWrite> writeList = writeToWxCpChatModelWrite(collect);
    // 写法1
    String fileName = "C:\\WorkSpace\\dowm\\variableTitleWrite\\" + System.currentTimeMillis() + ".xlsx";
    // 这里 需要指定写用哪个class去写，然后写到第一个sheet，名字为模板 然后文件流会自动关闭
    EasyExcel.write(fileName, WxCpChatModelWrite.class).sheet("模板").doWrite(writeList);


    Thread.sleep(2000);
  }

  private static List<WxCpChatModelWrite> writeToWxCpChatModelWrite(List<WxCpChatModel> collect) {
    List<WxCpChatModelWrite> wxCpChatModelWritelist= Lists.newArrayList();
    for (WxCpChatModel wxCpChatModel :collect) {
      wxCpChatModelWritelist.add(convertFromWxCpChatModel(wxCpChatModel));
    }
    return wxCpChatModelWritelist;
  }

  private static WxCpChatModelWrite convertFromWxCpChatModel(WxCpChatModel wxCpChatModel) {
    WxCpChatModelWrite wxCpChatModelWrite = new WxCpChatModelWrite();
    wxCpChatModelWrite.setMsgId(wxCpChatModel.getMsgId());
    wxCpChatModelWrite.setAction(wxCpChatModel.getAction());
    wxCpChatModelWrite.setFrom(wxCpChatModel.getFrom());
    wxCpChatModelWrite.setMyRoomid(wxCpChatModel.getMyRoomId());
//      wxCpChatModelWrite.setTolist(wxCpChatModel.getTolist());
    wxCpChatModelWrite.setToId(wxCpChatModel.getToId());
    wxCpChatModelWrite.setTargetPath(wxCpChatModel.getTargetPath());
    Date date = new Date(wxCpChatModel.getMsgTime());
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String time  =  df.format(date);

    wxCpChatModelWrite.setMsgTime(time);
    wxCpChatModelWrite.setMsgType(wxCpChatModel.getMsgType());

    wxCpChatModelWrite.setMsgData(wxCpChatModel.getMsgData());

    return wxCpChatModelWrite;
  }

}
