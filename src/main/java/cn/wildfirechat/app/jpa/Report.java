package cn.wildfirechat.app.jpa;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "report")
public class Report {

    private String tid;

    private String tname;

    private String tDisplayName;

    private String tPhone;

    private String uid;

    private String uname;

    private String uDisplayName;

    private String uPhone;

    private String content;

    private Integer status;

    @Id
    private Date createTime;

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getTname() {
        return tname;
    }

    public void setTname(String tname) {
        this.tname = tname;
    }

    public String getUid() {
        return uid;
    }

    public String getUname() {
        return uname;
    }

    public void setUname(String uname) {
        this.uname = uname;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String gettDisplayName() {
        return tDisplayName;
    }

    public void settDisplayName(String tDisplayName) {
        this.tDisplayName = tDisplayName;
    }

    public String gettPhone() {
        return tPhone;
    }

    public void settPhone(String tPhone) {
        this.tPhone = tPhone;
    }

    public String getuDisplayName() {
        return uDisplayName;
    }

    public void setuDisplayName(String uDisplayName) {
        this.uDisplayName = uDisplayName;
    }

    public String getuPhone() {
        return uPhone;
    }

    public void setuPhone(String uPhone) {
        this.uPhone = uPhone;
    }
}
