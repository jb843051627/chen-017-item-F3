package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 核签单过口签迹对象 t_item_change_sign
 * 一笔一名：一道口一处落点，并排落名各记各的；掐同一分钟也按两笔算。
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_item_change_sign")
@ApiModel(value = "TItemChangeSign", description = "核签单过口签迹（一笔一名）")
public class TItemChangeSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 核签单主键 */
    @TableField("bill_id")
    @ApiModelProperty(value = "核签单主键")
    private Long billId;

    /** 所属轮次（打回重走另起一轮，沉底批不与新批并排） */
    @TableField("round_no")
    @ApiModelProperty(value = "所属轮次（打回重走另起一轮，沉底批不与新批并排）")
    private Integer roundNo;

    /** 落名道次 0..2（县核/市核/省定） */
    @TableField("node_no")
    @ApiModelProperty(value = "落名道次 0..2（县核/市核/省定）")
    private Integer nodeNo;

    /** 落名人本人（代笔不作数：同人当轮当口只记一笔） */
    @TableField("signer")
    @ApiModelProperty(value = "落名人本人（代笔不作数：同人当轮当口只记一笔）")
    private String signer;

    /** 省定并排分工 0不分工 1盯要素齐不齐 2盯与上位名录合不合 */
    @TableField("sign_role")
    @ApiModelProperty(value = "省定并排分工 0不分工 1盯要素齐不齐 2盯与上位名录合不合")
    private Integer signRole;

    /** 落笔时刻（掐同一分钟也按两笔算） */
    @TableField("sign_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落笔时刻（掐同一分钟也按两笔算）")
    private Date signTime;

    /** 删除标记 0正常 1删除（打回时旧轮整批沉底置1，不物理删） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除（打回时旧轮整批沉底置1，不物理删）")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public Integer getSignRole() {
        return signRole;
    }

    public void setSignRole(Integer signRole) {
        this.signRole = signRole;
    }

    public Date getSignTime() {
        return signTime;
    }

    public void setSignTime(Date signTime) {
        this.signTime = signTime;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
