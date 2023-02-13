package cn.wildfirechat.app.pojo;

import cn.wildfirechat.pojos.MessagePayload;

import java.util.List;

public class UserGroupDel {

    String operator;
    String groupId;
    List<Integer> toLines;
    MessagePayload notifyMessage;

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public List<Integer> getToLines() {
        return toLines;
    }

    public void setToLines(List<Integer> toLines) {
        this.toLines = toLines;
    }

    public MessagePayload getNotifyMessage() {
        return notifyMessage;
    }

    public void setNotifyMessage(MessagePayload notifyMessage) {
        this.notifyMessage = notifyMessage;
    }
}
