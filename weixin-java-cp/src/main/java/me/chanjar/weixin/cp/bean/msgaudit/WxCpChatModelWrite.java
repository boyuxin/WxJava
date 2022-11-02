package me.chanjar.weixin.cp.bean.msgaudit;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 聊天记录数据内容.
 *
 * @author Wang_Wong
 */
@Data
public class WxCpChatModelWrite implements Serializable {
  private static final long serialVersionUID = -5028321625140879571L;

  @ExcelProperty("消息ID")
  private String msgId;

  @ExcelProperty("聊天ID")
  private String myRoomid;

  @ExcelProperty("消息类型")
  private String action;


  @ExcelProperty("发送账号")
  private String from;


  @ExcelProperty("接收账号")
  private String toId;

  @ExcelProperty("聊天时间")
  private String msgTime;

  @ExcelProperty("聊天类型")
  private String msgType;

  @ExcelProperty("聊天内容")
  private String msgData;

  @ExcelProperty("文件地址")
  private String targetPath;





}
